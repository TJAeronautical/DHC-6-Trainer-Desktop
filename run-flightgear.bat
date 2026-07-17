@echo off
setlocal
cd /d "%~dp0"

set "FGFS_EXE="
if exist "%~dp0install\bin\DHC-6 Trainer.exe" set "FGFS_EXE=%~dp0install\bin\DHC-6 Trainer.exe"
if not defined FGFS_EXE if exist "%~dp0install\bin\DHC-6-Traine-Desktop.exe" set "FGFS_EXE=%~dp0install\bin\DHC-6-Traine-Desktop.exe"
if not defined FGFS_EXE if exist "%~dp0install\bin\fgfs.exe" set "FGFS_EXE=%~dp0install\bin\fgfs.exe"

if not defined FGFS_EXE (
    echo FlightGear has not been built yet.
    echo.
    echo 1. Move this project to a path without spaces, for example C:\FGDesktop
    echo 2. Run BUILD_DESKTOP_WINDOWS.bat
    echo 3. Run this launcher again after the build completes
    echo.
    exit /b 1
)

if not exist "%~dp0deps\fgdata\defaults.xml" (
    echo FGData is missing or incomplete.
    echo Run scripts\bootstrap-windows.ps1 and then rebuild.
    exit /b 1
)

if not exist "%~dp0app-data\profile" mkdir "%~dp0app-data\profile"
if not exist "%~dp0app-data\aircraft" mkdir "%~dp0app-data\aircraft"
if not exist "%~dp0app-data\downloads" mkdir "%~dp0app-data\downloads"
if not exist "%~dp0deps\fgdata\Scenery" mkdir "%~dp0deps\fgdata\Scenery"

set "FG_ROOT=%~dp0deps\fgdata"
set "FG_HOME=%~dp0app-data\profile"
set "FG_AIRCRAFT=%~dp0app-data\aircraft"
set "FG_DOWNLOAD_DIR=%~dp0app-data\downloads"
set "PATH=%~dp0install\bin;%~dp0deps\windows-3rd-party\msvc140\3rdParty.x64\bin;%PATH%"
rem FG_HOME is honoured from the environment only; fgfs has no --fg-home option.
"%FGFS_EXE%" --launcher --fg-root="%FG_ROOT%" --fg-aircraft="%FG_AIRCRAFT%" --download-dir="%FG_DOWNLOAD_DIR%" %*
set "EXIT_CODE=%ERRORLEVEL%"
endlocal & exit /b %EXIT_CODE%
