# Backups and restore

## What is backed up

The `backup` service (see `backup/`) runs every night at `BACKUP_TIME` (default 03:30, time zone `TZ`). Each run writes one folder to the `backups` volume:

| File | Contents |
|---|---|
| `adventr.dump` | The app database: users, groups, memberships, invites, adventures, reactions, comments (`pg_dump --format=custom`) |
| `keycloak.dump` | The Keycloak database: accounts, password hashes, linked Google logins, realm settings |
| `images.tar.gz` | The images volume (adventure images and thumbnails) |

- **Where backups are kept:**
  - `daily/<date>/` holds the newest 7 runs.
  - `weekly/<date>/` holds a copy of each Sunday's run, keeping the newest 4.
  - A run that fails halfway never replaces a good backup.
- **Off-host copy:** Each run copies the whole folder to `BACKUP_RCLONE_REMOTE`, then deletes remote backups older than 8 days (daily) and 29 days (weekly).
  - It uses `rclone copy`, never `sync`, so a rebuilt host with an empty volume can't wipe the remote.
- **Not in the backup:** `.env` and `backup/rclone/rclone.conf`. Keep both in a password manager. You need the **same** `KEYCLOAK_CLIENT_SECRET` to restore, because the restored Keycloak database contains it.

> The backups contain everyone's private data and Keycloak's password hashes. Wrap the remote in an rclone `crypt` remote (step 2 below), so that the storage provider only sees encrypted files.

## Setting up the off-host copy

1. Create a remote for the storage, e.g. Google Drive (`drive`) or Oracle Object Storage (`s3` with provider `Other`, or `oracleobjectstorage`):
   ```sh
   docker compose run --rm backup rclone config
   ```
   The configuration is saved to `backup/rclone/rclone.conf`, which git ignores. Google Drive needs a browser for its OAuth step. On a headless VM, choose "No" for auto config and follow rclone's instructions to authorize on your own computer.
2. Recommended: add a `crypt` remote on top of it, e.g. `adventr-crypt` pointing at `gdrive:adventr-backups`, with a generated password. Save the password in your password manager: without it, the backups can't be read.
3. Set the remote in `.env`, e.g. `BACKUP_RCLONE_REMOTE=adventr-crypt:`, and apply it with `docker compose up -d backup`.
4. Run one backup now and check that it reaches the remote:
   ```sh
   docker compose exec backup backup.sh
   docker compose exec backup sh -c 'rclone lsf -R "$BACKUP_RCLONE_REMOTE" | head'
   ```

Check `docker compose logs backup` now and then. A failed night logs `backup: FAILED`.

## Restore on a new host

Use this after losing the host, or to move to another one. Commands run in the repository folder on the new host.

1. **Prepare the host** as in [deploy-oracle.md](deploy-oracle.md) or [deploy-pi.md](deploy-pi.md), up to (but not including) `docker compose up -d`. Put back your saved `.env`, and `backup/rclone/rclone.conf` if you use rclone.
2. **Start only Postgres.** On an empty volume, its init script creates the empty `adventr` and `keycloak` databases and their owners:
   ```sh
   docker compose up -d postgres
   docker compose build backup
   ```
3. **Pick a backup** and copy it into the `backups` volume. List the available backups first:
   ```sh
   docker compose run --rm --entrypoint sh backup -c 'rclone lsf "$BACKUP_RCLONE_REMOTE/daily"'
   docker compose run --rm --entrypoint sh backup -c 'rclone copy "$BACKUP_RCLONE_REMOTE/daily/2026-10-14" /backups/restore'
   ```
   (If the old host still exists, you can copy a folder from its `backups` volume instead.)
4. **Restore both databases:**
   ```sh
   docker compose run --rm --entrypoint sh backup -c '
     for db in adventr keycloak; do
       pg_restore --dbname="$db" --clean --if-exists --exit-on-error "/backups/restore/$db.dump" || exit 1
     done'
   ```
5. **Restore the images** into the `images` volume. The archive holds an `images/` folder, and the app runs as uid 10001:
   ```sh
   docker run --rm -v adventr_images:/data/images -v adventr_backups:/backups alpine sh -c '
     tar -C /data/images --strip-components=1 -xzf /backups/restore/images.tar.gz &&
     chown -R 10001:10001 /data/images'
   ```
6. **Start everything:**
   ```sh
   docker compose up -d
   docker compose ps   # all services healthy
   ```
7. **Point the hostname at the new host.** For DuckDNS, update the IP (the updater does this on its next run). For a Cloudflare Tunnel, the tunnel reconnects by itself.
8. **Check:**
   - Log in with an existing account.
   - Your groups, adventures, reactions, and comments are there.
   - Adventure images load.
   - `docker compose exec backup backup.sh` succeeds.
9. **Clean up:**
   ```sh
   docker run --rm -v adventr_backups:/backups alpine rm -rf /backups/restore
   ```

### Restoring on the same host

This is for rolling back after a bad change, not after losing the host. Stop the writers first, then follow steps 3–6:

```sh
docker compose stop app keycloak
```

`pg_restore --clean` drops and recreates every object, so the databases end up exactly as they were in the backup. Images uploaded after the backup stay as unused files on disk. Images deleted after the backup come back.

## Restore test log

The deployment spec requires the procedure to have been tested at least once on a fresh machine (task 9.2).

| Date | From backup | Target | Result | Notes |
|---|---|---|---|---|
| 2026-09-29 | Local test data | Fresh Postgres 17 container (developer laptop) | Databases and images restored, table owners kept | Rehearsal of steps 4–5 only; not the full fresh-machine test |
| | | | | Full test on a fresh machine: still to do |
