@echo off
rem Starts the ChatApp client. Needs Java 11 or newer (https://adoptium.net).
java -jar "%~dp0ChatClient.jar"
if errorlevel 1 pause
