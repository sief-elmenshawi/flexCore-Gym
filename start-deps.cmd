@echo off
REM Start Flexcore dependencies (Postgres, Redis, RabbitMQ, Jaeger) from any directory.
REM Usage: start-deps.cmd [clean]
REM   clean  -> docker compose down -v then up (wipes DB data)

for %%I in ("%~dp0.") do set "PROJ=%%~fI"

if /i "%~1"=="clean" (
    echo [flexcore] Removing volumes and containers...
    call :compose down -v || goto :fail
)

echo [flexcore] Starting dependencies in %PROJ% ...
call :compose up -d postgres redis rabbitmq jaeger || goto :fail

echo [flexcore] Dependencies started. Ports: postgres 5434, redis 6379, rabbitmq 5672, jaeger 16686.
echo [flexcore] Now start the app in IntelliJ (Run FlexcoreApplication) - port 8080 is free.
exit /b 0

:compose
docker compose --project-directory "%PROJ%" -f "%PROJ%\docker-compose.yml" %*
exit /b %errorlevel%

:fail
echo [flexcore] FAILED - see errors above.
exit /b 1