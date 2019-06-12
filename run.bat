@echo off
set PATH=%PATH%;%cd%\libs\lib\win64
set /p RIOT_API_KEY="Riot API key: "
java -jar build\libs\overlay-1.0-SNAPSHOT.jar
