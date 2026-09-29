@echo off
rem Builds Guess Market for exercise 3: the server as one WAR, and the JavaFX
rem client as a folder with its jars, the JavaFX runtime, and a run.bat.
rem
rem   build.bat           everything
rem   build.bat server    only the WAR
rem
rem Requires JDK 25 or later on the PATH - everything is compiled for Java 25 -
rem plus Tomcat 10.1 (for servlet-api.jar, which the WAR must not carry) and the
rem JavaFX 25 SDK. Set TOMCAT_HOME and JAVAFX_HOME, or the defaults below are used.
rem The other libraries travel with the repository, in lib\.

setlocal
cd /d "%~dp0"

if "%TOMCAT_HOME%"=="" set TOMCAT_HOME=C:\Users\dimat\apache-tomcat-10.1.60
if "%JAVAFX_HOME%"=="" set JAVAFX_HOME=C:\Users\dimat\javafx-sdk-25.0.4
set JAVAC=javac --release 25 -encoding UTF-8
set GSON=lib\gson-2.13.2.jar
set HTTP_LIBS=lib\okhttp-4.12.0.jar;lib\okio-jvm-3.6.0.jar;lib\kotlin-stdlib-2.3.21.jar

if not exist "%TOMCAT_HOME%\lib\servlet-api.jar" (
    echo.
    echo Tomcat was not found at "%TOMCAT_HOME%".
    echo Install Tomcat 10.1 and set TOMCAT_HOME to its folder.
    exit /b 1
)

if exist build rmdir /s /q build
if exist dist rmdir /s /q dist
mkdir build\shared build\engine build\war\WEB-INF\classes build\war\WEB-INF\lib dist

echo [1/7] Compiling the shared module...
dir /s /b shared\src\*.java > build\shared-sources.txt
%JAVAC% -d build\shared @build\shared-sources.txt
if errorlevel 1 goto failed
jar --create --file build\guess-market-shared.jar -C build\shared .
if errorlevel 1 goto failed

echo [2/7] Compiling the engine module...
dir /s /b engine\src\*.java > build\engine-sources.txt
%JAVAC% -cp build\guess-market-shared.jar -d build\engine @build\engine-sources.txt
if errorlevel 1 goto failed
jar --create --file build\guess-market-engine.jar -C build\engine .
if errorlevel 1 goto failed

echo [3/7] Compiling the server module...
dir /s /b server\src\*.java > build\server-sources.txt
%JAVAC% -cp "build\guess-market-shared.jar;build\guess-market-engine.jar;%GSON%;%TOMCAT_HOME%\lib\servlet-api.jar" ^
      -d build\war\WEB-INF\classes @build\server-sources.txt
if errorlevel 1 goto failed

echo [4/7] Packing guess-market.war...
rem Everything the server needs travels inside the WAR, except the servlet API,
rem which Tomcat itself provides.
copy /y build\guess-market-shared.jar build\war\WEB-INF\lib\ > nul
copy /y build\guess-market-engine.jar build\war\WEB-INF\lib\ > nul
copy /y %GSON% build\war\WEB-INF\lib\ > nul
jar --create --file dist\guess-market.war -C build\war .
if errorlevel 1 goto failed

if /i "%~1"=="server" goto done

if not exist "%JAVAFX_HOME%\lib\javafx.controls.jar" (
    echo.
    echo The JavaFX SDK was not found at "%JAVAFX_HOME%".
    echo Download the JavaFX 25 SDK for Windows and set JAVAFX_HOME to its folder.
    exit /b 1
)
mkdir build\client dist\client\lib

echo [5/7] Compiling the client module...
dir /s /b client\src\*.java > build\client-sources.txt
%JAVAC% --module-path "%JAVAFX_HOME%\lib" --add-modules javafx.controls,javafx.fxml ^
      -cp "build\guess-market-shared.jar;%GSON%;%HTTP_LIBS%" -d build\client @build\client-sources.txt
if errorlevel 1 goto failed
rem The screens travel inside the jar, beside the classes that load them.
xcopy /s /y /q client\src\*.fxml build\client\ > nul
if errorlevel 1 goto failed

echo [6/7] Packing guess-market-client.jar...
jar --create --file dist\client\guess-market-client.jar --manifest client\manifest.txt -C build\client .
if errorlevel 1 goto failed

echo [7/7] Assembling the client folder...
copy /y build\guess-market-shared.jar dist\client\lib\ > nul
copy /y %GSON% dist\client\lib\ > nul
for %%j in (%HTTP_LIBS%) do copy /y %%j dist\client\lib\ > nul

rem Only the four modules the client asks for travel with it. The whole SDK is
rem 107 MB, nearly all of it jfxwebkit.dll, and this program has no WebView and
rem plays no media - shipping those would multiply the size of the zip for nothing.
mkdir dist\client\javafx\lib dist\client\javafx\bin
for %%m in (base graphics controls fxml) do copy /y "%JAVAFX_HOME%\lib\javafx.%%m.jar" dist\client\javafx\lib\ > nul
copy /y "%JAVAFX_HOME%\lib\javafx.properties" dist\client\javafx\lib\ > nul
rem The natives are copied whole and the web and media ones are then dropped, so
rem that anything unforeseen is still there.
xcopy /s /y /q "%JAVAFX_HOME%\bin\*" dist\client\javafx\bin\ > nul
for %%d in (jfxwebkit jfxmedia jfxmedia_qtkit gstreamer-lite glib-lite fxplugins) do (
    if exist dist\client\javafx\bin\%%d.dll del /q dist\client\javafx\bin\%%d.dll
)
copy /y client\run.bat dist\client\run.bat > nul

rem Exercise 4, the web client, for its own submission box: only what runs - its
rem little server, the page, run.bat. Its self-check stays in the repository, and
rem its readme is the exported docs\EX4_README_SUBMISSION.md.
mkdir dist\web-client\public
for %%f in (server.js package.json run.bat) do copy /y web-client\%%f dist\web-client\ > nul
xcopy /s /y /q web-client\public\* dist\web-client\public\ > nul

:done
echo.
echo Build finished. The folder "dist" holds exactly what should be zipped:
dir /b dist
if exist dist\client dir /b dist\client
if exist dist\web-client dir /b dist\web-client
exit /b 0

:failed
echo.
echo BUILD FAILED.
exit /b 1
