@echo off
set PATH=%PATH%;%cd%\libs\lib\win32
if exist apikey.txt goto :1
if not exist apikey.txt goto :2

:1
set /p RIOT_API_KEY=<apikey.txt
goto :start

:2
set /p RIOT_API_KEY="Riot API key: "
echo %RIOT_API_KEY%>apikey.txt

:start
java -jar build\libs\overlay-1.0-SNAPSHOT-win32.jar