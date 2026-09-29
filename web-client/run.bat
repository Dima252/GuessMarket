@echo off
rem Starts the Guess Market web client and opens it in the browser at
rem http://localhost:3000/
rem
rem The Guess Market server must already be running: Tomcat at localhost:8080
rem with guess-market.war deployed. Node.js must be installed. There is nothing
rem to install - the client uses no packages - so no npm install is needed.

setlocal
cd /d "%~dp0"

where node > nul 2> nul
if errorlevel 1 (
    echo Node.js was not found. Install it from https://nodejs.org and run this again.
    pause
    exit /b 1
)

node "%~dp0server.js"
if errorlevel 1 pause
