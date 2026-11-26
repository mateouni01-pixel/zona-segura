@echo off
setlocal
set "SERVER_ADDRESS=0.0.0.0"
set "SERVER_PORT=8080"
set "SERVER_SSL_ENABLED=false"
set "SPRING_PROFILES_ACTIVE="
echo API por Wi-Fi: http://192.168.0.5:8080
call "%~dp0iniciar-backend.cmd" -SinAdb %*
endlocal
