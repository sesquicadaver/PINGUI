@echo off
rem PINGUI Java — Windows launcher v6 (thin wrapper over gradlew.bat).
rem JDK 21: set JAVA_HOME or PINGUI_JAVA_HOME before run (Gradle toolchain).
rem Note: in cmd.exe %* is NEVER updated by shift — collect extras with a loop.
setlocal EnableExtensions
cd /d "%~dp0"

if defined PINGUI_JAVA_HOME set "JAVA_HOME=%PINGUI_JAVA_HOME%"

set "CMD=%~1"
if "%CMD%"=="" set "CMD=run"
set "PINGUI_EXTRA="
if not "%~1"=="" shift /1
:collect_extra
if "%~1"=="" goto dispatch
if defined PINGUI_EXTRA (
  set "PINGUI_EXTRA=%PINGUI_EXTRA% %~1"
) else (
  set "PINGUI_EXTRA=%~1"
)
shift /1
goto collect_extra

:dispatch
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
if defined PINGUI_EXTRA (
  call gradlew.bat build %PINGUI_EXTRA%
) else (
  call gradlew.bat build
)
exit /b %ERRORLEVEL%

:do_package
echo [pingui-java] Packaging...
if defined PINGUI_EXTRA (
  call gradlew.bat jpackage %PINGUI_EXTRA%
) else (
  call gradlew.bat jpackage
)
exit /b %ERRORLEVEL%

:do_run
echo [pingui-java] Starting GUI...
if defined PINGUI_EXTRA (
  call gradlew.bat run --args="%PINGUI_EXTRA%"
) else (
  call gradlew.bat run
)
exit /b %ERRORLEVEL%

:do_run_cli
call gradlew.bat run --args="%CMD% %PINGUI_EXTRA%"
exit /b %ERRORLEVEL%
