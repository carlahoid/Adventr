package app.adventr.adventure;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import app.adventr.user.CurrentUser;

/**
 * Posting, editing, and deleting comments. htmx requests get the re-rendered thread or comment;
 * plain form posts redirect back to the detail page. Authorization happens in
 * {@link CommentService}.
 */
@Controller
@RequestMapping("/groups/{groupId}/adventures/{adventureId}/comments")
class CommentController {

	private static final String THREAD = "adventures/comments :: thread";

	private static final String COMMENT = "adventures/comments :: comment(c=${comment}, editing=${editing}, editText=${editText}, editError=${editError})";

	private final CommentService comments;

	CommentController(CommentService comments) {
		this.comments = comments;
	}

	@PostMapping
	String post(@PathVariable long groupId, @PathVariable long adventureId, @RequestParam(defaultValue = "") String text,
			@RequestHeader(name = "HX-Request", required = false) String htmx, CurrentUser currentUser, Model model,
			RedirectAttributes redirect) {
		String error = null;
		try {
			this.comments.post(currentUser.id(), groupId, adventureId, text);
		}
		catch (CommentRejectedException ex) {
			error = ex.getMessage();
		}
		if (htmx == null) {
			if (error != null) {
				redirect.addFlashAttribute("commentError", error);
				redirect.addFlashAttribute("commentText", text);
			}
			return "redirect:/groups/" + groupId + "/adventures/" + adventureId + "#comments";
		}
		model.addAttribute("thread", this.comments.thread(currentUser.id(), groupId, adventureId, null));
		if (error != null) {
			model.addAttribute("commentError", error);
			model.addAttribute("commentText", text);
		}
		return THREAD;
	}

	@GetMapping("/{commentId}")
	String show(@PathVariable long groupId, @PathVariable long adventureId, @PathVariable long commentId,
			CurrentUser currentUser, Model model) {
		return renderComment(this.comments.comment(currentUser.id(), groupId, adventureId, commentId), false, null,
				null, model);
	}

	@GetMapping("/{commentId}/edit")
	String editForm(@PathVariable long groupId, @PathVariable long adventureId, @PathVariable long commentId,
			CurrentUser currentUser, Model model) {
		return renderComment(this.comments.ownComment(currentUser.id(), groupId, adventureId, commentId), true, null,
				null, model);
	}

	@PostMapping("/{commentId}/edit")
	String edit(@PathVariable long groupId, @PathVariable long adventureId, @PathVariable long commentId,
			@RequestParam(defaultValue = "") String text,
			@RequestHeader(name = "HX-Request", required = false) String htmx, CurrentUser currentUser, Model model,
			RedirectAttributes redirect) {
		try {
			this.comments.edit(currentUser.id(), groupId, adventureId, commentId, text);
		}
		catch (CommentRejectedException ex) {
			if (htmx == null) {
				redirect.addFlashAttribute("error", ex.getMessage());
				return "redirect:/groups/" + groupId + "/adventures/" + adventureId + "?editComment=" + commentId
						+ "#comment-" + commentId;
			}
			return renderComment(this.comments.ownComment(currentUser.id(), groupId, adventureId, commentId), true,
					text, ex.getMessage(), model);
		}
		if (htmx == null) {
			return "redirect:/groups/" + groupId + "/adventures/" + adventureId + "#comment-" + commentId;
		}
		return renderComment(this.comments.comment(currentUser.id(), groupId, adventureId, commentId), false, null,
				null, model);
	}

	@PostMapping("/{commentId}/delete")
	String delete(@PathVariable long groupId, @PathVariable long adventureId, @PathVariable long commentId,
			@RequestHeader(name = "HX-Request", required = false) String htmx, CurrentUser currentUser, Model model) {
		this.comments.delete(currentUser.id(), groupId, adventureId, commentId);
		if (htmx == null) {
			return "redirect:/groups/" + groupId + "/adventures/" + adventureId + "#comments";
		}
		model.addAttribute("thread", this.comments.thread(currentUser.id(), groupId, adventureId, null));
		// The deleted comment's button is gone; keyboard focus continues at the thread heading.
		model.addAttribute("focusComments", true);
		return THREAD;
	}

	private static String renderComment(CommentView comment, boolean editing, String editText, String editError,
			Model model) {
		model.addAttribute("comment", comment);
		model.addAttribute("editing", editing);
		model.addAttribute("editText", editText);
		model.addAttribute("editError", editError);
		// Back from the edit form (saved or cancelled): focus returns to the comment's "Edit" link.
		model.addAttribute("focusEdit", !editing);
		return COMMENT;
	}

}
