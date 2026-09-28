package app.adventr.group;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A friend group. The entity name is {@code FriendGroup} because {@code Group} is a reserved
 * word in JPQL.
 */
@Entity(name = "FriendGroup")
@Table(name = "groups")
public class Group {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 80)
	private String name;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	protected Group() {
	}

	Group(String name) {
		this.name = name;
	}

	public Long getId() {
		return this.id;
	}

	public String getName() {
		return this.name;
	}

	void rename(String name) {
		this.name = name;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
