---
title: The MSI removes the old version before installing the new one; removing it afterwards emptied the install folder
author: Radetski
date: 2026-11-18
branch: main
cost: { minutes: 9, tokens: 44458 }
id: l-ecf42e8b
scope: repo
status: active
replaces: l-4ad0ebe9
src: agent
touches:
  - desktopApp/packaging/windows/patch-msi-close-app.ps1
  - desktopApp/build.gradle.kts
---

## Decided
The MSI is patched after packageMsi to close a running SKIPI (taskkill actions before InstallValidate and, as SYSTEM, after InstallInitialize). RemoveExistingProducts now sits just after InstallValidate (1450): the old version goes first, then the new files.

## Tried and ruled out
- RemoveExistingProducts after InstallFinalize (the old placement): jpackage gives every file its own component and the component codes differ between versions, so the new components are "Absent". The old version's removal then deleted, by path, the files just written: version registered, install folder gone; only uninstall and install again helped. Reproduced from the verbose log.
- A position between InstallInitialize and InstallFinalize (1510): refused with error 2613.
- WiX CloseApplication: needs jpackage's main.wxs overridden, which Compose does not allow.

## Watch out
- The app is not started again after a silent msiexec install.
- Checked by installing 0.5.975 and then 0.5.976 over it, and 0.5.977 over a running SKIPI: exit 0, 87 files in the app folder, the app closed.
- Patching the same MSI twice fails on duplicate keys; the script runs once per packageMsi.

In the code: desktopApp/packaging/windows/patch-msi-close-app.ps1 (sequence 1450), desktopApp/build.gradle.kts (packageMsi doLast).
