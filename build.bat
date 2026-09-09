@echo off
rem Builds Guess Market: the engine, and the JavaFX application in front of it.
rem
rem Requires JDK 25 on the PATH and the JavaFX 25 SDK. Set JAVAFX_HOME to point
rem at it, or leave it and the default below is used.
rem
rem The console module of exercise 1 is no longer built - see ui\README.md.

setlocal
cd /d "%~dp0"

if "%JAVAFX_HOME%"=="" set JAVAFX_HOME=C:\Users\dimat\javafx-sdk-25.0.4

if not exist "%JAVAFX_HOME%\lib\javafx.controls.jar" (
    echo.
    echo The JavaFX SDK was not found at "%JAVAFX_HOME%".
    echo Download the JavaFX 25 SDK for Windows and set JAVAFX_HOME to its folder.
    exit /b 1
)

if exist build rmdir /s /q build
if exist dist rmdir /s /q dist
mkdir build\engine
mkdir build\fx
mkdir dist

echo [1/5] Compiling the engine module...
dir /s /b engine\src\*.java > build\engine-sources.txt
javac -encoding UTF-8 -d build\engine @build\engine-sources.txt
if errorlevel 1 goto failed

echo [2/5] Packing guess-market-engine.jar...
jar --create --file dist\guess-market-engine.jar -C build\engine .
if errorlevel 1 goto failed

echo [3/5] Compiling the JavaFX module...
dir /s /b fx\src\*.java > build\fx-sources.txt
javac -encoding UTF-8 --module-path "%JAVAFX_HOME%\lib" --add-modules javafx.controls,javafx.fxml ^
      -cp dist\guess-market-engine.jar -d build\fx @build\fx-sources.txt
if errorlevel 1 goto failed

rem The screens travel inside the jar, beside the classes that load them.
xcopy /s /y /q fx\src\market\fx\screens\*.fxml build\fx\market\fx\screens\ > nul
if errorlevel 1 goto failed

echo [4/5] Packing guess-market-fx.jar...
jar --create --file dist\guess-market-fx.jar --manifest fx\manifest.txt -C build\fx .
if errorlevel 1 goto failed

echo [5/5] Assembling dist...
rem Only the four modules the application asks for travel with it. The whole SDK
rem is 107 MB, nearly all of it jfxwebkit.dll, and this program has no WebView
rem and plays no media - shipping those would multiply the size of the zip for
rem nothing.
mkdir dist\javafx\lib
mkdir dist\javafx\bin
for %%m in (base graphics controls fxml) do copy /y "%JAVAFX_HOME%\lib\javafx.%%m.jar" dist\javafx\lib\ > nul
copy /y "%JAVAFX_HOME%\lib\javafx.properties" dist\javafx\lib\ > nul

rem The natives are copied whole and the web and media ones are then dropped, so
rem that anything unforeseen is still there.
xcopy /s /y /q "%JAVAFX_HOME%\bin\*" dist\javafx\bin\ > nul
for %%d in (jfxwebkit jfxmedia jfxmedia_qtkit gstreamer-lite glib-lite fxplugins) do (
    if exist dist\javafx\bin\%%d.dll del /q dist\javafx\bin\%%d.dll
)

copy /y fx\run.bat dist\run.bat > nul

echo.
echo Build finished. The folder "dist" holds exactly what should be zipped:
dir /b dist
exit /b 0

:failed
echo.
echo BUILD FAILED.
exit /b 1
