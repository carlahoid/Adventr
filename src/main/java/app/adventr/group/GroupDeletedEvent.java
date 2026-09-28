package app.adventr.group;

/**
 * Published inside the transaction that deleted a group. Database rows are already gone through
 * the {@code on delete cascade} foreign keys; listeners clean up what lives outside the
 * database. The image storage (task 6.7) listens with
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} to remove {@code /data/images/{groupId}}.
 */
public record GroupDeletedEvent(long groupId) {
}
