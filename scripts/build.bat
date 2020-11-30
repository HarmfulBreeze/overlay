@echo off
pushd "%~dp0.." & call gradlew jar & popd
