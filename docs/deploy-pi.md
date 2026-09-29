# Deploying on a Raspberry Pi (Cloudflare Tunnel)

The alternative to the Oracle VM ([deploy-oracle.md](deploy-oracle.md)): a Raspberry Pi at home, reachable through a free **Cloudflare Tunnel**. The Pi opens an outbound connection to Cloudflare, so the router needs **no port forwarding** and the home IP stays hidden. Cloudflare terminates HTTPS. Caddy still routes `/auth` to Keycloak and everything else to the app, but only inside Docker, over plain HTTP.

## What you need

- A **Raspberry Pi 4 or 5 with 8 GB** of RAM. Keycloak alone needs about 1 GB.
- **Storage:** a USB SSD is strongly recommended over the SD card, because a database writing to an SD card wears it out.
- **A free Cloudflare account.**
- **A domain whose DNS is managed by Cloudflare.** A tunnel's public hostname must be in a Cloudflare zone, so a DuckDNS subdomain **can't** be used here. To keep everything free, use a free domain that Cloudflare accepts as a zone, e.g. a subdomain from a free registry on the Public Suffix List, such as `eu.org`. Its approval can take a few weeks. A cheap paid domain also works, but it is the one part of the stack that would cost money.

## 1. Operating system and Docker

1. Flash **Raspberry Pi OS Lite (64-bit)** with Raspberry Pi Imager. In its settings, set a hostname, a user, and SSH with your public key. Booting from the SSD is supported on the Pi 4 (after a bootloader update) and on the Pi 5.
2. Update and install Docker:
   ```sh
   sudo apt-get update && sudo apt-get full-upgrade -y
   curl -fsSL https://get.docker.com | sudo sh
   sudo usermod -aG docker "$USER"   # log out and back in afterwards
   sudo apt-get install -y unattended-upgrades
   ```

## 2. Cloudflare Tunnel

1. Add your domain to Cloudflare (free plan) and switch its name servers to Cloudflare's. Wait until the zone is active.
2. Under SSL/TLS → Edge Certificates, turn on **Always Use HTTPS**.
3. Go to Zero Trust → Networks → **Tunnels** → Create a tunnel:
   - Type **Cloudflared**, named e.g. `adventr`.
   - On the "Install connector" step, copy only the **token**, the long string after `--token`. Docker Compose runs the connector for you.
4. Add a **public hostname** to the tunnel:
   - Subdomain/domain: your `APP_HOST`, e.g. `adventr.example.eu.org`.
   - Service: type **HTTP**, URL **`caddy:80`**.

## 3. Configure and start

```sh
git clone <your repository URL> adventr && cd adventr
cp .env.example .env
nano .env
chmod 600 .env
```

In `.env`, besides the usual values:

```
APP_HOST=adventr.example.eu.org
CLOUDFLARE_TUNNEL_TOKEN=<the token>
# Makes every "docker compose" command use the tunnel variant, including those in restore.md.
COMPOSE_FILE=docker-compose.yml:docker-compose.tunnel.yml
```

Then:

```sh
docker compose up -d --build
docker compose ps          # postgres, keycloak, and app become healthy; Keycloak takes a few minutes on a Pi
docker compose logs cloudflared | grep -i registered   # the tunnel is connected
```

With `COMPOSE_FILE` set, this is the same as `docker compose -f docker-compose.yml -f docker-compose.tunnel.yml up -d --build`.

**What the tunnel variant changes** (`docker-compose.tunnel.yml`):

- It adds the `cloudflared` service.
- Caddy publishes **no ports** and uses `Caddyfile.tunnel`, which serves plain HTTP and tells the app and Keycloak that visitors use HTTPS.
- The app's server-side calls to `https://<APP_HOST>/auth` (OIDC discovery and tokens) go out through Cloudflare. That is why the app waits for `cloudflared`. If the tunnel isn't connected yet, the app fails its first start and Docker restarts it until it is.

Next:

- Save `.env` in your password manager.
- Set up the off-host backup copy as described in [restore.md](restore.md#setting-up-the-off-host-copy). An off-host copy matters even more at home, where a power surge or a failed SSD takes everything.
- Password-reset email and Google login work exactly as on the Oracle VM: see [deploy-oracle.md, sections 7 and 8](deploy-oracle.md#7-password-reset-email-smtp). Use your `APP_HOST` in the Google redirect URI.

**Updating later:** `git pull && docker compose up -d --build`.

## 4. Go-live checks

Run the smoke test from [deploy-oracle.md, section 9](deploy-oracle.md#9-go-live-checks), with these differences:

- **Port scan:** your home IP should show **no** open ports for Adventr. Nothing is forwarded on the router, and `docker compose ps` shows no published ports.
- **Memory:** check it the same way. On an 8 GB Pi the stack uses about 2 GB.
- **Uploads:** Cloudflare's free plan accepts request bodies up to 100 MB, well above the app's 10 MB image limit.
- **Caching:** Cloudflare doesn't cache the pages, which are HTML without cache headers, or the images, which are marked `Cache-Control: private`.
