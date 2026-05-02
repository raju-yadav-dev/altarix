# Altarix Installer Builder Script
# PowerShell version with advanced features and validation
# Usage: .\build-installer.ps1 [Options]

param(
    [switch]$ExeOnly,
    [switch]$MsiOnly,
    [switch]$SkipBuild,
    [switch]$SkipTest,
    [switch]$OpenOutput,
    [switch]$Help
)

$ProgressPreference = 'Continue'
$ErrorActionPreference = 'Stop'

# Colors for output
$Colors = @{
    Success = 'Green'
    Error = 'Red'
    Warning = 'Yellow'
    Info = 'Cyan'
    Header = 'Magenta'
}

function Write-Header {
    param([string]$Message)
    Write-Host ""
    Write-Host "╔$('═' * 58)╗" -ForegroundColor $Colors.Header
    Write-Host "║ $($Message.PadRight(56)) ║" -ForegroundColor $Colors.Header
    Write-Host "╚$('═' * 58)╝" -ForegroundColor $Colors.Header
    Write-Host ""
}

function Write-Status {
    param(
        [string]$Message,
        [ValidateSet('Success', 'Error', 'Warning', 'Info')]
        [string]$Type = 'Info'
    )
    $Symbol = switch($Type) {
        'Success' { '✓' }
        'Error' { '✗' }
        'Warning' { '!' }
        'Info' { '•' }
    }
    Write-Host "[$Symbol] $Message" -ForegroundColor $Colors[$Type]
}

function Show-Help {
    Write-Host @"
Altarix Installer Builder - PowerShell Edition

USAGE:
    .\build-installer.ps1 [Options]

OPTIONS:
    -ExeOnly        Build only EXE installer
    -MsiOnly        Build only MSI installer
    -SkipBuild      Skip gradle build (use existing build)
    -SkipTest       Skip running tests
    -OpenOutput     Automatically open output directory
    -Help           Show this help message

EXAMPLES:
    # Build all installers
    .\build-installer.ps1

    # Build only EXE with existing build
    .\build-installer.ps1 -ExeOnly -SkipBuild -OpenOutput

    # Build MSI for deployment
    .\build-installer.ps1 -MsiOnly

REQUIREMENTS:
    - Gradle installed and in PATH
    - Java 25+
    - Windows 7 or later
    - PowerShell 5.0+

OUTPUT:
    Installers are created in: build\installers\
"@
}

function Test-Prerequisites {
    Write-Status "Checking prerequisites..." Info
    
    # Check Gradle
    $gradle = Get-Command gradlew -ErrorAction SilentlyContinue
    if (-not $gradle) {
        Write-Status "Gradle not found!" Error
        exit 1
    }
    Write-Status "Gradle found" Success
    
    # Check Java
    $java = Get-Command java -ErrorAction SilentlyContinue
    if (-not $java) {
        Write-Status "Java not found!" Error
        exit 1
    }
    Write-Status "Java found" Success
    
    # Check version.properties
    if (-not (Test-Path "version.properties")) {
        Write-Status "version.properties not found!" Error
        exit 1
    }
    Write-Status "version.properties found" Success
    
    # Check LICENSE.txt
    if (-not (Test-Path "LICENSE.txt")) {
        Write-Status "LICENSE.txt not found!" Warning
    } else {
        Write-Status "LICENSE.txt found" Success
    }
}

function Build-Project {
    Write-Status "Building project..." Info
    
    try {
        if ($SkipTest) {
            & ./gradlew clean build -x test
        } else {
            & ./gradlew clean build
        }
        
        if ($LASTEXITCODE -ne 0) {
            Write-Status "Build failed!" Error
            exit 1
        }
        Write-Status "Build completed successfully" Success
    }
    catch {
        Write-Status "Build error: $_" Error
        exit 1
    }
}

function Build-Installers {
    param(
        [ValidateSet('Exe', 'Msi', 'Both')]
        [string]$Type = 'Both'
    )
    
    try {
        if ($Type -in 'Exe', 'Both') {
            Write-Status "Building EXE installer..." Info
            & ./gradlew packageExe
            if ($LASTEXITCODE -eq 0) {
                Write-Status "EXE installer created successfully" Success
            }
        }
        
        if ($Type -in 'Msi', 'Both') {
            Write-Status "Building MSI installer..." Info
            & ./gradlew packageMsi
            if ($LASTEXITCODE -eq 0) {
                Write-Status "MSI installer created successfully" Success
            }
        }
    }
    catch {
        Write-Status "Installer build error: $_" Error
        exit 1
    }
}

function Verify-Installers {
    Write-Status "Verifying installers..." Info
    
    $exeFile = Get-ChildItem "build\installers\*.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
    $msiFile = Get-ChildItem "build\installers\*.msi" -ErrorAction SilentlyContinue | Select-Object -First 1
    
    if ($exeFile) {
        $exeSize = [math]::Round($exeFile.Length / 1MB, 2)
        Write-Status "EXE: $($exeFile.Name) ($exeSize MB)" Success
    } else {
        Write-Status "EXE installer not found" Warning
    }
    
    if ($msiFile) {
        $msiSize = [math]::Round($msiFile.Length / 1MB, 2)
        Write-Status "MSI: $($msiFile.Name) ($msiSize MB)" Success
    } else {
        Write-Status "MSI installer not found" Warning
    }
}

# Main execution
if ($Help) {
    Show-Help
    exit 0
}

Write-Header "ALTARIX AI TERMINAL - INSTALLER BUILDER"

# Change to script directory
Push-Location $PSScriptRoot

try {
    # Step 1: Prerequisites
    Write-Header "Step 1: Checking Prerequisites"
    Test-Prerequisites
    
    # Step 2: Build Project
    if (-not $SkipBuild) {
        Write-Header "Step 2: Building Project"
        Build-Project
    } else {
        Write-Status "Skipping project build (using existing build)" Warning
    }
    
    # Step 3: Build Installers
    Write-Header "Step 3: Building Installers"
    if ($ExeOnly) {
        Build-Installers -Type Exe
    } elseif ($MsiOnly) {
        Build-Installers -Type Msi
    } else {
        Build-Installers -Type Both
    }
    
    # Step 4: Verify
    Write-Header "Step 4: Verifying Installers"
    Verify-Installers
    
    # Final summary
    Write-Header "Build Complete!"
    Write-Status "Installers created in: build\installers\" Success
    
    if ($OpenOutput) {
        Write-Status "Opening installer directory..." Info
        Start-Process "build\installers"
    }
    
    Write-Host ""
    Write-Host "Features Included:" -ForegroundColor $Colors.Info
    Write-Host "  ✓ Desktop shortcut option" -ForegroundColor $Colors.Success
    Write-Host "  ✓ Custom installation path selector" -ForegroundColor $Colors.Success
    Write-Host "  ✓ Start Menu integration" -ForegroundColor $Colors.Success
    Write-Host "  ✓ Per-user installation option" -ForegroundColor $Colors.Success
    Write-Host "  ✓ Professional UI with application icon" -ForegroundColor $Colors.Success
    Write-Host "  ✓ License agreement display" -ForegroundColor $Colors.Success
    Write-Host "  ✓ Automatic uninstaller registration" -ForegroundColor $Colors.Success
    Write-Host ""
}
finally {
    Pop-Location
}
