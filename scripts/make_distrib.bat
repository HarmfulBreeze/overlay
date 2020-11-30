@echo off
pushd "%~dp0.." & call gradlew overlayDistZip & popd
