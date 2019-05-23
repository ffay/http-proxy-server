@echo off & setlocal enabledelayedexpansion
title http-proxy-server
cd %~dp0

set LIB_JARS=""
cd ..\lib
for %%i in (*) do set LIB_JARS=!LIB_JARS!;..\lib\%%i
cd ..\bin

java -Dapp.home=../ -Xms64m -Xmx1024m -classpath ..\conf;%LIB_JARS% com.io2c.httpproxyserver.HttpProxyServer
goto end