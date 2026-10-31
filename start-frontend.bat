@echo off
title CampusFix Frontend (Vite dev server)
cd /d "%~dp0frontend"
echo ================================================
echo  CampusFix frontend starting...
echo  Open http://localhost:5173 after Vite is ready.
echo  Keep this window OPEN while developing.
echo ================================================
call npm run dev
echo.
echo Vite exited. Press any key to close this window.
pause >nul
