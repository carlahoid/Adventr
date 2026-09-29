package app.adventr.web;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.HandlerMapping;

import app.adventr.account.Appearance;
import app.adventr.group.GroupService;
import app.adventr.group.GroupSummary;
import app.adventr.user.CurrentUser;
import app.adventr.user.UserService;

/**
 * Model attributes needed by the shared layout (header) on every page.
 */
@ControllerAdvice
public class LayoutModelAdvice {

	private final UserService userService;

	private final GroupService groupService;

	public LayoutModelAdvice(UserService userService, GroupService groupService) {
		this.userService = userService;
		this.groupService = groupService;
	}

	/**
	 * {@code appearance} (theme and colors for {@code <html>}; defaults on public pages),
	 * {@code currentUser} for the header (absent on public pages), plus the group switcher's
	 * {@code switcherGroups} (the user's active groups) and {@code currentGroup} (the one in
	 * the {@code /groups/{groupId}} URL, if the user is a member of it).
	 */
	@ModelAttribute
	void layout(Model model, HttpServletRequest request) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
			model.addAttribute("appearance", Appearance.DEFAULT);
			return;
		}
		CurrentUser currentUser = this.userService.current(oidcUser);
		model.addAttribute("appearance", Appearance.of(currentUser.theme(), currentUser.accentColor()));
		List<GroupSummary> groups = this.groupService.myGroups(currentUser.id());
		model.addAttribute("currentUser", currentUser);
		model.addAttribute("switcherGroups", groups);
		String groupId = currentGroupId(request);
		groups.stream()
			.filter((group) -> String.valueOf(group.id()).equals(groupId))
			.findFirst()
			.ifPresent((group) -> model.addAttribute("currentGroup", group));
	}

	private static String currentGroupId(HttpServletRequest request) {
		if (request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> variables
				&& variables.get("groupId") instanceof String groupId) {
			return groupId;
		}
		return null;
	}

}
