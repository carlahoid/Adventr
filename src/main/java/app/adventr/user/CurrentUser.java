package app.adventr.user;

/**
 * The logged-in user, as a local user id plus display name. Controllers declare it as a
 * handler method parameter; {@link CurrentUserArgumentResolver} fills it in.
 */
public record CurrentUser(long id, String displayName) {
}
