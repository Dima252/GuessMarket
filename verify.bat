@echo off
rem Compiles the engine and runs the checks in verification\Verify.java against it.
rem Requires JDK 25 or later on the PATH; everything is compiled for Java 25.
rem Run it from the root of the repository.

setlocal
cd /d "%~dp0"

if exist build\verify rmdir /s /q build\verify
mkdir build\verify

dir /s /b shared\src\*.java engine\src\*.java > build\verify-sources.txt
javac --release 25 -encoding UTF-8 -d build\verify @build\verify-sources.txt
if errorlevel 1 goto failed

javac --release 25 -encoding UTF-8 -cp build\verify -d build\verify verification\Verify.java
if errorlevel 1 goto failed

java -cp build\verify Verify
exit /b %errorlevel%

:failed
echo.
echo COMPILATION FAILED.
exit /b 1
