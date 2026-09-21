# SKIPI Desktop

Desktop запускает SKIPI Core **внутри JVM-процесса** через нативную библиотеку
`skipicore.dll`. В дистрибутив не входит и не запускается отдельный
`xray.exe`.

## Сборка Windows

По умолчанию Gradle собирает библиотеку из соседнего checkout `../skipi-core` и
ожидает MSYS2 UCRT64 GCC в `C:/msys64/ucrt64/bin`.

1. Установить JDK 26 и Go версии из `../skipi-core/go.mod`.
2. Установить MSYS2 и пакет `mingw-w64-ucrt-x86_64-gcc`.
3. Выполнить из корня `skipi-box`:

```powershell
.\gradlew.bat :desktopApp:packageMsi
```

Результат: `desktopApp/build/compose/binaries/main/msi/SKIPI-<version>.msi`.
Перед упаковкой Gradle:

- строит `../skipi-core/dist/skipicore.dll` как Go `c-shared` библиотеку;
- кладёт DLL, `geoip.dat`, `geosite.dat` и лицензии в `app/resources`;
- скачивает только geo-данные из проверенного по SHA-256 архива Xray, но не
  его исполняемый файл.

Если Core уже собран отдельно, можно не собирать соседний checkout:

```powershell
.\gradlew.bat :desktopApp:packageMsi `
  -PskipiCoreDesktopLibrary=C:\path\to\skipicore.dll
```

Для нестандартной установки компилятора доступны
`-PskipiCoreDesktopCc=C:\path\to\gcc.exe` и
`-PskipiCoreDesktopCxx=C:\path\to\g++.exe`.

## Проверка настоящего нативного запуска

```powershell
.\gradlew.bat :desktopApp:prepareDesktopCoreRuntime
$env:SKIPI_CORE_INTEGRATION_DIR = (Resolve-Path `
  'desktopApp\build\generated\skipi-core-resources\common').Path
.\gradlew.bat :desktopApp:test `
  --tests app.skipi.desktop.DesktopCoreFfmIntegrationTest
```

Тест загружает реальную DLL через Java FFM, запускает и останавливает Core два
раза и использует правило `geosite`, проверяя staged geo-базы.
