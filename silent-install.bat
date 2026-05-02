@echo off
REM ============================================================
REM Altarix AI Terminal - Silent Installer
REM Usage: silent-install.bat [InstallPath] [ALLUSERS]
REM ============================================================

setlocal enabledelayedexpansion

echo Installing Altarix AI Terminal...

REM Default installation path
set INSTALL_PATH=C:\Program Files\Altarix
if not "%~1"=="" set INSTALL_PATH=%~1

REM Check if this is a per-machine installation
set ALLUSERS=0
if not "%~2"=="" set ALLUSERS=%~2

REM Find the installer files
set EXE_INSTALLER=
set MSI_INSTALLER=

for %%F in (*.exe) do (
    if "%%F" neq "silent-install.bat" (
        set EXE_INSTALLER=%%F
        goto :found_exe
    )
)

:found_exe
for %%F in (*.msi) do (
    set MSI_INSTALLER=%%F
    goto :found_msi
)

:found_msi
REM Use MSI if available (better for silent installation), otherwise EXE
if defined MSI_INSTALLER (
    echo Using MSI installer: !MSI_INSTALLER!
    if %ALLUSERS% equ 1 (
        msiexec /i "!MSI_INSTALLER!" ALLUSERS=1 /quiet /norestart /l*v altarix-install.log
    ) else (
        msiexec /i "!MSI_INSTALLER!" /quiet /norestart /l*v altarix-install.log
    )
) else if defined EXE_INSTALLER (
    echo Using EXE installer: !EXE_INSTALLER!
    "!EXE_INSTALLER!" --verbose --install-dir "!INSTALL_PATH!" --start-menu --desktop-shortcut
) else (
    echo ERROR: No installer found!
    exit /b 1
)

if %ERRORLEVEL% equ 0 (
    echo Installation completed successfully
    exit /b 0
) else (
    echo Installation failed with error code %ERRORLEVEL%
    exit /b 1
)
