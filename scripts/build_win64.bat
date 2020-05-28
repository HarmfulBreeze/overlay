@echo off
set PREV_DIR=%CD%
cd /d "%~dp0"
cd ..
call gradlew.bat shadowJarW64
cd %PREV_DIR%