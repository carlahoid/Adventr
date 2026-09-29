package app.adventr.adventure;

/**
 * Where an adventure is in its life: an idea, planned, or done (a memory). Any active member
 * can move an adventure between them.
 */
public enum Status {

	IDEA("Idea"), PLANNED("Planned"), DONE("Done");

	private final String label;

	Status(String label) {
		this.label = label;
	}

	public String label() {
		return this.label;
	}

}
