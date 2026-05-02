# Altarix AI Terminal - Professional Installer Setup

## ✅ Complete Setup Summary

A comprehensive professional installer infrastructure has been created for the Altarix AI Terminal project. This includes everything needed to build and deploy enterprise-grade Windows installers.

---

## 📦 What Was Created

### 1. **Enhanced Build Configuration** (`build.gradle`)
- ✓ Added professional installer features
- ✓ Desktop shortcut prompt option
- ✓ Custom installation path selector
- ✓ Per-user installation support
- ✓ License file integration
- ✓ Application description and vendor details

### 2. **License File** (`LICENSE.txt`)
- Professional software license agreement
- Displayed during installation
- Covers usage rights and limitations

### 3. **Build Automation Scripts**

#### **Batch Script** (`build-installer.bat`)
- Simple one-click build for all installers
- Error handling and validation
- Automatic output directory opening
- System checks

**Usage:**
```batch
cd altarix
build-installer.bat
```

#### **PowerShell Script** (`build-installer.ps1`)
- Advanced building with validation
- Multiple options (EXE only, MSI only, skip tests, etc.)
- Color-coded output with progress indicators
- Professional logging

**Usage:**
```powershell
cd altarix
.\build-installer.ps1 -ExeOnly -OpenOutput
.\build-installer.ps1 -Help
```

### 4. **Deployment Scripts**

#### **Silent Installation** (`silent-install.bat`)
- Automated deployment without user interaction
- Custom installation paths
- System-wide or per-user modes

#### **Enterprise Batch Deployment** (`Installer/deploy-enterprise.bat`)
- Multiple deployment modes (user, machine, silent, repair, uninstall)
- Logging and reporting
- Suitable for batch deployments

#### **Enterprise PowerShell Deployment** (`Installer/deploy-enterprise.ps1`)
- Advanced enterprise deployment script
- Remote computer deployment support
- Deployment reports and logging
- Verbose output and help system

**Usage:**
```powershell
.\deploy-enterprise.ps1 -Mode Silent -Verbose
.\deploy-enterprise.ps1 -Mode Verify -ComputerList "computers.txt"
.\deploy-enterprise.ps1 -Help
```

### 5. **Documentation**

#### **INSTALLER-GUIDE.md**
Complete guide covering:
- Quick start instructions
- Installation options (EXE and MSI)
- Building instructions
- Customization options
- Troubleshooting
- Advanced MSI options

#### **Installer/README.md**
Comprehensive manual with:
- Overview and features
- Installation procedures
- Building from source
- Deployment scenarios
- Security best practices
- Distribution guidelines

---

## 🚀 Quick Start

### For End Users (Simple)

```batch
# Run this from altarix directory
build-installer.bat
```

Or with PowerShell:
```powershell
.\build-installer.ps1 -OpenOutput
```

**Output:** `build\installers\`
- `Altarix-1.5.5.exe` - For end users
- `Altarix-1.5.5.msi` - For IT administrators

### For IT Administrators

**Deploy to single machine:**
```batch
Installer\deploy-enterprise.bat silent
```

**Deploy to multiple machines:**
```powershell
$computers = @("PC1", "PC2", "PC3")
.\Installer\deploy-enterprise.ps1 -Mode Silent -ComputerList machines.txt
```

---

## 📋 Installer Features

### User-Facing Features
✓ Professional Windows installer UI  
✓ Custom installation directory selector  
✓ Desktop shortcut option (prompted)  
✓ Start Menu group integration  
✓ License agreement display  
✓ Uninstall support (Add/Remove Programs)  
✓ System file validation  
✓ Rollback on failure  

### Administrator Features
✓ All above plus:  
✓ Silent installation mode  
✓ System-wide (ALLUSERS) installation  
✓ Per-user installation  
✓ Installation logging  
✓ Remote deployment support  
✓ Repair/reinstall capabilities  
✓ Unattended deployment  

---

## 📁 File Structure

```
altarix/
├── build-installer.bat              # Batch build script
├── build-installer.ps1              # PowerShell build script
├── build.gradle                     # Updated with installer config
├── LICENSE.txt                      # Software license
├── silent-install.bat               # Simple deployment script
├── INSTALLER-GUIDE.md               # Installation guide
├── Installer/
│   ├── README.md                    # Complete installer documentation
│   ├── deploy-enterprise.bat        # Enterprise batch deployment
│   └── deploy-enterprise.ps1        # Enterprise PowerShell deployment
└── src/main/resources/
    └── icon/
        └── Altarix-1024.ico         # Installer icon
