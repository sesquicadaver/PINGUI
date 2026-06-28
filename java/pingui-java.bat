@echo off
rem PINGUI Java — Windows launcher v3 (paths with spaces safe).
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "JAVA_EXE="
set "JAVA_HOME_RESOLVED="

call :find_jdk21
if errorlevel 1 goto :no_jdk

set "JAVA_HOME=!JAVA_HOME_RESOLVED!"
set "PATH=!JAVA_HOME!\bin;!PATH!"
echo [pingui-java] JAVA_HOME=!JAVA_HOME!

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

:no_jdk
echo.
echo [pingui-java] ERROR: JDK 21 not found.
echo [pingui-java] Install: https://adoptium.net/temurin/releases/?version=21
echo [pingui-java] Then set PINGUI_JAVA_HOME to the JDK folder, for example:
echo [pingui-java]   set PINGUI_JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot
exit /b 1

:do_help
call gradlew.bat run --args="--help"
exit /b !ERRORLEVEL!

:do_build
echo [pingui-java] Building...
call gradlew.bat build %*
if errorlevel 1 echo [pingui-java] BUILD FAILED
exit /b !ERRORLEVEL!

:do_package
echo [pingui-java] Packaging...
call gradlew.bat jpackage %*
if errorlevel 1 echo [pingui-java] PACKAGE FAILED
exit /b !ERRORLEVEL!

:do_run
echo [pingui-java] Starting GUI...
if "%~1"=="" goto do_run_plain
call gradlew.bat run --args="%*"
goto do_run_done
:do_run_plain
call gradlew.bat run
:do_run_done
if errorlevel 1 echo [pingui-java] RUN FAILED
exit /b !ERRORLEVEL!

:do_run_cli
call gradlew.bat run --args="%CMD% %*"
exit /b !ERRORLEVEL!

rem --- JDK 21 discovery (no IF/FOR blocks with expanded paths) ---

:find_jdk21
if not defined PINGUI_JAVA_HOME goto :find_env_java_home
if not exist "!PINGUI_JAVA_HOME!\bin\java.exe" goto :find_env_java_home
set "JAVA_HOME_RESOLVED=!PINGUI_JAVA_HOME!"
set "JAVA_EXE=!JAVA_HOME_RESOLVED!\bin\java.exe"
goto :verify_jdk21

:find_env_java_home
if not defined JAVA_HOME goto :find_where_java
if not exist "!JAVA_HOME!\bin\java.exe" goto :find_where_java
set "JAVA_HOME_RESOLVED=!JAVA_HOME!"
set "JAVA_EXE=!JAVA_HOME_RESOLVED!\bin\java.exe"
goto :verify_jdk21

:find_where_java
set "JAVA_EXE="
for /f "delims=" %%J in ('where java 2^>nul') do (
  set "JAVA_EXE=%%J"
  goto :find_where_java_done
)
goto :find_adoptium
:find_where_java_done
if not exist "!JAVA_EXE!" goto :find_adoptium
for %%I in ("!JAVA_EXE!") do set "JAVA_HOME_RESOLVED=%%~dpI"
set "JAVA_HOME_RESOLVED=!JAVA_HOME_RESOLVED:~0,-5!"
goto :verify_jdk21

:find_adoptium
call :pick_jdk_from_dir "%ProgramFiles%\Eclipse Adoptium" "jdk-21"
if defined JAVA_EXE goto :verify_jdk21

call :pick_jdk_from_dir "%LocalAppData%\Programs\Eclipse Adoptium" "jdk-21"
if defined JAVA_EXE goto :verify_jdk21

call :pick_jdk_from_dir "%ProgramFiles%\Java" "jdk-21"
if defined JAVA_EXE goto :verify_jdk21

call :pick_jdk_from_dir "%ProgramFiles%\Microsoft" "jdk-21"
if defined JAVA_EXE goto :verify_jdk21

call :pick_jdk_from_dir "%ProgramFiles%\Amazon Corretto" "jdk21"
if defined JAVA_EXE goto :verify_jdk21

exit /b 1

:pick_jdk_from_dir
set "JAVA_EXE="
set "JAVA_HOME_RESOLVED="
set "_DIR=%~1"
set "_PFX=%~2"
for /f "delims=" %%D in ('dir /b /ad "!_DIR!\!_PFX!*" 2^>nul') do (
  set "JAVA_HOME_RESOLVED=!_DIR!\%%D"
  set "JAVA_EXE=!JAVA_HOME_RESOLVED!\bin\java.exe"
  goto :pick_jdk_done
)
:pick_jdk_done
set "_DIR="
set "_PFX="
exit /b 0

:verify_jdk21
set "_VERFILE=%TEMP%\pingui-java-version.txt"
del "!_VERFILE!" 2>nul
"!JAVA_EXE!" -version 2>"!_VERFILE!"
if not exist "!_VERFILE!" exit /b 1
findstr /C:"21.0" "!_VERFILE!" >nul 2>&1
if not errorlevel 1 exit /b 0
findstr /C:"version \"21" "!_VERFILE!" >nul 2>&1
if not errorlevel 1 exit /b 0
echo [pingui-java] ERROR: JDK 21 required. Version output:
type "!_VERFILE!"
echo [pingui-java] JAVA_HOME was: "!JAVA_HOME_RESOLVED!"
set "JAVA_EXE="
set "JAVA_HOME_RESOLVED="
del "!_VERFILE!" 2>nul
exit /b 1
