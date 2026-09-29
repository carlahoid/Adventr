#!/bin/sh
# Runs backup.sh every day at BACKUP_TIME (HH:MM, container time zone TZ). A plain loop instead
# of crond, so that the job sees the container's environment (database password, remote).
set -eu

# "docker compose run --rm backup rclone config" and similar: run that command instead.
if [ "$#" -gt 0 ]; then
	exec "$@"
fi

BACKUP_TIME="${BACKUP_TIME:-03:30}"

case "$BACKUP_TIME" in
	[01][0-9]:[0-5][0-9] | 2[0-3]:[0-5][0-9]) ;;
	*) echo "BACKUP_TIME must be HH:MM, got '$BACKUP_TIME'" >&2; exit 1 ;;
esac

echo "backup: daily at $BACKUP_TIME ($(date +%Z))"
while true; do
	now=$(date +%s)
	next=$(date -d "$(date +%Y-%m-%d) $BACKUP_TIME" +%s)
	if [ "$next" -le "$now" ]; then
		next=$((next + 86400))
	fi
	sleep $((next - now))
	# A failed run is logged and retried the next night; the service keeps running.
	backup.sh || echo "$(date '+%Y-%m-%d %H:%M:%S') backup: FAILED (exit $?)" >&2
done
