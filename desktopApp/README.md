# SKIPI Desktop

Desktop запускает SKIPI Core **внутри JVM-процесса** через нативную библиотеку
(`libskipicore.so` на Linux, `skipicore.dll` на Windows). В дистрибутив не входит и не запускается отдельный
`xray.exe`.

## Сборка и компиляторы

По умолчанию Gradle собирает библиотеку из соседнего checkout `../skipi-core` с автоматическим обнаружением компилятора под хост:
- **Linux:** обнаружение `gcc` и `g++` в `$PATH`, `/usr/bin`, `/usr/local/bin`; собирает `dist/libskipicore.so`.
- **Windows:** MSYS2 UCRT64 GCC в `C:/msys64/ucrt64/bin` или `$PATH`; собирает `dist/skipicore.dll`.

### Linux

1. Установить JDK 26, Go версии из `../skipi-core/go.mod` и GCC (`build-essential`).
2. Сборка из корня `skipi-box`:

```bash
./gradlew :desktopApp:compileKotlinDesktop
./gradlew :desktopApp:test
```

### Windows

1. Установить JDK 26 и Go версии из `../skipi-core/go.mod`.
2. Установить MSYS2 и пакет `mingw-w64-ucrt-x86_64-gcc`.
3. Выполнить из корня `skipi-box`:

```powershell
.\gradlew.bat :desktopApp:packageMsi
```

Результат: `desktopApp/build/compose/binaries/main/msi/SKIPI-<version>.msi`.
Перед упаковкой Gradle:

- строит нативную библиотеку (`libskipicore.so` / `skipicore.dll`) как Go `c-shared` библиотеку;
- кладёт библиотеку, `geoip.dat`, `geosite.dat` и лицензии в `app/resources`;
- скачивает только geo-данные из проверенного по SHA-256 архива Xray, но не
  его исполняемый файл.

Если Core уже собран отдельно, можно не собирать соседний checkout:

```bash
# Linux
./gradlew :desktopApp:assemble -PskipiCoreDesktopLibrary=/path/to/libskipicore.so

# Windows
.\gradlew.bat :desktopApp:packageMsi -PskipiCoreDesktopLibrary=C:\path\to\skipicore.dll
```

Для явного указания компиляторов и Go доступны свойства:
- `-PskipiCoreDesktopCc=<path-or-binary>`
- `-PskipiCoreDesktopCxx=<path-or-binary>`
- `-PskipiCoreDesktopGo=<path-or-binary>`

## Установщики

| ОС | Артефакты релиза | Gradle |
|---|---|---|
| Windows x64 | `SKIPI-windows-x64.msi`, `SKIPI-windows-x64-portable.zip` | `:desktopApp:packageMsi` |
| Linux x64 | `SKIPI-linux-x64.AppImage`, `.deb`, `.rpm`, `-portable.tar.gz` | `:desktopApp:packageDeb`, `:desktopApp:packageRpm -PskipiPackageRpm=true` (the license option is only valid for the rpm); AppImage: `packaging/linux/build-appimage.sh` |
| macOS arm64 | `SKIPI-macos-arm64.dmg` | `:desktopApp:packageDmg` |

- MSI ставится на всю машину (TUN требует прав администратора); `upgradeUuid` в `build.gradle.kts` неизменен, чтобы новая версия обновляла установленную. Для сборки нужен WiX 5 с расширениями `WixToolset.Util.wixext` и `WixToolset.UI.wixext` (`dotnet tool install --global wix --version 5.0.2`).
- deb/rpm собирает jpackage (для rpm нужен `rpmbuild`). Образ ставится в `/opt/skipi`.
- AppImage собирается из образа `createDistributable` скриптом и закреплённым `appimagetool` (см. CI).
- `packaging/verify-listing.sh <linux|windows>` проверяет список файлов пакета (Core, hev, geo, запускатор); тесты: `bash desktopApp/packaging/tests/verify-listing-test.sh`.
- Установщики не подписаны (Windows SmartScreen и macOS Gatekeeper покажут предупреждение).

## Проверка настоящего нативного запуска

```powershell
.\gradlew.bat :desktopApp:prepareDesktopCoreRuntime
$env:SKIPI_CORE_INTEGRATION_DIR = (Resolve-Path `
  'desktopApp\build\generated\skipi-core-resources\common').Path
.\gradlew.bat :desktopApp:test `
  --tests app.skipi.desktop.DesktopCoreFfmIntegrationTest
```

Тест загружает реальную библиотеку через Java FFM, запускает и останавливает Core два
раза и использует правило `geosite`, проверяя staged geo-базы.
