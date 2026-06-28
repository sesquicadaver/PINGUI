@echo off
rem PINGUI Java — launcher для Windows (Linux/macOS: pingui-java.sh).
setlocal EnableExtensions
cd /d "%~dp0"

where java >nul 2>&1
if errorlevel 1 (
  echo [pingui-java] ПОМИЛКА: Java 21+ не знайдено. Встановіть JDK 21.
  exit /b 1
)

set "CMD=%~1"
if "%CMD%"=="" set "CMD=run"
if not "%CMD%"=="" shift

if /I "%CMD%"=="--help" goto help
if /I "%CMD%"=="-h" goto help
if /I "%CMD%"=="help" goto help
if /I "%CMD%"=="--test" goto test
if /I "%CMD%"=="test" goto test
if /I "%CMD%"=="--build" goto build
if /I "%CMD%"=="build" goto build
if /I "%CMD%"=="--package" goto package
if /I "%CMD%"=="package" goto package
if /I "%CMD%"=="--run" goto run
if /I "%CMD%"=="run" goto run
goto run_cli

:help
call gradlew.bat run --args="--help" -q
exit /b %ERRORLEVEL%

:test
call gradlew.bat test %*
exit /b %ERRORLEVEL%

:build
call gradlew.bat build %*
exit /b %ERRORLEVEL%

:package
call gradlew.bat jpackage %*
exit /b %ERRORLEVEL%

:run
if "%~1"=="" (
  call gradlew.bat run -q
) else (
  call gradlew.bat run --args="%*" -q
)
exit /b %ERRORLEVEL%

:run_cli
call gradlew.bat run --args="%CMD% %*" -q
exit /b %ERRORLEVEL%
