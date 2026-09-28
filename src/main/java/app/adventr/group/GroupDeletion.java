package app.adventr.group;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes a group with all of its content. Callers have already checked that deletion is
 * allowed (Owner confirmation, sole owner leaving, or owner succession).
 */
@Component
class GroupDeletion {

	private final GroupRepository groups;

	private final ApplicationEventPublisher events;

	GroupDeletion(GroupRepository groups, ApplicationEventPublisher events) {
		this.groups = groups;
		this.events = events;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	void delete(long groupId) {
		this.groups.deleteGroup(groupId);
		this.events.publishEvent(new GroupDeletedEvent(groupId));
	}

}