```

---

## 🛠️ Building Installers

### Prerequisites
- Windows 7 or later
- Gradle 7.0+ (usually comes with project)
- Java 25+
- 2GB free disk space

### Build Commands

**Gradle (most direct):**
```bash
gradlew packageInstallers   # Both EXE and MSI
gradlew packageExe          # EXE only
gradlew packageMsi          # MSI only
```

**Batch (recommended for users):**
```batch
build-installer.bat
```

**PowerShell (advanced options):**
```powershell
.\build-installer.ps1 -ExeOnly -SkipTest -OpenOutput
```

### Output Location
```
altarix/build/installers/
├── Altarix-1.5.5.exe
└── Altarix-1.5.5.msi
```

---

## 💻 Installation Methods

### Method 1: End Users (Simplest)
```
Double-click → Follow prompts → Done
```

### Method 2: Command Line (Users)
```batch
Altarix-1.5.5.exe --help
Altarix-1.5.5.exe --install-dir "D:\CustomPath"
```

### Method 3: Silent Installation
```batch
msiexec /i Altarix-1.5.5.msi /quiet /norestart
```

### Method 4: System-Wide Deployment
```batch
msiexec /i Altarix-1.5.5.msi ALLUSERS=1 /quiet /norestart
```

### Method 5: Automated Deployment Script
```batch
Installer\deploy-enterprise.bat silent
```

### Method 6: Enterprise Deployment
```powershell
.\Installer\deploy-enterprise.ps1 -Mode Silent -Verbose
```

---

## ⚙️ Customization

### Change Version
Edit `altarix/version.properties`:
```properties
version=1.5.5
```

### Change Application Name
Edit `altarix/build.gradle`:
```gradle
def appName = 'MyApp'
def vendorName = 'My Company'
```

### Change Icon
Replace `src/main/resources/icon/Altarix-1024.ico`

### Change License
Edit `LICENSE.txt` or create custom license

### Change Installation Details
Edit `build.gradle` to modify:
- Default installation path
- Application description
- Menu group names
- Vendor information

---

## 📊 Deployment Scenarios

### Scenario 1: Single User
```batch
# User runs installer
Altarix-1.5.5.exe
```

### Scenario 2: Department Deployment
```batch
# IT admin runs
msiexec /i Altarix-1.5.5.msi ALLUSERS=1 /quiet
```

### Scenario 3: Enterprise SCCM Deployment
```batch
# SCCM deploys
msiexec /i Altarix-1.5.5.msi ALLUSERS=1 /quiet /l*v deploy.log
```

### Scenario 4: Automated Lab Deployment
```powershell
# PowerShell script deploys to multiple machines
.\Installer\deploy-enterprise.ps1 -Mode Silent -ComputerList labs.txt
```

---

## 🔒 Security Considerations

### For Production
Consider code-signing installers:
```powershell
signtool.exe sign /f cert.pfx /p password Altarix-1.5.5.exe
```

### For Enterprise
- Use MSI for authenticated deployments
- Enable logging for audit trails
- Use network deployment tools (SCCM, etc.)
- Test on test machines first

---

## 📝 Verification Checklist

- [x] build.gradle configured with jpackage tasks
- [x] build-installer.bat created with error handling
- [x] build-installer.ps1 created with advanced options
- [x] LICENSE.txt included
- [x] Installer/README.md with complete documentation
- [x] INSTALLER-GUIDE.md with quick reference
- [x] Silent installation script created
- [x] Enterprise batch deployment script created
- [x] Enterprise PowerShell deployment script created
- [x] Application icons available
- [x] Version management in place
- [x] Desktop shortcut support enabled
- [x] Custom path selection enabled
- [x] Start menu integration enabled
- [x] Per-user installation support enabled

---

## 📞 Support & Resources

### Documentation Files
- [INSTALLER-GUIDE.md](INSTALLER-GUIDE.md) - Quick reference
- [Installer/README.md](Installer/README.md) - Complete documentation
- [build.gradle](build.gradle) - Build configuration

### Common Commands
```powershell
# Build all installers
.\build-installer.ps1

# Build EXE only
.\build-installer.ps1 -ExeOnly

# Silent deployment
msiexec /i Altarix-1.5.5.msi /quiet

# Check installation
Installer\deploy-enterprise.ps1 -Mode Verify
```

### Troubleshooting
1. Check [INSTALLER-GUIDE.md](INSTALLER-GUIDE.md) troubleshooting section
2. Review build logs: `build/installers/`
3. Check msiexec logs: `msiexec /l*v install.log`

---

## 🎯 Next Steps

1. **Build Installers:**
   ```batch
   cd altarix
   build-installer.bat
   ```

2. **Test Installation:**
   - Run `Altarix-1.5.5.exe` and verify
   - Install in custom location
   - Create desktop shortcut
   - Verify in Add/Remove Programs

3. **Deploy (optional):**
   - Use scripts from `Installer/` directory
   - Test silent installation
   - Validate remote deployment

4. **Distribute:**
   - Host on website
   - Create download page
   - Provide documentation
   - Document requirements

---

## 📋 File Checklist

| File | Purpose | Created |
|------|---------|---------|
| build.gradle | Build config with jpackage | ✓ |
| build-installer.bat | Batch build script | ✓ |
| build-installer.ps1 | PowerShell build script | ✓ |
| LICENSE.txt | Software license | ✓ |
| silent-install.bat | Silent deployment | ✓ |
| INSTALLER-GUIDE.md | Quick reference guide | ✓ |
| Installer/README.md | Complete documentation | ✓ |
| Installer/deploy-enterprise.bat | Batch deployment | ✓ |
| Installer/deploy-enterprise.ps1 | PowerShell deployment | ✓ |
| INSTALLATION-SETUP.md | This file | ✓ |

---

## 🎉 Summary

You now have a **complete, professional installer infrastructure** for Altarix AI Terminal with:

✅ **Easy building** - One command to create installers  
✅ **Professional UI** - Branded with your icon  
✅ **Enterprise features** - Silent deployment, logging, remote support  
✅ **User-friendly** - Desktop shortcuts, custom paths, license display  
✅ **Complete documentation** - Multiple guides for different audiences  
✅ **Flexible deployment** - Scripts for users, admins, and enterprises  

**Ready to build? Start with:**
```batch
build-installer.bat
```

Or use PowerShell:
```powershell
.\build-installer.ps1 -OpenOutput
```

---

*Created: May 2, 2026*  
*Altarix AI Terminal v1.5.5*  
*Professional Installer Setup Complete*
