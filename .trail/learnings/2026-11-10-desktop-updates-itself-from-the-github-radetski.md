---
title: "Desktop updates itself from the GitHub release file that matches how it was installed, checked by its published SHA-256"
author: Radetski
date: 2026-11-10
branch: main
cost: { minutes: 30, tokens: 264308 }
id: l-115c511d
scope: repo
status: active
src: agent
touches:
  - shared/app/src/commonTest/kotlin/app/skipi/app/home/ProxyHomeGroupHeaderTest.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/ProxyHomePanels.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/ProxyHomeScreen.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiSubscriptionSummaryCard.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiSubscriptionSummaryDetails.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiSubscriptionSummaryPanel.kt
  - app/src/main/kotlin/app/AppStateSlices.kt
  - app/src/main/kotlin/data/AppSettingsPreferences.kt
  - app/src/main/kotlin/features/proxy/server/list/ProxyHomeAndroidInput.kt
  - app/src/main/kotlin/features/proxy/server/list/ProxyServerListGroups.kt
---

## Decided
Desktop downloads the release asset of its own package (MSI, portable zip, AppImage, deb, rpm, dmg), proves its size and the SHA-256 GitHub lists, then installs it. Windows: a hidden PowerShell helper waits for the app to exit, runs msiexec (one UAC prompt, the MSI is per machine) or swaps a portable folder aside and back on failure, and always starts the app again, the old one if the install did not go through. Linux AppImage: one rename over the running file. deb, rpm and macOS dmg are handed to the system (xdg-open, open) and the app keeps running. A release that has no file for this package is still offered as a link to its page.
The update state is the shared AppUpdateState used by Android, so one banner serves both; the banner button reads Download, Restart and update, Open installer or Open release page.
The app exits through the normal close path (tunnel stopped first), then the helper replaces it.

## Tried and ruled out
Guessing the asset by a name pattern: the release workflow uploads fixed names, so the app matches them exactly.
Replacing a portable folder in place: a failed copy would leave no app; the new files are unpacked aside and the old folder is restored on failure.

## Watch out
The Desktop jobs upload after the APK job, so a release can be public before its installers; the check then offers the page and the next check finds the file.
Nothing was run end to end on a real MSI, Linux or macOS: the Windows portable helper is tested for real with PowerShell, the MSI helper only for syntax and shape.
Shell here eats backslashes in heredocs: write Kotlin files with the editor, not cat.

In the code:
- features/updater/DesktopUpdatePackage.kt, GitHubReleaseParser.parseAvailability(desktopPackage)
- desktopApp app/DesktopAppUpdater.kt, DesktopUpdateDownloader.kt, DesktopUpdateInstaller.kt, DesktopUpdatePackageDetection.kt
