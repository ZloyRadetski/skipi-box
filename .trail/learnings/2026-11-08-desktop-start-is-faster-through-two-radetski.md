---
title: "Desktop start is faster through two class-data-sharing archives made by the build, because the packaged runtime has none"
author: Radetski
date: 2026-11-08
branch: main
cost: { minutes: 34, tokens: 141829 }
id: l-0e1de187
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopWindowsAutostart.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/app/DesktopWindowsAutostartTest.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/app/DesktopAppCdsTest.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopAppCds.kt
  - desktopApp/build.gradle.kts
  - desktopApp/src/main/kotlin/app/skipi/desktop/home/DesktopServerLibrary.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/resources/DesktopResourceFileServices.kt
---

## Decided
The jpackage runtime ships without a JDK class-data-sharing archive, so every start loaded and verified all classes from scratch. After createDistributable on Windows, a build task makes two archives in place: the JDK one (the launcher run with JAVA_TOOL_OPTIONS=-Xshare:dump, which makes the JVM write bin/server/classes.jsa and exit) and the app one (a training start with -XX:ArchiveClassesAtExit and -Dskipi.exitAfterMillis=12000, which exits by itself). Only then is -XX:SharedArchiveFile=$APPDIR/skipi.jsa appended to the launcher config. Measured on a copy of the real profile: window at 2.58 s before, 1.92 s after.
The training start uses a scratch APPDATA and XDG_RUNTIME_DIR, so it never touches real settings or hands itself to a running SKIPI.

## Tried and ruled out
Having the running app dump its own archive (jcmd VM.cds dynamic_dump through the DiagnosticCommand MBean): the JVM needs -XX:+RecordDynamicDumpInfo for it, and with a named -XX:SharedArchiveFile that flag makes the JVM refuse to start when the file is missing.
-XX:+AutoCreateSharedArchive: the archive is written only on a normal JVM exit, and the app lives in the tray and is rarely exited.
-XX:TieredStopAtLevel=1 and -XX:-UsePerfData: about 0.2 s or nothing, not worth the lower peak speed.

## Watch out
The archives belong to the exact jars (path, size, mtime) they were made for. Copying the folder changes mtimes and the JVM then silently ignores the archive; measure in place. An installer (MSI) builds its own image and does not get these archives.
A machine can mark SKIPI.exe "run as administrator" in the AppCompatFlags Layers registry key (it is set for the dev builds here). CreateProcess then fails with error 740, so the build task sets __COMPAT_LAYER=RunAsInvoker. The same mark also stops the Run-key autostart: Windows does not start elevation-requiring programs at login.
In the code: desktopApp/build.gradle.kts trainStartupArchives, DesktopTrainingExit.kt.
