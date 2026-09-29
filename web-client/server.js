'use strict';

/*
 * The Guess Market web client's own little server. It does two things:
 *
 *   1. serves the page itself - the files in ./public;
 *   2. passes every request under /guess-market/ on to the real Guess Market
 *      server in Tomcat, at localhost:8080, and its answer back.
 *
 * The second is what lets the page and the API share one origin in the browser,
 * so the Tomcat server is used exactly as it is - same WAR, same API, no CORS
 * settings, nothing configured differently for this client. The session cookie
 * Tomcat sets for /guess-market passes through untouched.
 *
 * Only Node's own modules are used, so there is nothing to install.
 */

const http = require('http');
const fs = require('fs');
const path = require('path');
const { exec } = require('child_process');

const PORT = Number(process.env.PORT) || 3000;
const TOMCAT_HOST = process.env.TOMCAT_HOST || 'localhost';
const TOMCAT_PORT = Number(process.env.TOMCAT_PORT) || 8080;
const API_PREFIX = '/guess-market/';
const PUBLIC_DIR = path.join(__dirname, 'public');

const CONTENT_TYPES = {
    '.html': 'text/html; charset=utf-8',
    '.css': 'text/css; charset=utf-8',
    '.js': 'text/javascript; charset=utf-8',
    '.svg': 'image/svg+xml',
    '.ico': 'image/x-icon',
    '.png': 'image/png',
};

const server = http.createServer((request, response) => {
    if (request.url.startsWith(API_PREFIX)) {
        forwardToTomcat(request, response);
    } else {
        serveFile(request, response);
    }
});

/** Hands the request to Tomcat as it came, and Tomcat's answer back as it comes. */
function forwardToTomcat(request, response) {
    const upstream = http.request({
        host: TOMCAT_HOST,
        port: TOMCAT_PORT,
        method: request.method,
        path: request.url,
        headers: { ...request.headers, host: `${TOMCAT_HOST}:${TOMCAT_PORT}` },
    }, (answer) => {
        response.writeHead(answer.statusCode, answer.headers);
        answer.pipe(response);
    });
    upstream.on('error', () => {
        if (!response.headersSent) {
            response.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' });
        }
        response.end(JSON.stringify({
            message: `The Guess Market server cannot be reached at ${TOMCAT_HOST}:${TOMCAT_PORT}. `
                + 'Make sure Tomcat is running with guess-market.war deployed.',
        }));
    });
    request.pipe(upstream);
}

/** Serves a file of the page, and never anything outside the public folder. */
function serveFile(request, response) {
    let wanted;
    try {
        wanted = decodeURIComponent(new URL(request.url, 'http://localhost').pathname);
    } catch {
        return refuse(response, 400, 'Bad request');
    }
    if (wanted === '/') {
        wanted = '/index.html';
    }
    const file = path.normalize(path.join(PUBLIC_DIR, wanted));
    if (!file.startsWith(PUBLIC_DIR + path.sep)) {
        return refuse(response, 403, 'Forbidden');
    }
    fs.readFile(file, (problem, contents) => {
        if (problem) {
            return refuse(response, 404, 'Not found');
        }
        response.writeHead(200, {
            'Content-Type': CONTENT_TYPES[path.extname(file).toLowerCase()] || 'application/octet-stream',
            'Cache-Control': 'no-cache',
        });
        response.end(contents);
    });
}

function refuse(response, status, message) {
    response.writeHead(status, { 'Content-Type': 'text/plain; charset=utf-8' });
    response.end(message);
}

server.on('error', (problem) => {
    if (problem.code === 'EADDRINUSE') {
        console.error(`Port ${PORT} is already in use. Close whatever uses it, or start with another port:`);
        console.error('  set PORT=3001 && run.bat');
    } else {
        console.error(problem.message);
    }
    process.exit(1);
});

server.listen(PORT, () => {
    const address = `http://localhost:${PORT}/`;
    console.log('Guess Market web client');
    console.log(`  open            ${address}`);
    console.log(`  talking to      http://${TOMCAT_HOST}:${TOMCAT_PORT}${API_PREFIX}`);
    console.log('  stop it with    Ctrl+C');
    if (process.platform === 'win32' && !process.env.NO_BROWSER) {
        exec(`start "" "${address}"`);
    }
});
