@echo off
cd /d "%~dp0"
where mvn >nul 2>nul
if errorlevel 1 (
  echo Maven was not found. Install Maven 3.8+ and ensure 'mvn' is on PATH.
  echo Then run this file again, or run: mvn spring-boot:run
  pause
  exit /b 1
)
echo Starting Seatly at http://localhost:8080 ...
mvn spring-boot:run
pause
