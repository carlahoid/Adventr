package app.adventr.adventure;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import app.adventr.group.Author;
import app.adventr.group.AuthorLabels;
import app.adventr.group.ForbiddenActionException;
import app.adventr.group.GroupAccessService;
import app.adventr.group.GroupScopedService;
import app.adventr.group.Membership;
import app.adventr.image.ImageRejectedException;

/**
 * Adventures of a group: list, detail, create, edit, status, image, delete. Any member can add
 * adventures and change their status; only the creator edits, and the creator or the Owner
 * deletes.
 */
@Service
@GroupScopedService
public class AdventureService {

	private final AdventureRepository adventures;

	private final AdventureQueries queries;

	private final AdventureImages images;

	private final GroupAccessService access;

	private final AuthorLabels authorLabels;

	private final Clock clock;

	public AdventureService(AdventureRepository adventures, AdventureQueries queries, AdventureImages images,
			GroupAccessService access, AuthorLabels authorLabels, Clock clock) {
		this.adventures = adventures;
		this.queries = queries;
		this.images = images;
		this.access = access;
		this.authorLabels = authorLabels;
		this.clock = clock;
	}

	/**
	 * The group's adventures in their sections, with counts and the user's own reactions.
	 */
	@Transactional(readOnly = true)
	public AdventureList list(long userId, long groupId) {
		this.access.requireMember(userId, groupId);
		List<AdventureQueries.ListRow> rows = this.queries.list(groupId, userId);
		Set<Long> reactorIds = new HashSet<>();
		rows.forEach((row) -> {
			reactorIds.addAll(row.upIds());
			reactorIds.addAll(row.downIds());
		});
		Map<Long, Author> authors = this.authorLabels.authorsIn(groupId, reactorIds);
		List<AdventureItem> items = rows.stream()
			.map((row) -> new AdventureItem(row.id(), row.title(), row.status(), row.dateFrom(), row.dateTo(),
					AdventureImages.version(row.imagePath()), row.commentCount(),
					new ReactionBar(groupId, row.id(), row.myReaction(), authorsOf(row.upIds(), authors),
							authorsOf(row.downIds(), authors))))
			.toList();
		return new AdventureList(withStatus(items, Status.PLANNED), withStatus(items, Status.IDEA),
				withStatus(items, Status.DONE));
	}

	@Transactional(readOnly = true)
	public AdventureDetail detail(long userId, long groupId, long adventureId) {
		Membership membership = this.access.requireMember(userId, groupId);
		Adventure adventure = adventure(groupId, adventureId);
		boolean creator = adventure.getCreatedBy() == userId;
		return new AdventureDetail(adventure.getId(), groupId, adventure.getTitle(), adventure.getDescription(),
				adventure.getLocation(), adventure.getDateFrom(), adventure.getDateTo(), adventure.getTimeHint(),
				adventure.getCostAmount(), adventure.getCostNote(), adventure.getLink(),
				AdventureImages.version(adventure.getImagePath()), adventure.getStatus(),
				this.authorLabels.authorIn(groupId, adventure.getCreatedBy()),
				adventure.getCreatedAt().atZone(this.clock.getZone()),
				adventure.getUpdatedAt().atZone(this.clock.getZone()), creator, creator || membership.isOwner());
	}

	/**
	 * Adds an adventure with status Idea and returns its id.
	 * @throws AdventureValidationException if a field is invalid
	 */
	@Transactional
	public long create(long userId, long groupId, AdventureForm form) {
		this.access.requireMember(userId, groupId);
		AdventureFields fields = AdventureFields.validate(form);
		return this.adventures.save(new Adventure(groupId, userId, fields, this.clock.instant())).getId();
	}

	/**
	 * The current values for the edit form; only the creator may edit.
	 */
	@Transactional(readOnly = true)
	public AdventureForm editForm(long userId, long groupId, long adventureId) {
		this.access.requireMember(userId, groupId);
		return AdventureForm.of(ownAdventure(userId, groupId, adventureId));
	}

