#!/bin/sh
# One backup run: dumps both databases and archives the images volume into
# /backups/daily/<date>, copies it to /backups/weekly on Sundays, keeps the newest 7 daily and
# 4 weekly backups, and copies everything to the off-host rclone remote, if one is configured.
#
# Run by the backup service every night; run it by hand with:
#   docker compose exec backup backup.sh
set -eu

KEEP_DAILY=7
KEEP_WEEKLY=4
root=/backups
stamp=$(date +%Y-%m-%d)
target="$root/daily/$stamp"
partial="$target.partial"

log() {
	echo "$(date '+%Y-%m-%d %H:%M:%S') backup: $*"
}

# Deletes all but the newest $2 complete backups in directory $1.
prune() {
	[ -d "$1" ] || return 0
	ls -1 "$1" | grep -v '\.partial$' | sort -r | tail -n +"$(($2 + 1))" | while read -r old; do
		log "removing $1/$old"
		rm -rf "${1:?}/$old"
	done
}

log "starting $stamp"
rm -rf "$partial"
mkdir -p "$partial"

# Custom format: compressed, and pg_restore can recreate objects with their owners.
for db in adventr keycloak; do
	pg_dump --format=custom --file="$partial/$db.dump" "$db"
done
tar -C /data -czf "$partial/images.tar.gz" images

# Only complete backups get their final name, so a failed run never replaces a good one.
rm -rf "$target"
mv "$partial" "$target"
log "wrote $target ($(du -sh "$target" | cut -f1))"

if [ "$(date +%u)" = 7 ]; then
	mkdir -p "$root/weekly"
	rm -rf "$root/weekly/$stamp"
	cp -a "$target" "$root/weekly/$stamp"
	log "kept $stamp as a weekly backup"
fi

prune "$root/daily" "$KEEP_DAILY"
prune "$root/weekly" "$KEEP_WEEKLY"

if [ -n "${BACKUP_RCLONE_REMOTE:-}" ]; then
	remote="${BACKUP_RCLONE_REMOTE%/}"
	# copy, never sync: an empty backups volume on a rebuilt host must not wipe the remote.
	# The remote is pruned by age instead, which keeps about as many backups as locally.
	rclone copy "$root" "$remote" --exclude "*.partial/**"
	remote_dirs=$(rclone lsf "$remote" --dirs-only)
	case "$remote_dirs" in *daily/*) rclone delete "$remote/daily" --min-age "$((KEEP_DAILY + 1))d" ;; esac
	case "$remote_dirs" in *weekly/*) rclone delete "$remote/weekly" --min-age "$((KEEP_WEEKLY * 7 + 1))d" ;; esac
	rclone rmdirs "$remote" --leave-root
	log "copied to $remote"
else
	log "BACKUP_RCLONE_REMOTE is not set; the backup stays on this host only"
fi

log "done"
