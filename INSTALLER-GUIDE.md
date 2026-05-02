# Altarix Installer Configuration Guide

This guide explains how to build professional EXE and MSI installers for Altarix AI Terminal.

## Quick Start

Run the installer builder script:
```bash
.\build-installer.bat
```

This will automatically:
1. Clean and build the project
2. Create professional Windows installers
3. Output files to `build/installers/`

## What You Get

### EXE Installer Features
- ✓ Silent/interactive installation modes
- ✓ Desktop shortcut option (user prompted)
- ✓ Start Menu integration
- ✓ Custom installation path selection
- ✓ Per-user installation option
- ✓ Automatic uninstaller registration
- ✓ Professional UI with Altarix branding
- ✓ License agreement display

### MSI Installer Features
All EXE features plus:
- ✓ Windows Installer standards compliance
- ✓ System-wide installation option
- ✓ ALLUSERS support for IT deployment
- ✓ Rollback/repair capabilities
- ✓ Better IT management tool support
- ✓ Remote desktop compatibility

## Installation Options

### End Users
1. Download `Altarix-1.5.5.exe` (recommended for most users)
2. Run the installer
3. Choose installation path (default: `C:\Program Files\Altarix`)
4. Optionally create desktop shortcut
5. Complete installation

### System Administrators / Corporate Deployment
1. Download `Altarix-1.5.5.msi`
2. Use with Group Policy, SCCM, or other deployment tools
3. Command line example:
   ```batch
   msiexec /i Altarix-1.5.5.msi ALLUSERS=1 /quiet
   ```

## Build Configuration Details

### Gradle Tasks
- `gradlew packageExe` - Build EXE installer only
- `gradlew packageMsi` - Build MSI installer only
- `gradlew packageInstallers` - Build all installers

### Configuration Files
- `LICENSE.txt` - License displayed during installation
- `src/main/resources/icon/Altarix-1024.ico` - Application icon
- `version.properties` - Application version (auto-detected)

## Customization

### Change Application Icon
Replace `src/main/resources/icon/Altarix-1024.ico` with your icon.

### Change Application Name
Edit `build.gradle`:
```gradle
def appName = 'Altarix'  // Change this
def vendorName = 'Altarix AI'  // And this
```

### Change Version
Edit `altarix/version.properties`:
```properties
version=1.5.5  // Change version here
```

### Change Installation Directory Default
Edit `build.gradle` to add:
```gradle
'--install-dir', 'C:\\Program Files\\YourApp'
```

## System Requirements

- Windows 7 or later (64-bit)
- 4GB RAM (recommended 8GB)
- 500MB free disk space
- Java Runtime included in installer

## Troubleshooting

### Build Fails
- Ensure Gradle is installed: `gradlew --version`
- Check Java 25+ is installed: `java -version`
- Delete `build/` directory and try again

### Installer Won't Run
- Disable antivirus temporarily (some engines flag jpackage installers)
- Run as Administrator
- Check Windows Event Viewer for error details

### Installation Paths
- **EXE**: Defaults to `C:\Program Files\Altarix` or user-selected path
- **MSI**: Same paths available, configurable via msiexec properties

## Distribution

### For End Users
- Use the EXE installer: `Altarix-1.5.5.exe`
- Host on your website
- Create download page with system requirements

### For IT Departments
- Use the MSI installer: `Altarix-1.5.5.msi`
- Provide deployment scripts
- Document silent installation options

## Advanced MSI Options

Silent installation example:
```batch
msiexec /i Altarix-1.5.5.msi /quiet /norestart
```

With logging:
```batch
msiexec /i Altarix-1.5.5.msi /quiet /l*v install.log
```

Per-machine installation:
```batch
msiexec /i Altarix-1.5.5.msi ALLUSERS=1 /quiet
```

## Support

For issues or feature requests, contact: support@altarix.ai

---
*Last Updated: May 2026*
*Altarix AI Terminal v1.5.5*
