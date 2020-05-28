@echo off
set PATH=%PATH%;%CD%\libs\lib\win32
set PREV_DIR=%CD%
cd /d "%~dp0"
cd ..
call gradlew run
cd %PREV_DIR%