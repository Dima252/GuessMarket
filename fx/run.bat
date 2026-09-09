@echo off
rem Starts Guess Market. The JavaFX runtime travels beside this file, so the
rem folder can be unzipped anywhere.

setlocal
cd /d "%~dp0"

rem --enable-native-access keeps Java 25 from warning about the native half of JavaFX.
java --module-path "%~dp0javafx\lib" --add-modules javafx.controls,javafx.fxml ^
     --enable-native-access=javafx.graphics ^
     -jar "%~dp0guess-market-fx.jar"

if errorlevel 1 (
    echo.
    echo Guess Market did not start. Java 25 has to be installed and on the PATH.
    pause
)
