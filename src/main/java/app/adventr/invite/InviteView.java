package app.adventr.invite;

import java.time.ZonedDateTime;

/**
 * The group's current invite link as shown on the settings page.
 */
public record InviteView(String token, ZonedDateTime expiresAt, boolean expired) {
}