	/**
	 * Replaces all editable fields; only the creator may edit.
	 * @throws AdventureValidationException if a field is invalid
	 */
	@Transactional
	public void update(long userId, long groupId, long adventureId, AdventureForm form) {
		this.access.requireMember(userId, groupId);
		Adventure adventure = ownAdventure(userId, groupId, adventureId);
		adventure.apply(AdventureFields.validate(form), this.clock.instant());
	}

	/**
	 * Any active member may move any adventure between Idea, Planned, and Done.
	 */
	@Transactional
	public void changeStatus(long userId, long groupId, long adventureId, Status status) {
		this.access.requireMember(userId, groupId);
		adventure(groupId, adventureId).changeStatus(status, this.clock.instant());
	}

	/**
	 * Attaches or replaces the image; only the creator may. The previous file is deleted after
	 * commit.
	 * @throws ImageRejectedException if the upload is not an acceptable image
	 */
	@Transactional
	public void setImage(long userId, long groupId, long adventureId, MultipartFile upload) {
		this.access.requireMember(userId, groupId);
		Adventure adventure = ownAdventure(userId, groupId, adventureId);
		String previous = adventure.getImagePath();
		adventure.changeImage(this.images.store(groupId, upload), this.clock.instant());
		this.images.deleteAfterCommit(previous);
	}

	@Transactional
	public void removeImage(long userId, long groupId, long adventureId) {
		this.access.requireMember(userId, groupId);
		Adventure adventure = ownAdventure(userId, groupId, adventureId);
		String previous = adventure.getImagePath();
		if (previous != null) {
			adventure.changeImage(null, this.clock.instant());
			this.images.deleteAfterCommit(previous);
		}
	}

	/**
	 * The image file (or its list thumbnail) for a member of the group.
	 * @throws AdventureNotFoundException if the adventure has no image
	 */
	@Transactional(readOnly = true)
	public Resource image(long userId, long groupId, long adventureId, boolean thumbnail) {
		this.access.requireMember(userId, groupId);
		String path = adventure(groupId, adventureId).getImagePath();
		Resource file = (path == null) ? null : (thumbnail ? this.images.thumbnail(path) : this.images.image(path));
		if (file == null) {
			throw new AdventureNotFoundException("No image for adventure " + adventureId);
		}
		return file;
	}

	/**
	 * Deletes the adventure with its reactions and comments (database cascade) and its image
	 * (after commit). The creator or the group Owner may delete.
	 */
	@Transactional
	public void delete(long userId, long groupId, long adventureId) {
		Membership membership = this.access.requireMember(userId, groupId);
		Adventure adventure = adventure(groupId, adventureId);
		if (adventure.getCreatedBy() != userId && !membership.isOwner()) {
			throw new ForbiddenActionException("Only the creator or the group owner can delete this adventure");
		}
		this.adventures.delete(adventure);
		this.images.deleteAfterCommit(adventure.getImagePath());
	}

	private Adventure adventure(long groupId, long adventureId) {
		return this.adventures.findByIdAndGroupId(adventureId, groupId)
			.orElseThrow(() -> new AdventureNotFoundException("No adventure " + adventureId + " in group " + groupId));
	}

	private Adventure ownAdventure(long userId, long groupId, long adventureId) {
		Adventure adventure = adventure(groupId, adventureId);
		if (adventure.getCreatedBy() != userId) {
			throw new ForbiddenActionException("Only the creator can edit this adventure");
		}
		return adventure;
	}

	private static List<Author> authorsOf(List<Long> ids, Map<Long, Author> authors) {
		return ids.stream().map(authors::get).toList();
	}

	private static List<AdventureItem> withStatus(List<AdventureItem> items, Status status) {
		return items.stream().filter((item) -> item.status() == status).toList();
	}

}
