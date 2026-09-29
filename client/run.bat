@echo off
rem Starts the Guess Market client. The JavaFX runtime and every library travel
rem beside this file, so the folder can be unzipped anywhere. The server is
rem expected at http://localhost:8080/guess-market - deploy guess-market.war into
rem Tomcat's webapps folder and start Tomcat first.

setlocal
cd /d "%~dp0"

rem --enable-native-access keeps Java 25 from warning about the native half of JavaFX.
java --module-path "%~dp0javafx\lib" --add-modules javafx.controls,javafx.fxml ^
     --enable-native-access=javafx.graphics ^
     -jar "%~dp0guess-market-client.jar"

if errorlevel 1 (
    echo.
    echo The Guess Market client did not start. Java 25 has to be installed and on the PATH.
    pause
)
