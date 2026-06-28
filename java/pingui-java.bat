@echo off
rem PINGUI Java — launcher для Windows (Linux/macOS: pingui-java.sh).
setlocal EnableExtensions
cd /d "%~dp0"

where java >nul 2>&1
if errorlevel 1 (
  echo [pingui-java] ПОМИЛКА: Java не знайдено. Потрібен JDK 21.
  exit /b 1
)

rem Require JDK 21 (Gradle 8.10 fails under Java 25 launcher).
java -version 2>&1 | findstr /R /C:"version \"21\." /C:"version \"21\"" >nul
if errorlevel 1 (
  echo [pingui-java] ПОМИЛКА: для збірки потрібен JDK 21.
  echo [pingui-java] Gradle 8.10 не запускається під Java 25 ^(типова помилка: «What went wrong: 25.0.3»^).
  java -version 2>&1
  echo [pingui-java] Встановіть Eclipse Temurin 21 і встановіть JAVA_HOME.
  exit /b 1
)

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
