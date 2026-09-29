package app.adventr.adventure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Something a group wants to do together. Only the title is required. It is always loaded
 * together with its group id ({@link AdventureRepository#findByIdAndGroupId}), so an id from
 * another group cannot be used.
 */
@Entity
@Table(name = "adventures")
public class Adventure {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "group_id", nullable = false, updatable = false)
	private Long groupId;

	@Column(name = "created_by", nullable = false, updatable = false)
	private Long createdBy;

	@Column(nullable = false, length = 120)
	private String title;

	private String description;

	private String location;

	@Column(name = "date_from")
	private LocalDate dateFrom;

	@Column(name = "date_to")
	private LocalDate dateTo;

	@Column(name = "time_hint")
	private String timeHint;

	@Column(name = "cost_amount")
	private BigDecimal costAmount;

	@Column(name = "cost_note")
	private String costNote;

	private String link;

	@Column(name = "image_path")
	private String imagePath;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Status status;

	@Column(name = "status_changed_at", nullable = false)
	private Instant statusChangedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Adventure() {
	}

	Adventure(long groupId, long createdBy, AdventureFields fields, Instant now) {
		this.groupId = groupId;
		this.createdBy = createdBy;
		this.status = Status.IDEA;
		this.statusChangedAt = now;
		this.createdAt = now;
		apply(fields, now);
	}

	void apply(AdventureFields fields, Instant now) {
		this.title = fields.title();
		this.description = fields.description();
		this.location = fields.location();
		this.dateFrom = fields.dateFrom();
		this.dateTo = fields.dateTo();
		this.timeHint = fields.timeHint();
		this.costAmount = fields.costAmount();
		this.costNote = fields.costNote();
		this.link = fields.link();
		this.updatedAt = now;
	}

	void changeStatus(Status status, Instant now) {
		if (this.status != status) {
			this.status = status;
			this.statusChangedAt = now;
		}
	}

	void changeImage(String imagePath, Instant now) {
		this.imagePath = imagePath;
		this.updatedAt = now;
	}

	public Long getId() {
		return this.id;
	}

	public long getGroupId() {
		return this.groupId;
	}

	public long getCreatedBy() {
		return this.createdBy;
	}

	public String getTitle() {
		return this.title;
	}

	public String getDescription() {
		return this.description;
	}

	public String getLocation() {
		return this.location;
	}

	public LocalDate getDateFrom() {
		return this.dateFrom;
	}

	public LocalDate getDateTo() {
		return this.dateTo;
	}

	public String getTimeHint() {
		return this.timeHint;
	}

	public BigDecimal getCostAmount() {
		return this.costAmount;
	}

	public String getCostNote() {
		return this.costNote;
	}

	public String getLink() {
		return this.link;
	}

	public String getImagePath() {
		return this.imagePath;
	}

	public Status getStatus() {
		return this.status;
	}

	public Instant getStatusChangedAt() {
		return this.statusChangedAt;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

}
