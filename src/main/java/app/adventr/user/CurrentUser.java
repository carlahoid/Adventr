package app.adventr.user;

/**
 * The logged-in user: local user id, display name, avatar version ({@code null} without an
 * avatar), and appearance ({@code accentColor} {@code null} for the default). Controllers declare
 * it as a handler method parameter; {@link CurrentUserArgumentResolver} fills it in.
 */
public record CurrentUser(long id, String displayName, String avatarVersion, Theme theme, String accentColor) {
}
