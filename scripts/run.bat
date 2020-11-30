@echo off
set PATH=%PATH%;%~dp0..\libs\lib\win64
pushd "%~dp0.." & call gradlew -q run & popd
