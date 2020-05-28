@echo off
set PATH=%PATH%;%cd%\libs\lib\win64
set PREV_DIR=%CD%
cd /d "%~dp0"
cd ..
call gradlew run
cd %PREV_DIR%