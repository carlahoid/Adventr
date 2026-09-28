package app.adventr.invite;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import app.adventr.user.CurrentUser;

/**
 * Generating invite links and the {@code /join/{token}} flow. Unauthenticated visitors are sent
 * through Keycloak login or registration first and return here via the saved request.
 */
@Controller
class InviteController {

	private final InviteService invites;

	InviteController(InviteService invites) {
		this.invites = invites;
	}

	@PostMapping("/groups/{groupId}/invite")
	String generate(@PathVariable long groupId, CurrentUser currentUser, RedirectAttributes redirect) {
		this.invites.generate(currentUser.id(), groupId);
		redirect.addFlashAttribute("notice", "New invite link created. Any older link no longer works.");
		return "redirect:/groups/" + groupId + "/settings";
	}

	/**
	 * The join landing page: group name and a "Join group" button. Joining needs the POST, so a
	 * link preview bot fetching the URL cannot add anyone.
	 */
	@GetMapping("/join/{token}")
	String preview(@PathVariable String token, CurrentUser currentUser, Model model, HttpServletResponse response) {
		JoinPreview preview = this.invites.preview(currentUser.id(), token);
		if (preview.status() == JoinStatus.ALREADY_MEMBER) {
			return "redirect:/groups/" + preview.groupId();
		}
		return render(preview, token, model, response);
	}

	@PostMapping("/join/{token}")
	String join(@PathVariable String token, CurrentUser currentUser, Model model, HttpServletResponse response,
			RedirectAttributes redirect) {
		JoinPreview preview = this.invites.join(currentUser.id(), token);
		if (preview.status().canJoin()) {
			redirect.addFlashAttribute("notice", "Welcome to " + preview.groupName() + "!");
		}
		if (preview.status().canJoin() || preview.status() == JoinStatus.ALREADY_MEMBER) {
			return "redirect:/groups/" + preview.groupId();
		}
		return render(preview, token, model, response);
	}

	private String render(JoinPreview preview, String token, Model model, HttpServletResponse response) {
		switch (preview.status()) {
			case INVALID -> response.setStatus(HttpStatus.NOT_FOUND.value());
			case EXPIRED -> response.setStatus(HttpStatus.GONE.value());
			default -> {
			}
		}
		model.addAttribute("preview", preview);
		model.addAttribute("token", token);
		return "invites/join";
	}

}
