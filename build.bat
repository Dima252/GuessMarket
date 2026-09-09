@echo off
rem Builds the engine of Guess Market.
rem
rem Exercise 1 shipped two jars: this engine and the console module in "ui".
rem Exercise 2 replaces that console with a JavaFX application, and the engine
rem API it is built on now names the user who is acting, which the old console
rem does not. The console is therefore no longer built - see ui\README.md.
rem
rem Requires JDK 25 on the PATH.

setlocal
cd /d "%~dp0"

if exist build rmdir /s /q build
if exist dist rmdir /s /q dist
mkdir build\engine
mkdir dist

echo [1/2] Compiling the engine module...
dir /s /b engine\src\*.java > build\engine-sources.txt
javac -encoding UTF-8 -d build\engine @build\engine-sources.txt
if errorlevel 1 goto failed

echo [2/2] Packing guess-market-engine.jar...
jar --create --file dist\guess-market-engine.jar -C build\engine .
if errorlevel 1 goto failed

echo.
echo Build finished:
dir /b dist
echo.
echo Run verify.bat to check the engine against the worked examples of the course.
exit /b 0

:failed
echo.
echo BUILD FAILED.
exit /b 1
