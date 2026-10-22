@echo off
setlocal
cd /d "%~dp0"
if errorlevel 1 exit /b 1
call ant compile
if errorlevel 1 exit /b 1
java -cp "build\classes;lib\*" battleship.client.ClientMain
endlocal
