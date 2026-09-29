package app.adventr.adventure;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import app.adventr.user.CurrentUser;

/**
 * Reacting from the list or the detail page. htmx gets the re-rendered bar; a plain form post
 * redirects back to where the click came from.
 */
@Controller
class ReactionController {

	private final ReactionService reactions;

	ReactionController(ReactionService reactions) {
		this.reactions = reactions;
	}

	@PostMapping("/groups/{groupId}/adventures/{adventureId}/reactions")
	String toggle(@PathVariable long groupId, @PathVariable long adventureId, @RequestParam ReactionType type,
			@RequestParam(defaultValue = "detail") String view,
			@RequestHeader(name = "HX-Request", required = false) String htmx, CurrentUser currentUser, Model model) {
		ReactionBar bar = this.reactions.toggle(currentUser.id(), groupId, adventureId, type);
		boolean detail = !"list".equals(view);
		if (htmx == null) {
			return detail ? "redirect:/groups/" + groupId + "/adventures/" + adventureId
					: "redirect:/groups/" + groupId + "#reactions-" + adventureId;
		}
		model.addAttribute("bar", bar);
		model.addAttribute("detail", detail);
		return "adventures/reactions :: bar(bar=${bar}, detail=${detail})";
	}

}
