@echo off
rem PINGUI Java — Windows launcher v7.
rem GUI defaults to detached javaw (no console). CLI modes stay attached.
rem JDK 21: set JAVA_HOME or PINGUI_JAVA_HOME before run.
rem Note: in cmd.exe %* is NEVER updated by shift — collect extras with a loop.
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

if defined PINGUI_JAVA_HOME set "JAVA_HOME=%PINGUI_JAVA_HOME%"

set "CMD=%~1"
if "%CMD%"=="" set "CMD=run"
set "PINGUI_EXTRA="
set "PINGUI_FOREGROUND=0"
if not "%~1"=="" shift /1

:collect_extra
if "%~1"=="" goto after_collect
if /I "%~1"=="--foreground" (
  set "PINGUI_FOREGROUND=1"
  shift /1
  goto collect_extra
)
if /I "%~1"=="--fg" (
  set "PINGUI_FOREGROUND=1"
  shift /1
  goto collect_extra
)
if defined PINGUI_EXTRA (
  set "PINGUI_EXTRA=!PINGUI_EXTRA! %~1"
) else (
  set "PINGUI_EXTRA=%~1"
)
shift /1
goto collect_extra

:after_collect
if /I "%CMD%"=="--help" goto do_help
if /I "%CMD%"=="-h" goto do_help
if /I "%CMD%"=="help" goto do_help
if /I "%CMD%"=="--build" goto do_build
if /I "%CMD%"=="build" goto do_build
if /I "%CMD%"=="--package" goto do_package
if /I "%CMD%"=="package" goto do_package
if /I "%CMD%"=="--foreground" (
  set "PINGUI_FOREGROUND=1"
  goto do_gui_entry
)
if /I "%CMD%"=="--fg" (
  set "PINGUI_FOREGROUND=1"
  goto do_gui_entry
)
if /I "%CMD%"=="--run" goto do_gui_entry
if /I "%CMD%"=="run" goto do_gui_entry
if /I "%CMD%"=="--" goto do_gui_entry
rem First token is an app flag/arg (e.g. --daemon)
if defined PINGUI_EXTRA (
  set "PINGUI_EXTRA=%CMD% !PINGUI_EXTRA!"
) else (
  set "PINGUI_EXTRA=%CMD%"
)
goto do_gui_entry

:do_help
call gradlew.bat installDist -q
if errorlevel 1 exit /b %ERRORLEVEL%
call "build\install\pingui-java\bin\pingui-java.bat" --help
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

:do_gui_entry
call :detect_console_need
if "!PINGUI_NEED_CONSOLE!"=="1" goto do_run_attached
if "!PINGUI_FOREGROUND!"=="1" goto do_run_attached
goto do_run_detached

:do_run_attached
echo [pingui-java] Starting (attached)...
call gradlew.bat installDist -q
if errorlevel 1 exit /b %ERRORLEVEL%
if defined PINGUI_EXTRA (
  call "build\install\pingui-java\bin\pingui-java.bat" %PINGUI_EXTRA%
) else (
  call "build\install\pingui-java\bin\pingui-java.bat"
)
exit /b %ERRORLEVEL%

:do_run_detached
echo [pingui-java] Starting GUI (detached, javaw)...
call gradlew.bat installDist -q
if errorlevel 1 exit /b %ERRORLEVEL%
set "APP_HOME=%CD%\build\install\pingui-java"
set "JAVAW_EXE=javaw"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" set "JAVAW_EXE=%JAVA_HOME%\bin\javaw.exe"
if not defined PINGUI_GUI_LOG set "PINGUI_GUI_LOG=%LOCALAPPDATA%\pingui\gui.log"
for %%D in ("%PINGUI_GUI_LOG%") do if not exist "%%~dpD" mkdir "%%~dpD"
set "CP=%APP_HOME%\lib\*"
if defined PINGUI_EXTRA (
  start "" /B cmd /c ""%JAVAW_EXE%" -Dfile.encoding=UTF-8 -cp "%CP%" io.pingui.PinguiLauncher %PINGUI_EXTRA% >>"%PINGUI_GUI_LOG%" 2>&1"
) else (
  start "" /B cmd /c ""%JAVAW_EXE%" -Dfile.encoding=UTF-8 -cp "%CP%" io.pingui.PinguiLauncher >>"%PINGUI_GUI_LOG%" 2>&1"
)
echo [pingui-java] GUI launched (no console). Log: %PINGUI_GUI_LOG%
echo [pingui-java] Foreground debug: pingui-java.bat --foreground
exit /b 0

:detect_console_need
set "PINGUI_NEED_CONSOLE=0"
if not defined PINGUI_EXTRA exit /b 0
echo !PINGUI_EXTRA! | findstr /I /C:"--daemon" /C:"--stop" /C:"--status" /C:"--help" /C:"--export-report" /C:"--export-schedule" /C:"--telemetry-dump" /C:"--telemetry-retention" >nul
if not errorlevel 1 set "PINGUI_NEED_CONSOLE=1"
exit /b 0
