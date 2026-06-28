@echo off
rem PINGUI Java — Windows launcher (Linux/macOS: pingui-java.sh).
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "JAVA_EXE="
set "JAVA_HOME_RESOLVED="

call :resolve_java_home
if errorlevel 1 exit /b 1

set "JAVA_HOME=%JAVA_HOME_RESOLVED%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

set "CMD=%~1"
if "%CMD%"=="" set "CMD=run"
if not "%CMD%"=="" shift

if /I "%CMD%"=="--help" goto help
if /I "%CMD%"=="-h" goto help
if /I "%CMD%"=="help" goto help
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

rem ---------------------------------------------------------------------------
rem Locate JDK 21: PINGUI_JAVA_HOME, JAVA_HOME, PATH, then common install dirs.
rem ---------------------------------------------------------------------------
:resolve_java_home
if defined PINGUI_JAVA_HOME call :try_home "%PINGUI_JAVA_HOME%"
if defined JAVA_EXE goto :verify_major

if defined JAVA_HOME call :try_home "%JAVA_HOME%"
if defined JAVA_EXE goto :verify_major

for /f "delims=" %%J in ('where java 2^>nul') do (
  call :try_exe "%%J"
  if defined JAVA_EXE goto :verify_major
)

call :scan_dir "%ProgramFiles%\Eclipse Adoptium" "jdk-21*"
if defined JAVA_EXE goto :verify_major
call :scan_dir "%ProgramFiles%\Eclipse Adoptium" "jdk-21.*"
if defined JAVA_EXE goto :verify_major
call :scan_dir "%ProgramFiles%\Java" "jdk-21*"
if defined JAVA_EXE goto :verify_major
call :scan_dir "%ProgramFiles%\Microsoft" "jdk-21*"
if defined JAVA_EXE goto :verify_major
call :scan_dir "%ProgramFiles%\Amazon Corretto" "jdk21*"
if defined JAVA_EXE goto :verify_major
call :scan_dir "%LocalAppData%\Programs\Eclipse Adoptium" "jdk-21*"
if defined JAVA_EXE goto :verify_major
call :scan_dir "%ProgramFiles%\Temurin" "jdk-21*"
if defined JAVA_EXE goto :verify_major

echo [pingui-java] ERROR: JDK 21 not found.
echo [pingui-java] Install Eclipse Temurin 21: https://adoptium.net/temurin/releases/?version=21
echo [pingui-java] Enable "Add to PATH" and "Set JAVA_HOME" during setup.
echo [pingui-java] Or set: set PINGUI_JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.x-hotspot
exit /b 1

:verify_major
call :try_exe "%JAVA_EXE%"
"%JAVA_EXE%" -version 2>&1 | findstr /R /C:"version \"21\." /C:"version \"21\"" >nul
if errorlevel 1 (
  echo [pingui-java] ERROR: JDK 21 required for Gradle 8.10 ^(Java 25 breaks the build^).
  "%JAVA_EXE%" -version 2>&1
  echo [pingui-java] Found: %JAVA_HOME_RESOLVED%
  echo [pingui-java] Set PINGUI_JAVA_HOME to a JDK 21 install directory.
  set "JAVA_EXE="
  exit /b 1
)
exit /b 0

:try_home
set "CAND=%~1"
if not exist "!CAND!\bin\java.exe" exit /b 0
call :accept_home "!CAND!"
exit /b 0

:try_exe
set "CAND=%~1"
if not exist "!CAND!" exit /b 0
"!CAND!" -version >nul 2>&1
if errorlevel 1 exit /b 0
for %%H in ("!CAND!") do set "CAND=%%~dpH"
if /I "!CAND:~-4!"=="\bin\" set "CAND=!CAND:~0,-4!"
call :accept_home "!CAND!"
exit /b 0

:accept_home
set "JAVA_HOME_RESOLVED=%~1"
set "JAVA_EXE=%JAVA_HOME_RESOLVED%\bin\java.exe"
exit /b 0

:scan_dir
set "BASE=%~1"
set "PATTERN=%~2"
if not exist "%BASE%\" exit /b 0
for /d %%D in ("%BASE%\%PATTERN%") do (
  if exist "%%D\bin\java.exe" (
    call :accept_home "%%D"
    exit /b 0
  )
)
exit /b 0
