package app.adventr.adventure;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A plain-text comment in an adventure's flat thread.
 */
@Entity
@Table(name = "comments")
public class Comment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "adventure_id", nullable = false, updatable = false)
	private Long adventureId;

	@Column(name = "author_id", nullable = false, updatable = false)
	private Long authorId;

	@Column(nullable = false, length = 2000)
	private String text;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "edited_at")
	private Instant editedAt;

	protected Comment() {
	}

	Comment(long adventureId, long authorId, String text, Instant now) {
		this.adventureId = adventureId;
		this.authorId = authorId;
		this.text = text;
		this.createdAt = now;
	}

	void edit(String text, Instant now) {
		this.text = text;
		this.editedAt = now;
	}

	public Long getId() {
		return this.id;
	}

	public long getAdventureId() {
		return this.adventureId;
	}

	public long getAuthorId() {
		return this.authorId;
	}

	public String getText() {
		return this.text;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Instant getEditedAt() {
		return this.editedAt;
	}

}
