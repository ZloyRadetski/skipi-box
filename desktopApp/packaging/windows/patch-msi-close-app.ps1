# Makes the MSI close a running SKIPI (and its hev tunnel process) instead of failing or asking for a restart.
#
# jpackage cannot add custom actions, so the finished MSI is edited with the Windows Installer API:
#  - SkipiCloseAppNow / SkipiCloseHevNow: immediate, before InstallValidate (which is where Windows Installer
#    finds files in use). Run as the installing user, so they end an application of the same privileges.
#  - SkipiCloseApp / SkipiCloseHev: deferred and run as SYSTEM inside the install script, before the old
#    version is removed, so they also end an elevated SKIPI (TUN mode). RemoveExistingProducts is moved
#    after InstallFinalize (the old version is removed once the new files are in), the placement Windows Installer documents for upgrades that replace files.
# taskkill's exit code 128 (no such process) is ignored (msidbCustomActionTypeContinue).
param([Parameter(Mandatory = $true)][string]$Msi)

$ErrorActionPreference = 'Stop'
$installer = New-Object -ComObject WindowsInstaller.Installer
$db = $installer.GetType().InvokeMember('OpenDatabase', 'InvokeMethod', $null, $installer, @((Resolve-Path $Msi).Path, 1))

function Invoke-Sql([string]$sql) {
    $view = $db.GetType().InvokeMember('OpenView', 'InvokeMethod', $null, $db, @($sql))
    $view.GetType().InvokeMember('Execute', 'InvokeMethod', $null, $view, $null) | Out-Null
    $view.GetType().InvokeMember('Close', 'InvokeMethod', $null, $view, $null) | Out-Null
}

$immediate = 34 + 64          # exe in a directory, continue on failure
$deferred = 34 + 64 + 1024 + 2048   # ... deferred, as SYSTEM
$kill = '"[SystemFolder]taskkill.exe" /F /T /IM'

# A directory-based exe action needs its directory in the Directory table, standard folders included.
Invoke-Sql "INSERT INTO ``Directory`` (``Directory``, ``Directory_Parent``, ``DefaultDir``) VALUES ('SystemFolder', 'TARGETDIR', '.')"

$actions = @(
    @('SkipiCloseAppNow', $immediate, "$kill SKIPI.exe"),
    @('SkipiCloseHevNow', $immediate, "$kill hev-socks5-tunnel.exe"),
    @('SkipiCloseApp', $deferred, "$kill SKIPI.exe"),
    @('SkipiCloseHev', $deferred, "$kill hev-socks5-tunnel.exe")
)
foreach ($a in $actions) {
    Invoke-Sql ("INSERT INTO ``CustomAction`` (``Action``, ``Type``, ``Source``, ``Target``) VALUES ('{0}', {1}, 'SystemFolder', '{2}')" -f $a[0], $a[1], $a[2].Replace("'", "''"))
}

# InstallValidate is 1400, InstallInitialize 1500: the immediate ones go just before the former.
Invoke-Sql "INSERT INTO ``InstallExecuteSequence`` (``Action``, ``Sequence``) VALUES ('SkipiCloseAppNow', 1395)"
Invoke-Sql "INSERT INTO ``InstallExecuteSequence`` (``Action``, ``Sequence``) VALUES ('SkipiCloseHevNow', 1396)"
Invoke-Sql "INSERT INTO ``InstallExecuteSequence`` (``Action``, ``Sequence``) VALUES ('SkipiCloseApp', 1501)"
Invoke-Sql "INSERT INTO ``InstallExecuteSequence`` (``Action``, ``Sequence``) VALUES ('SkipiCloseHev', 1502)"
Invoke-Sql "UPDATE ``InstallExecuteSequence`` SET ``Sequence``=6601 WHERE ``Action``='RemoveExistingProducts'"

$db.GetType().InvokeMember('Commit', 'InvokeMethod', $null, $db, $null) | Out-Null
Write-Output "Patched $Msi"
