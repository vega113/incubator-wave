# Supawave Deployment Plan

Task epic: `incubator-wave-deployment`
Branch: `deploy-supawave-contabo`
Worktree: `/Users/vega/devroot/worktrees/incubator-wave/deploy-supawave-contabo`
Target host: `ubuntu@86.48.3.138`
Domain: `supawave.ai`

## Goal
Automate deployment of Wave to the Contabo host from GitHub Actions and wire DNS for `supawave.ai` through Cloudflare so the app is reachable on a stable public domain.

## Scope
- Define the runtime topology on the Contabo host.
- Add a GitHub Actions deployment workflow.
- Add repo-owned deploy scripts/config for bootstrap and rollout.
- Configure Cloudflare DNS records for the chosen hostnames.
- Record the server/bootstrap assumptions in docs and Beads.

## Proposed topology
- Run Wave from an installed distribution or containerized service on the Contabo host.
- Terminate public HTTP(S) through a reverse proxy on the host.
- Keep Wave itself bound to localhost/private interfaces where possible.
- Use GitHub Actions over SSH for deployment.
- Use Cloudflare DNS for apex and service hostnames.

## Immediate checks
- Verify current software on the server: Docker, reverse proxy, open ports, filesystem layout.
- Verify Cloudflare zone ownership and whether `supawave.ai` already exists in the current account.
- Decide deployment shape:
  - Docker Compose + reverse proxy, or
  - `:wave:installDist` + systemd + reverse proxy.
- Decide canonical public hostname set:
  - `supawave.ai`
  - `www.supawave.ai`
  - optional app host like `wave.supawave.ai`

## Deliverables
- New deployment epic + tasks in Beads.
- Plan/doc for deployment topology and secrets.
- GitHub Actions workflow and deploy scripts on a branch/PR.
- Cloudflare DNS applied or scripted.
