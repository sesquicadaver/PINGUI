@echo off
rem P26-004 — Windows launcher smoke (quoting + javaw + detach + fail→log).
rem Isolated temp tree (does not overwrite repo installDist). No JavaFX.
setlocal EnableExtensions EnableDelayedExpansion

set "REPO_JAVA=%~dp0.."
pushd "%REPO_JAVA%" >nul
set "REPO_JAVA=%CD%"
popd >nul

set "TMP=%TEMP%\pingui-launcher-smoke-%RANDOM%"
set "ROOT=%TMP%\java"
mkdir "%ROOT%\build\install\pingui-java\bin" >nul 2>&1
mkdir "%ROOT%\build\install\pingui-java\lib" >nul 2>&1
copy /Y "%REPO_JAVA%\pingui-java.bat" "%ROOT%\pingui-java.bat" >nul
if errorlevel 1 (
  echo FAIL: copy pingui-java.bat
  exit /b 1
)

set "PINGUI_SMOKE_ARGS=%TMP%\args.txt"
set "PINGUI_SMOKE_LOG=%TMP%\stub.log"
set "PINGUI_GUI_LOG=%TMP%\gui.log"
set "PINGUI_SKIP_INSTALL_DIST=1"

rem Attached stub: one argv per line (token boundaries, not substring).
(
  echo @echo off
  echo setlocal EnableExtensions
  echo if not defined PINGUI_SMOKE_ARGS exit /b 1
  echo type nul ^> "%%PINGUI_SMOKE_ARGS%%"
  echo :write_args
  echo if "%%~1"=="" goto args_done
  echo ^>^>"%%PINGUI_SMOKE_ARGS%%" echo %%~1
  echo shift
  echo goto write_args
  echo :args_done
  echo echo stub-ok^>^>"%%PINGUI_SMOKE_LOG%%"
  echo exit /b 0
) > "%ROOT%\build\install\pingui-java\bin\pingui-java.bat"

rem Detached stub: fail → stderr so launcher `>>gui.log 2>&1` must capture it.
set "PINGUI_JAVAW=%TMP%\javaw.cmd"
(
  echo @echo off
  echo setlocal EnableExtensions
  echo if not defined PINGUI_SMOKE_ARGS exit /b 1
  echo type nul ^> "%%PINGUI_SMOKE_ARGS%%"
  echo :write_args
  echo if "%%~1"=="" goto args_done
  echo ^>^>"%%PINGUI_SMOKE_ARGS%%" echo %%~1
  echo shift
  echo goto write_args
  echo :args_done
  echo echo stub-javaw-ok^>^>"%%PINGUI_SMOKE_LOG%%"
  echo if /I "%%PINGUI_SMOKE_FAIL%%"=="1" ^(
  echo   echo stub-fail 1^>^&2
  echo   exit /b 1
  echo ^)
  echo exit /b 0
) > "%PINGUI_JAVAW%"

set "CFG=%TMP%\path with spaces\hosts.yaml"
mkdir "%TMP%\path with spaces" >nul 2>&1
echo hosts:> "%CFG%"

cd /d "%ROOT%"

echo [smoke_launcher.cmd] 1/5 --help attached
del "%PINGUI_SMOKE_ARGS%" >nul 2>&1
del "%PINGUI_SMOKE_LOG%" >nul 2>&1
call pingui-java.bat --help
if errorlevel 1 (
  echo FAIL: --help
  exit /b 1
)
findstr /X /C:"--help" "%PINGUI_SMOKE_ARGS%" >nul
if errorlevel 1 (
  echo FAIL: --help args
  type "%PINGUI_SMOKE_ARGS%"
  exit /b 1
)

echo [smoke_launcher.cmd] 2/5 quoting spaces via --foreground
del "%PINGUI_SMOKE_ARGS%" >nul 2>&1
call pingui-java.bat --foreground -- --config "%CFG%"
if errorlevel 1 (
  echo FAIL: foreground
  exit /b 1
)
findstr /X /C:"--config" "%PINGUI_SMOKE_ARGS%" >nul
if errorlevel 1 (
  echo FAIL: --config token missing
  type "%PINGUI_SMOKE_ARGS%"
  exit /b 1
)
findstr /X /C:"%CFG%" "%PINGUI_SMOKE_ARGS%" >nul
if errorlevel 1 (
  echo FAIL: spaced path not preserved as single argv
  type "%PINGUI_SMOKE_ARGS%"
  exit /b 1
)

