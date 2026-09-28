package app.adventr.group;

/**
 * One row of "My groups" and of the header switcher.
 */
public record GroupSummary(long id, String name, long memberCount) {
}
