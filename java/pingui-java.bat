@echo off
rem PINGUI Java — Windows launcher (Linux/macOS: pingui-java.sh).
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "JAVA_EXE="
set "JAVA_HOME_RESOLVED="

call :find_jdk21
if errorlevel 1 (
  echo.
  echo [pingui-java] ERROR: JDK 21 not found.
  echo [pingui-java] Install: https://adoptium.net/temurin/releases/?version=21
  echo [pingui-java] Enable "Add to PATH" + "Set JAVA_HOME" in the installer.
  echo [pingui-java] Or: set "PINGUI_JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.x-hotspot"
  exit /b 1
)

set "JAVA_HOME=%JAVA_HOME_RESOLVED%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo [pingui-java] JAVA_HOME=%JAVA_HOME%

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
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" echo [pingui-java] BUILD FAILED (exit %RC%)
exit /b %RC%

:do_package
echo [pingui-java] Packaging...
call gradlew.bat jpackage %*
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" echo [pingui-java] PACKAGE FAILED (exit %RC%)
exit /b %RC%

:do_run
echo [pingui-java] Starting GUI...
if "%~1"=="" (
  call gradlew.bat run
) else (
  call gradlew.bat run --args="%*"
)
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" echo [pingui-java] RUN FAILED (exit %RC%)
exit /b %RC%

:do_run_cli
call gradlew.bat run --args="%CMD% %*"
exit /b %ERRORLEVEL%

rem --- JDK 21 discovery ---

:find_jdk21
if defined PINGUI_JAVA_HOME call :probe_home "%PINGUI_JAVA_HOME%"
if defined JAVA_EXE goto :jdk_found

if defined JAVA_HOME call :probe_home "%JAVA_HOME%"
if defined JAVA_EXE goto :jdk_found

for /f "delims=" %%J in ('where java 2^>nul') do (
  call :probe_exe "%%J"
  if defined JAVA_EXE goto :jdk_found
)

call :scan_dir "%ProgramFiles%\Eclipse Adoptium" "jdk-21*"
if defined JAVA_EXE goto :jdk_found

call :scan_dir "%ProgramFiles%\Java" "jdk-21*"
if defined JAVA_EXE goto :jdk_found

call :scan_dir "%ProgramFiles%\Microsoft" "jdk-21*"
if defined JAVA_EXE goto :jdk_found

call :scan_dir "%ProgramFiles%\Amazon Corretto" "jdk21*"
if defined JAVA_EXE goto :jdk_found

call :scan_dir "%LocalAppData%\Programs\Eclipse Adoptium" "jdk-21*"
if defined JAVA_EXE goto :jdk_found

exit /b 1

:jdk_found
call :check_jdk21_major
exit /b %ERRORLEVEL%

:check_jdk21_major
set "JDK_OK=0"
for /f "tokens=3 delims= \"" %%V in ('"%JAVA_EXE%" -version 2^>^&1 ^| findstr /i version') do (
  set "VER=%%V"
)
if not defined VER exit /b 1
echo !VER! | findstr /B /R "21\." >nul 2>&1
if not errorlevel 1 set "JDK_OK=1"
if "!JDK_OK!"=="0" (
  echo !VER! | findstr /B "21" >nul 2>&1
  if not errorlevel 1 set "JDK_OK=1"
)
if "!JDK_OK!"=="0" (
  echo [pingui-java] ERROR: JDK 21 required, found version !VER!
  "%JAVA_EXE%" -version 2>&1
  echo [pingui-java] Path: %JAVA_HOME_RESOLVED%
  set "JAVA_EXE="
  set "JAVA_HOME_RESOLVED="
  exit /b 1
)
exit /b 0

:probe_home
set "CAND=%~1"
if exist "!CAND!\bin\java.exe" call :set_java_home "!CAND!"
exit /b 0

:probe_exe
set "CAND=%~1"
if not exist "!CAND!" exit /b 0
"!CAND!" -version >nul 2>&1
if errorlevel 1 exit /b 0
for %%H in ("!CAND!") do set "CAND=%%~dpH"
if /I "!CAND:~-4!"=="\bin\" set "CAND=!CAND:~0,-4!"
call :set_java_home "!CAND!"
exit /b 0

:set_java_home
set "JAVA_HOME_RESOLVED=%~1"
set "JAVA_EXE=%JAVA_HOME_RESOLVED%\bin\java.exe"
exit /b 0

:scan_dir
set "BASE=%~1"
set "PATTERN=%~2"
if not exist "%BASE%\" exit /b 0
for /d %%D in ("%BASE%\%PATTERN%") do (
  if exist "%%D\bin\java.exe" (
    call :set_java_home "%%D"
    exit /b 0
  )
)
exit /b 0
