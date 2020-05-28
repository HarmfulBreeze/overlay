@echo off
set PREV_DIR=%CD%
cd /d "%~dp0"
cd ..
call gradlew.bat shadowJarW32
cd %PREV_DIR%