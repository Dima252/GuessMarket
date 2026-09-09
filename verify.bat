@echo off
rem Compiles the engine and runs the checks in verification\Verify.java against it.
rem Requires JDK 25 on the PATH. Run it from the root of the repository.

setlocal
cd /d "%~dp0"

if exist build\verify rmdir /s /q build\verify
mkdir build\verify

dir /s /b engine\src\*.java > build\verify-sources.txt
javac -encoding UTF-8 -d build\verify @build\verify-sources.txt
if errorlevel 1 goto failed

javac -encoding UTF-8 -cp build\verify -d build\verify verification\Verify.java
if errorlevel 1 goto failed

java -cp build\verify Verify
exit /b %errorlevel%

:failed
echo.
echo COMPILATION FAILED.
exit /b 1
