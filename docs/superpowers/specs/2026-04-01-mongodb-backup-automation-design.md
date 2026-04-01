# MongoDB Backup Automation Design

Status: Draft
Date: 2026-04-01

## Problem

The production MongoDB instance (`wiab` database on `supawave`) has no automated backups.
A data loss event would be unrecoverable. The existing `deploy/mongo/backup.sh` and
`restore.sh` scripts assume `mongodump`/`mongorestore` are installed on the host, but in
production these tools only exist inside the `supawave-mongo-1` container.

## Goals

1. Automated backups every 6 hours via host crontab.
2. Retain the last 10 backups on disk, delete older ones automatically.
3. Manual backup and restore scripts that work in the production Docker environment.
4. Documentation explaining setup, usage, and troubleshooting.

## Non-Goals

- Off-host backup replication (S3, rsync) — future work.
- MongoDB authentication wiring — tracked separately.
- Point-in-time recovery via oplog — overkill for current scale.

## Environment

| Property | Value |
|---|---|
| Host | `supawave` (ssh alias) |
| Deploy root | `/home/ubuntu/supawave` |
| MongoDB container | `supawave-mongo-1` (mongo:6.0) |
| Database | `wiab` |
| Collections | 12 (deltas, account, contacts, snapshots, attachments, feature_flags, etc.) |
| Data size | ~10 MB logical, ~414 MB on disk (WiredTiger) |
| Compressed backup | ~5-10 MB per archive |
| 10 backups | ~100 MB total |
| Disk available | 645 GB free (5% used of 678 GB) |
| mongodump location | `/usr/bin/mongodump` inside container |

## Design

### Approach: docker exec into existing container

Use `docker exec supawave-mongo-1 mongodump` with `--archive --gzip` streamed to stdout,
piped to a file on the host filesystem. This avoids installing `mongodb-database-tools` on
the host and keeps tools version-matched with the running MongoDB.

### File changes

#### 1. `deploy/mongo/backup.sh` — Rewrite

Replace the current script (which calls `mongodump` directly) with a Docker-aware version.

**Behavior:**
- Detects whether running inside Docker (container name configured) or with local tools.
- Default mode: `docker exec $CONTAINER mongodump --archive --gzip` piped to host file.
- Archive naming: `wiab-YYYYMMDD-HHMMSS.archive.gz` in `$BACKUP_DIR`.
- Default `BACKUP_DIR`: `$DEPLOY_ROOT/shared/mongo/backups` where `DEPLOY_ROOT`
  defaults to `/home/ubuntu/supawave`.
- After dump: validate file exists and size > 0.
- Rotation: list archives sorted by name (lexicographic = chronological), delete all but
  the newest `$KEEP_COUNT` (default 10).
- Disk check: before backup, warn and abort if less than 1 GB free on the backup partition.
- Exit codes: 0 success, 1 backup failed, 2 disk space insufficient.
- Stdout: prints the archive path on success.
- Stderr: logs timestamped messages for cron visibility.

**Environment variables:**
- `DEPLOY_ROOT` — base deploy directory (default: `/home/ubuntu/supawave`)
- `BACKUP_DIR` — override backup directory (default: `$DEPLOY_ROOT/shared/mongo/backups`)
- `MONGO_CONTAINER` — container name (default: `supawave-mongo-1`)
- `MONGO_DATABASE` — database to dump (default: `wiab`)
- `KEEP_COUNT` — number of backups to retain (default: `10`)

#### 2. `deploy/mongo/restore.sh` — Rewrite

Replace the current script with a Docker-aware version.

**Behavior:**
- If no argument given: list available backups in `$BACKUP_DIR` and exit.
- Accepts archive path as argument.
- Validates archive file exists and is non-empty.
- Prints a confirmation prompt ("This will DROP existing data. Continue? [y/N]") unless
  `--yes` flag is passed (for scripted use).
- Pipes archive into `docker exec -i $CONTAINER mongorestore --archive --gzip --drop`.
- Exit codes: 0 success, 1 restore failed, 66 archive not found.

**Environment variables:**
- `DEPLOY_ROOT`, `BACKUP_DIR`, `MONGO_CONTAINER`, `MONGO_DATABASE` — same defaults as
  backup.sh.

#### 3. `deploy/mongo/install-cron.sh` — New file

One-time setup script that installs the cron job on the host.

**Behavior:**
- Checks current crontab for existing Wave backup entry.
- If missing, appends: `0 */6 * * * /home/ubuntu/supawave/current/deploy/mongo/backup.sh >> /home/ubuntu/supawave/shared/logs/backup.log 2>&1`
- If present, prints current entry and exits.
- Verifies `backup.sh` is executable.
- Creates log directory if needed.

#### 4. `deploy/mongo/README.md` — Update

Expand the existing README to add:
- Quick-start section for manual backup and restore.
- Cron setup instructions (manual and via `install-cron.sh`).
- Retention policy explanation.
- Troubleshooting section (common failures, how to verify backups).
- Restore drill procedure.

### Cron schedule

```
0 */6 * * *   — runs at 00:00, 06:00, 12:00, 18:00 UTC
```

Output appended to `$DEPLOY_ROOT/shared/logs/backup.log`. The log file will be small
since each entry is just a few lines of status + the archive path.

### Retention

Backups are named `wiab-YYYYMMDD-HHMMSS.archive.gz`. The rotation logic:
1. `ls -1` the backup directory, filter for `wiab-*.archive.gz`.
2. Sort lexicographically (ISO timestamp ensures chronological order).
3. If count > `KEEP_COUNT`, delete the oldest files.

This gives ~60 hours of backup history at the 6-hour interval with 10 retained.

### Restore procedure

1. Stop the Wave application: `docker compose stop wave` (Mongo stays running).
2. Run restore: `./deploy/mongo/restore.sh /path/to/archive.gz`
3. Start Wave: `docker compose start wave`

Stopping Wave first prevents it from writing while the restore drops and replaces data.

### Deployment integration

The `deploy/caddy/deploy.sh` script already creates `$DEPLOY_ROOT/shared/` directories.
The backup directory (`shared/mongo/backups/`) will be created by `backup.sh` on first run.
The cron job points to `$DEPLOY_ROOT/current/deploy/mongo/backup.sh`, which follows the
existing `current` symlink so it always runs the latest deployed version.

### Security considerations

- No credentials needed currently (MongoDB has no auth enabled).
- When auth is added later, the scripts will need `MONGODB_URI` or separate credential
  env vars. The README will note this as a future enhancement.
- Backup files contain all database contents — file permissions should be 600 (owner-only).
- The backup directory should be owned by the deploy user (ubuntu).

## Testing

- Run `backup.sh` manually on the host, verify archive is created and non-empty.
- Run `restore.sh` with the archive, verify data is intact.
- Run backup 12 times, verify only 10 archives remain (oldest 2 deleted).
- Simulate low disk space (set threshold high), verify script aborts gracefully.
- Install cron, wait 6 hours, verify automatic backup appears.
