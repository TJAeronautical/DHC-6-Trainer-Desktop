@echo off
setlocal
cd /d "%~dp0"

rem Direct flight: starts the simulator WITHOUT the Qt launcher. Any arguments are
rem forwarded (e.g. --aircraft=dhc6jsb). Used by the trainer's "Fly now" button.

set "FGFS_EXE="
if exist "%~dp0install\bin\DHC-6 Trainer.exe" set "FGFS_EXE=%~dp0install\bin\DHC-6 Trainer.exe"
if not defined FGFS_EXE if exist "%~dp0install\bin\DHC-6-Traine-Desktop.exe" set "FGFS_EXE=%~dp0install\bin\DHC-6-Traine-Desktop.exe"
if not defined FGFS_EXE if exist "%~dp0install\bin\fgfs.exe" set "FGFS_EXE=%~dp0install\bin\fgfs.exe"

if not defined FGFS_EXE (
    echo FlightGear has not been built yet. Run BUILD_DESKTOP_WINDOWS.bat first.
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
rem Default to the DHC-6 unless a variant was passed explicitly.
set "DEFAULT_AIRCRAFT=--aircraft=dhc6"
echo %* | findstr /C:"--aircraft" >nul
if not errorlevel 1 set "DEFAULT_AIRCRAFT="

rem Default to a runway start. Without downloaded scenery the terrain is open
rem ocean and only the startup runway platform is generated - spawning
rem anywhere else puts the aircraft in the water (black underwater view).
set "DEFAULT_POSITION=--airport=BIKF --runway=29"
echo %* | findstr /C:"--airport" /C:"--runway" /C:"--lat" /C:"--lon" /C:"--carrier" /C:"--parking-id" >nul
if not errorlevel 1 set "DEFAULT_POSITION="

rem Offline-first trainer launches are more reliable. Pass --enable-terrasync
rem explicitly to download real scenery into app-data\downloads.
set "TERRASYNC_ARG=--disable-terrasync"
echo %* | findstr /C:"--enable-terrasync" /C:"--disable-terrasync" >nul
if not errorlevel 1 set "TERRASYNC_ARG="

rem FG_HOME is honoured from the environment only; fgfs has no --fg-home option.
rem TerraSync, when enabled explicitly, writes under app-data\downloads.
"%FGFS_EXE%" --fg-root="%FG_ROOT%" --fg-aircraft="%FG_AIRCRAFT%" --download-dir="%FG_DOWNLOAD_DIR%" %TERRASYNC_ARG% %DEFAULT_AIRCRAFT% %DEFAULT_POSITION% %*
set "EXIT_CODE=%ERRORLEVEL%"
endlocal & exit /b %EXIT_CODE%
