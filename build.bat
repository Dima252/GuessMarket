@echo off
rem Builds both modules into two jars and assembles the folder that is submitted.
rem Requires JDK 25 on the PATH.

setlocal
cd /d "%~dp0"

if exist build rmdir /s /q build
if exist dist rmdir /s /q dist
mkdir build\engine
mkdir build\ui
mkdir dist

echo [1/4] Compiling the engine module...
dir /s /b engine\src\*.java > build\engine-sources.txt
javac -encoding UTF-8 -d build\engine @build\engine-sources.txt
if errorlevel 1 goto failed

echo [2/4] Packing guess-market-engine.jar...
jar --create --file dist\guess-market-engine.jar -C build\engine .
if errorlevel 1 goto failed

echo [3/4] Compiling the ui module...
dir /s /b ui\src\*.java > build\ui-sources.txt
javac -encoding UTF-8 -cp dist\guess-market-engine.jar -d build\ui @build\ui-sources.txt
if errorlevel 1 goto failed

echo [4/4] Packing guess-market-ui.jar...
jar --create --file dist\guess-market-ui.jar --manifest ui\manifest.txt -C build\ui .
if errorlevel 1 goto failed

copy /y packaging\run.bat dist\run.bat > nul

echo.
echo Build finished. The folder "dist" holds exactly what should be zipped:
dir /b dist
exit /b 0

:failed
echo.
echo BUILD FAILED.
exit /b 1
