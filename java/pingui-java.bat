@echo off
rem PINGUI Java — Windows launcher (thin wrapper over gradlew.bat).
rem JDK 21: set JAVA_HOME or PINGUI_JAVA_HOME before run (Gradle toolchain).
setlocal EnableExtensions
cd /d "%~dp0"

if defined PINGUI_JAVA_HOME set "JAVA_HOME=%PINGUI_JAVA_HOME%"

set "CMD=%~1"
if "%CMD%"=="" set "CMD=run"
if not "%~1"=="" shift

if /I "%CMD%"=="--help" goto do_help
if /I "%CMD%"=="-h" goto do_help
if /I "%CMD%"=="help" goto do_help
if /I "%CMD%"=="--build" goto do_build
if /I "%CMD%"=="build" goto do_build
if /I "%CMD%"=="--package" goto do_package
if /I "%CMD%"=="package" goto do_package
if /I "%CMD%"=="--run" goto do_run
if /I "%CMD%"=="run" goto do_run
goto do_run_cli

:do_help
call gradlew.bat run --args="--help"
exit /b %ERRORLEVEL%

:do_build
echo [pingui-java] Building...
call gradlew.bat build %*
exit /b %ERRORLEVEL%

:do_package
echo [pingui-java] Packaging...
call gradlew.bat jpackage %*
exit /b %ERRORLEVEL%

:do_run
echo [pingui-java] Starting GUI...
if "%~1"=="" (
  call gradlew.bat run
) else (
  call gradlew.bat run --args="%*"
)
exit /b %ERRORLEVEL%

:do_run_cli
call gradlew.bat run --args="%CMD% %*"
exit /b %ERRORLEVEL%
