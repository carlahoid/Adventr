# Nightly backups: pg_dump (same major version as the server), tar, and rclone.
# Multi-arch (linux/amd64, linux/arm64), like the rest of the stack.
FROM postgres:17-alpine
RUN apk add --no-cache rclone tzdata
COPY --chmod=755 backup.sh entrypoint.sh /usr/local/bin/
ENV RCLONE_CONFIG=/config/rclone/rclone.conf
ENTRYPOINT ["entrypoint.sh"]
