# Deploying on Oracle Cloud Always Free

The primary target: one Ampere (ARM) VM from Oracle Cloud's Always Free tier, with a free DuckDNS hostname and Caddy's automatic HTTPS. Everything here costs nothing. The Raspberry Pi alternative is in [deploy-pi.md](deploy-pi.md).

## 1. Account

1. Sign up at [cloud.oracle.com](https://cloud.oracle.com). A credit card is needed for identity verification only. Pick your **home region** carefully: Always Free resources live there, and it can't be changed later. A region with Ampere capacity (e.g. Frankfurt, Ashburn, or Phoenix) saves retries.
2. **Upgrade to Pay As You Go** (Billing & Cost Management → Upgrade and Manage Payment). It still costs nothing as long as you stay within the Always Free limits, but it protects the VM from **idle reclamation**: Oracle may stop Always Free instances whose CPU, network, and memory use stay low for 7 days, which a small friend-group app easily does.
3. Right after upgrading, create a **budget** with an alert at a tiny amount (e.g. 1 in your currency), so any accidental paid resource is noticed at once.

## 2. VM

Create an instance (Compute → Instances → Create):

| Setting | Value |
|---|---|
| Image | Canonical Ubuntu 24.04 (aarch64) |
| Shape | VM.Standard.A1.Flex (Ampere), e.g. 2 OCPUs and 12 GB memory. Always Free allows up to 4 OCPUs and 24 GB in total. |
| Networking | A new VCN with a public subnet, and "Assign a public IPv4 address" |
| SSH key | Your public key |
| Boot volume | The default 50 GB is plenty (Always Free includes 200 GB in total) |

If you see "Out of capacity", retry later or pick another availability domain. Capacity usually frees up within hours to days. Once the VM exists, consider turning its ephemeral IP into a **reserved public IP** (Networking → IP Management), so it survives stopping the instance.

## 3. Firewall: open 80 and 443

Two layers block traffic by default:

1. **Security list** of the subnet (Networking → Virtual Cloud Networks → your VCN → Security Lists → Default): add ingress rules from `0.0.0.0/0` for **TCP 80** and **TCP 443** (and optionally **UDP 443** for HTTP/3). Leave SSH (22) as it is, or restrict it to your own IP.
2. **The VM's own iptables.** Oracle's Ubuntu images allow only SSH:
   ```sh
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
   sudo iptables -I INPUT 6 -p udp --dport 443 -j ACCEPT
   sudo netfilter-persistent save
   ```

Do **not** open 5432, 8080, or 9000: Postgres, the app, and Keycloak are reachable only inside Docker.

## 4. Docker

```sh
sudo apt-get update && sudo apt-get upgrade -y
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker "$USER"   # log out and back in afterwards
docker compose version
```

Turn on unattended security updates, which Ubuntu enables by default. Check with `systemctl status unattended-upgrades`.

## 5. Hostname (DuckDNS)

1. Log in at [duckdns.org](https://www.duckdns.org), create a subdomain (e.g. `adventr`), and set it to the VM's public IP.
2. Keep it up to date with a cron job on the VM (`crontab -e`). Replace the domain and token:
   ```
   */5 * * * * curl -fsS "https://www.duckdns.org/update?domains=adventr&token=YOUR-TOKEN&ip=" >/dev/null 2>&1
   ```
   With a reserved IP it rarely changes, but the updater costs nothing and covers a restore onto a new VM.

## 6. Configure and start

```sh
git clone <your repository URL> adventr && cd adventr
cp .env.example .env
nano .env    # fill in every value; APP_HOST=adventr.duckdns.org
chmod 600 .env
docker compose up -d --build
docker compose ps   # wait until postgres, keycloak, and app are healthy (Keycloak takes 1–2 minutes)
```

- The first build compiles the app on the VM, which takes a few minutes on ARM.
- Caddy obtains the certificate on the first request to `https://<APP_HOST>`.
- Save `.env` in your password manager now. You need it for a restore (see [restore.md](restore.md)).

Then set up the off-host backup copy as described in [restore.md](restore.md#setting-up-the-off-host-copy).

**Updating later:**

```sh
git pull && docker compose up -d --build
```

Flyway migrates the database on start. If something breaks, go back to the previous commit and rebuild, or restore from the backup.

## 7. Password-reset email (SMTP)

Keycloak sends only password-reset emails. A free relay is enough. For example, with Brevo:

1. Create a free Brevo account, verify a sender address (Senders & IPs), and create an **SMTP key** (SMTP & API).
2. Put the relay into `.env`:
   ```
   SMTP_HOST=smtp-relay.brevo.com
   SMTP_PORT=587
   SMTP_FROM=<the verified sender>
   SMTP_USER=<your Brevo login>
   SMTP_PASSWORD=<the SMTP key>
   ```
3. The realm file is imported only on the very first start. If Keycloak has already started once, also enter the settings in the admin console: `https://<APP_HOST>/auth/admin` → realm **adventr** → Realm settings → Email. Use **Test connection**, which needs an email address on the admin user.
4. **Verify end to end:** on the login page, click "Forgot password?", enter a registered email, receive the mail, set a new password, and log in with it. Check the spam folder too.

A Gmail app password works the same way (`smtp.gmail.com`, port 587, your Gmail address, and an app password from your Google account's security settings).

## 8. Google login

Optional. Users can then register and join with their Google account, and can link it to an existing password account.

1. In the [Google Cloud Console](https://console.cloud.google.com), create a project, then go to APIs & Services → **OAuth consent screen**:
   - External user type, app name "Adventr", your support email.
   - Scopes `openid`, `email`, and `profile`. These basic scopes need no Google review.
2. APIs & Services → Credentials → **Create OAuth client ID**:
   - Type: Web application.
   - Authorized redirect URI: `https://<APP_HOST>/auth/realms/adventr/broker/google/endpoint`
3. Put the client ID and secret into `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` in `.env`, and run `docker compose up -d`.
4. The realm import runs only once, and the provider was imported disabled. In the admin console, go to realm **adventr** → Identity providers → **google**, check the client ID and secret, switch **Enabled** on, and save.
5. **Publish** the consent screen (OAuth consent screen → Publish app). In testing mode, only listed test users can sign in.
6. **Smoke test:**
   - Log out, then use "Google" on the login page with a new Google account. You land in Adventr with your Google name.
   - Log in with Google using an email that already has a password account. Keycloak says the account exists and offers to link it after you confirm with the password, or with an emailed link. Afterwards both ways log into the **same** Adventr user and see the same groups.

## 9. Go-live checks

**Smoke test through the public URL:**

- [ ] Register a new account at `https://<APP_HOST>`, and log out and back in.
- [ ] Create a group, then copy the invite link from the group settings.
- [ ] In a private window, register a second account through the invite link, and join.
- [ ] Add an adventure with quick add, open it, edit it, and upload a photo from a phone.
- [ ] React 👍 with one account and 👎 with the other. Comment, edit the comment, and delete it.
- [ ] Log out; the Keycloak session ends too (the next login asks for the password).
- [ ] From a machine outside the VM, only 80 and 443 answer:
      `nmap -Pn -p 22,80,443,5432,8080,8081,9000 <APP_HOST>` shows 80 and 443 open, 22 only if you allowed it, and the rest closed or filtered.
- [ ] `https://<APP_HOST>/actuator/health` answers 404.

**Memory over 24 hours.** Leave the stack running for a day, then check:

```sh
docker stats --no-stream
docker inspect --format '{{.Name}} OOMKilled={{.State.OOMKilled}} restarts={{.RestartCount}}' $(docker compose ps -q)
free -h
```

- No container should show `OOMKilled=true` or restarts.
- The app stays within its `-Xmx384m` heap plus overhead (about 500–600 MB in total), and Keycloak within about 1 GB.
- On a 12 GB VM there is plenty of headroom. On smaller hosts, lower `KEYCLOAK_JAVA_OPTS_HEAP` or `APP_JAVA_OPTS` in `.env`.