echo [smoke_launcher.cmd] 3/5 --daemon forces attached console
del "%PINGUI_SMOKE_ARGS%" >nul 2>&1
del "%PINGUI_SMOKE_LOG%" >nul 2>&1
call pingui-java.bat -- --daemon --config "%CFG%"
if errorlevel 1 (
  echo FAIL: daemon
  exit /b 1
)
findstr /X /C:"--daemon" "%PINGUI_SMOKE_ARGS%" >nul
if errorlevel 1 (
  echo FAIL: daemon args
  type "%PINGUI_SMOKE_ARGS%"
  exit /b 1
)
findstr /X /C:"%CFG%" "%PINGUI_SMOKE_ARGS%" >nul
if errorlevel 1 (
  echo FAIL: daemon spaced path
  type "%PINGUI_SMOKE_ARGS%"
  exit /b 1
)
findstr /C:"stub-ok" "%PINGUI_SMOKE_LOG%" >nul
if errorlevel 1 (
  echo FAIL: daemon did not use attached stub
  type "%PINGUI_SMOKE_LOG%"
  exit /b 1
)

echo [smoke_launcher.cmd] 4/5 detached uses PINGUI_JAVAW + spaced args
del "%PINGUI_SMOKE_ARGS%" >nul 2>&1
del "%PINGUI_SMOKE_LOG%" >nul 2>&1
call pingui-java.bat -- --config "%CFG%"
if errorlevel 1 (
  echo FAIL: detached launch
  exit /b 1
)
call :await_file "%PINGUI_SMOKE_ARGS%"
if errorlevel 1 (
  echo FAIL: timeout waiting for detached args
  exit /b 1
)
findstr /X /C:"--config" "%PINGUI_SMOKE_ARGS%" >nul
if errorlevel 1 (
  echo FAIL: detached --config token
  type "%PINGUI_SMOKE_ARGS%"
  exit /b 1
)
findstr /X /C:"%CFG%" "%PINGUI_SMOKE_ARGS%" >nul
if errorlevel 1 (
  echo FAIL: detached spaced path as single argv
  type "%PINGUI_SMOKE_ARGS%"
  exit /b 1
)
call :await_file "%PINGUI_SMOKE_LOG%"
if errorlevel 1 (
  echo FAIL: timeout waiting for javaw stub log
  exit /b 1
)
findstr /C:"stub-javaw-ok" "%PINGUI_SMOKE_LOG%" >nul
if errorlevel 1 (
  echo FAIL: javaw stub not invoked
  type "%PINGUI_SMOKE_LOG%"
  exit /b 1
)

echo [smoke_launcher.cmd] 5/5 detached failure lands in GUI log via redirect
del "%PINGUI_SMOKE_ARGS%" >nul 2>&1
del "%PINGUI_GUI_LOG%" >nul 2>&1
set "PINGUI_SMOKE_FAIL=1"
call pingui-java.bat -- --config "%CFG%"
set "PINGUI_SMOKE_FAIL="
call :await_file "%PINGUI_GUI_LOG%"
if errorlevel 1 (
  echo FAIL: timeout waiting for gui.log
  exit /b 1
)
findstr /C:"stub-fail" "%PINGUI_GUI_LOG%" >nul
if errorlevel 1 (
  echo FAIL: fail marker missing from gui.log ^(redirect broken?^)
  type "%PINGUI_GUI_LOG%"
  exit /b 1
)

echo [smoke_launcher.cmd] OK
exit /b 0

:await_file
set "_af=%~1"
set "_n=0"
:await_file_loop
if exist "%_af%" (
  for %%A in ("%_af%") do if not %%~zA==0 exit /b 0
)
set /a _n+=1
if !_n! GEQ 50 exit /b 1
ping -n 1 127.0.0.1 >nul
goto await_file_loop
