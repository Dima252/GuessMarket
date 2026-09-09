@echo off
rem Runs the checks that need a JavaFX runtime: the loading task, and the
rem screens driven without a mouse. Run build.bat first - these check the jars
rem that were built, and the JavaFX runtime that was packed beside them.
rem
rem The engine checks are in verify.bat and need no JavaFX at all.

setlocal
cd /d "%~dp0"

if "%JAVAFX_HOME%"=="" set JAVAFX_HOME=C:\Users\dimat\javafx-sdk-25.0.4

if not exist dist\guess-market-fx.jar (
    echo Run build.bat first.
    exit /b 1
)

if exist build\verify-ui rmdir /s /q build\verify-ui
mkdir build\verify-ui

set CP=dist\guess-market-engine.jar;dist\guess-market-fx.jar;build\verify-ui

javac -encoding UTF-8 --module-path "%JAVAFX_HOME%\lib" --add-modules javafx.controls,javafx.fxml ^
      -cp "%CP%" -d build\verify-ui verification\LoadTaskCheck.java verification\EventsScreenCheck.java
if errorlevel 1 goto failed

echo.
echo === The loading task ===
java --module-path "%JAVAFX_HOME%\lib" --add-modules javafx.controls,javafx.fxml ^
     --enable-native-access=javafx.graphics -cp "%CP%" LoadTaskCheck
if errorlevel 1 goto broken

echo.
echo === The events screen ===
java --module-path "%JAVAFX_HOME%\lib" --add-modules javafx.controls,javafx.fxml ^
     --enable-native-access=javafx.graphics -cp "%CP%" EventsScreenCheck
if errorlevel 1 goto broken

echo.
echo Every screen check passed.
exit /b 0

:broken
echo.
echo A SCREEN CHECK FAILED.
exit /b 1

:failed
echo.
echo COMPILATION FAILED.
exit /b 1
