# Blue-Green Deployment

Wave uses blue-green deployment for zero-downtime updates to supawave.ai.

## How It Works

At any time, one slot is **active** (serving traffic) and one is **inactive** (running but not receiving traffic):

```
Blue slot (port 9898)  ← Caddy routes traffic here
Green slot (port 9899) ← Standby, ready for traffic
```

**On each deployment:**

1. New release starts on the **inactive** slot
2. Health checks pass (`/healthz`)
3. Sanity checks pass (login, search, fetch work)
4. Caddy reloads config to swap traffic to the new slot
5. Old slot stays running for 30 minutes (ready for instant rollback)

**No downtime** — traffic switches in ~1 second via Caddy reload, old instance keeps serving if needed.

## First Deploy (Completed)

The first deploy migrated from legacy single-slot layout:

- **Before**: Single `wave` container, Caddy depends on it, health check timeout during redeployment
- **After**: Dual slots, Caddy independent, zero-downtime updates

**Downtime occurred** during first migration (stopping old container to free port 9898). Subsequent deploys have zero downtime.

## Deploying

### Auto-Deploy (Recommended)

Merging to `main` triggers auto-deploy:

```bash
git push origin your-branch
gh pr create  # Create PR
# Merge in GitHub UI
# Deployment starts automatically
```

### Manual Deploy

Trigger from CLI:

```bash
# Deploy current main
gh workflow run deploy-contabo.yml -f action=deploy

# Check status
gh run list --workflow=deploy-contabo.yml --limit=1
gh run view <run-id>  # Watch live logs
```

## Rollback

If a deploy breaks the app, rollback to the previous slot instantly:

```bash
gh workflow run deploy-contabo.yml -f action=rollback
```

This:
- Verifies the previous slot is healthy
- Runs sanity checks
- Swaps traffic back to the old slot
- Stops the broken slot (after cooldown)

Takes ~30 seconds total.

## Status

Check current deployment state:

```bash
gh workflow run deploy-contabo.yml -f action=status
```

Returns which slot is active, previous slot info, and cooldown timer.

## Workflow Actions

The deploy workflow accepts three actions via `workflow_dispatch`:

| Action | Behavior |
|--------|----------|
| `deploy` | Build, push, deploy new release to inactive slot, swap traffic. **Default.** |
| `rollback` | Swap traffic back to previous slot. No build. |
| `status` | Show current active slot, previous slot, and cooldown timer. No changes. |

## Deployment Files

**Production on Contabo:**

```
/home/wave/supawave/
├── incoming/          # Staging area for bundles
├── releases/
│   ├── current/       # Latest release (symlink from CI)
│   ├── previous/      # Backup of prior release
│   ├── blue/          # Blue slot release
│   └── green/         # Green slot release
├── shared/
│   ├── active-slot    # "blue" or "green"
│   ├── previous-slot  # Slot for rollback
│   ├── upstream.caddy # Dynamic upstream config
│   ├── indexes/
│   │   ├── blue/      # Lucene indexes (slot-specific)
│   │   └── green/
│   ├── sessions/
│   │   ├── blue/      # Jetty sessions (slot-specific)
│   │   └── green/
│   ├── mongo/         # MongoDB data (shared)
│   └── caddy-*        # Caddy config and data (shared)
└── deploy.lock        # Deployment lock file
```

**Local Codespace or Test:**

Set `DEPLOY_ROOT` to the local `deploy/caddy/` directory:

```bash
export DEPLOY_ROOT=/path/to/incubator-wave/deploy/caddy
bash deploy/caddy/deploy.sh deploy
```

## Sanity Check Gate

New releases must pass application-level sanity checks before traffic swap:

1. **Login** with test credentials (from `SANITY_ADDRESS` / `SANITY_PASSWORD`)
2. **Search** for a wave
3. **Fetch** the wave

If any check fails, the swap is blocked and the broken slot is stopped. Previous slot remains active.

To disable (not recommended): Don't set `SANITY_ADDRESS` / `SANITY_PASSWORD` secrets.

## Cooldown

After swapping traffic, the old slot runs idle for **30 minutes**:

- Allows quick rollback if issues appear
- Survives SSH disconnects (via `systemd-run`)
- Automatically stops after 30 minutes to free resources

To cancel cooldown:

```bash
systemctl --user stop wave-cooldown.timer
```

## Environment Variables

Required (set by CI workflow):

- `DEPLOY_ROOT` — Path to deploy state (`/home/wave/supawave` on Contabo)
- `WAVE_IMAGE` — Full image ref (`ghcr.io/vega113/incubator-wave:sha`)
- `CANONICAL_HOST` — Domain for TLS/email (`supawave.ai`)
- `ROOT_HOST` — Redirect target (`wave.supawave.ai`)
- `WWW_HOST` — Redirect target (`www.supawave.ai`)

Optional (for email notifications):

- `RESEND_API_KEY` — Resend API key
- `WAVE_EMAIL_FROM` — Sender address

Optional (for sanity checks):

- `SANITY_ADDRESS` — Test user email
- `SANITY_PASSWORD` — Test user password

## Troubleshooting

**Deploy stuck or slow?**

Check remote logs:

```bash
ssh wave@contabo.host
tail -f /home/wave/supawave/shared/logs/wave-blue.log  # Or wave-green
```

**Caddy not reloading?**

Ensure upstream.caddy is mounted correctly and Caddy container is running:

```bash
docker ps | grep caddy
docker compose -f /home/wave/supawave/releases/current/compose.yml \
  -p supawave exec -T caddy caddy version
```

**Can't rollback?**

Verify previous slot data:

```bash
cat /home/wave/supawave/shared/previous-slot
cat /home/wave/supawave/releases/green/image-ref  # Or blue
```

If missing, only the current slot exists — no rollback available. Deploy a known-good release again.

## See Also

- [Deployment Overview](./README.md)
- [CI Workflow](../../.github/workflows/deploy-contabo.yml)
- [Deploy Script](../../deploy/caddy/deploy.sh)
- [Compose File](../../deploy/caddy/compose.yml)
