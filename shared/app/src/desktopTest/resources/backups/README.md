# Backup fixtures

Backups written by released SKIPI versions, with every personal value replaced (see `tools/sanitize_backup.py`).
`BackupFixtureCompatibilityTest` restores each one and checks that nothing it holds is lost.

| File | Written by |
|---|---|
| `skipi-backup-0.4.1.json` | SKIPI 0.4.1 (Android) |
| `skipi-backup-0.5.0.json` | SKIPI 0.5.0 pre-release (Android) |

Add a fixture whenever a release changes what a backup holds. Never commit a real backup.
Profile-owned strategy groups are not in these files: the restore re-derives them from the profile text, and the
reconciler's own tests cover that.
