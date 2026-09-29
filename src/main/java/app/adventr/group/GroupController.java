package app.adventr.group;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import app.adventr.invite.InviteLinks;
import app.adventr.invite.InviteService;
import app.adventr.user.CurrentUser;

/**
 * "My groups" and the group settings actions (the group page itself is the adventure list,
 * {@code AdventureController}). Authorization happens in
 * {@link GroupService}; rule violations come back as {@link GroupRuleException} and are shown
 * as a message on the page the user came from.
 */
@Controller
@RequestMapping("/groups")
class GroupController {

	private final GroupService groups;

	private final InviteService invites;

	GroupController(GroupService groups, InviteService invites) {
		this.groups = groups;
		this.invites = invites;
	}

	@GetMapping
	String myGroups(CurrentUser currentUser, Model model) {
		model.addAttribute("groups", this.groups.myGroups(currentUser.id()));
		return "groups/my-groups";
	}

	@PostMapping
	String create(@RequestParam(defaultValue = "") String name, CurrentUser currentUser, Model model) {
		try {
			long groupId = this.groups.create(currentUser.id(), name);
			return "redirect:/groups/" + groupId;
		}
		catch (GroupRuleException ex) {
			model.addAttribute("nameError", ex.getMessage());
			model.addAttribute("name", name);
			return myGroups(currentUser, model);
		}
	}

	@GetMapping("/{groupId}/settings")
	String settings(@PathVariable long groupId, CurrentUser currentUser, Model model) {
		model.addAttribute("group", this.groups.view(currentUser.id(), groupId));
		model.addAttribute("members", this.groups.members(currentUser.id(), groupId));
		this.invites.current(currentUser.id(), groupId).ifPresent((invite) -> {
			model.addAttribute("invite", invite);
			model.addAttribute("inviteUrl", InviteLinks.url(invite.token()));
		});
		return "groups/settings";
	}

	@PostMapping("/{groupId}/rename")
	String rename(@PathVariable long groupId, @RequestParam(defaultValue = "") String name, CurrentUser currentUser,
			RedirectAttributes redirect) {
		return settingsAction(groupId, redirect, "Group renamed.",
				() -> this.groups.rename(currentUser.id(), groupId, name));
	}

	@PostMapping("/{groupId}/members/{memberUserId}/remove")
	String removeMember(@PathVariable long groupId, @PathVariable long memberUserId, CurrentUser currentUser,
			RedirectAttributes redirect) {
		return settingsAction(groupId, redirect, "Member removed.",
				() -> this.groups.removeMember(currentUser.id(), groupId, memberUserId));
	}

	@PostMapping("/{groupId}/members/{memberUserId}/make-owner")
	String transferOwnership(@PathVariable long groupId, @PathVariable long memberUserId, CurrentUser currentUser,
			RedirectAttributes redirect) {
		return settingsAction(groupId, redirect, "Ownership transferred.",
				() -> this.groups.transferOwnership(currentUser.id(), groupId, memberUserId));
	}

	@PostMapping("/{groupId}/leave")
	String leave(@PathVariable long groupId, CurrentUser currentUser, RedirectAttributes redirect) {
		try {
			LeaveOutcome outcome = this.groups.leave(currentUser.id(), groupId);
			redirect.addFlashAttribute("notice",
					(outcome == LeaveOutcome.GROUP_DELETED) ? "You left, and the group was deleted." : "You left the group.");
			return "redirect:/groups";
		}
		catch (GroupRuleException ex) {
			redirect.addFlashAttribute("error", ex.getMessage());
			return "redirect:/groups/" + groupId + "/settings";
		}
	}

	@PostMapping("/{groupId}/delete")
	String delete(@PathVariable long groupId, @RequestParam(defaultValue = "") String confirmName,
			CurrentUser currentUser, RedirectAttributes redirect) {
		try {
			this.groups.delete(currentUser.id(), groupId, confirmName);
			redirect.addFlashAttribute("notice", "The group was deleted.");
			return "redirect:/groups";
		}
		catch (GroupRuleException ex) {
			redirect.addFlashAttribute("error", ex.getMessage());
			return "redirect:/groups/" + groupId + "/settings";
		}
	}

	private String settingsAction(long groupId, RedirectAttributes redirect, String notice, Runnable action) {
		try {
			action.run();
			redirect.addFlashAttribute("notice", notice);
		}
		catch (GroupRuleException ex) {
			redirect.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:/groups/" + groupId + "/settings";
	}

}
