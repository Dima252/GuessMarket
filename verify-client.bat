@echo off
rem Drives the real client against the real server: logs in through the login
rem screen, loads funds, trades and chats while a second user trades against it,
rem and saves pictures of every tab into build\screens.
rem
rem Run build.bat first, deploy dist\guess-market.war into Tomcat and start it.
rem Needs the JavaFX SDK for javafx.swing, which the pictures are saved with.

setlocal
cd /d "%~dp0"

if "%JAVAFX_HOME%"=="" set JAVAFX_HOME=C:\Users\dimat\javafx-sdk-25.0.4

if not exist dist\client\guess-market-client.jar (
    echo Run build.bat first.
    exit /b 1
)

if exist build\verify-client rmdir /s /q build\verify-client
mkdir build\verify-client

set CP=dist\client\guess-market-client.jar;dist\client\lib\*;build\verify-client
set MODULES=javafx.controls,javafx.fxml,javafx.swing

javac --release 25 -encoding UTF-8 --module-path "%JAVAFX_HOME%\lib" --add-modules %MODULES% ^
      -cp "%CP%" -d build\verify-client verification\ClientCheck.java
if errorlevel 1 goto failed

java --module-path "%JAVAFX_HOME%\lib" --add-modules %MODULES% --enable-native-access=javafx.graphics ^
     -cp "%CP%" ClientCheck
exit /b %errorlevel%

:failed
echo.
echo COMPILATION FAILED.
exit /b 1
