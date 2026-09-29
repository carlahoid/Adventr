package app.adventr.adventure;

import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import app.adventr.group.GroupService;
import app.adventr.image.ImageRejectedException;
import app.adventr.user.CurrentUser;

/**
 * The group page (adventure list with quick add), the detail page, and the edit form.
 * Authorization happens in {@link AdventureService}.
 */
@Controller
@RequestMapping("/groups/{groupId}")
class AdventureController {

	/** Image URLs carry the image version, so a cached image never goes stale. */
	private static final CacheControl IMAGE_CACHE = CacheControl.maxAge(365, TimeUnit.DAYS).cachePrivate().immutable();

	private final AdventureService adventures;

	private final ReactionService reactions;

	private final CommentService comments;

	private final GroupService groups;

	AdventureController(AdventureService adventures, ReactionService reactions, CommentService comments,
			GroupService groups) {
		this.adventures = adventures;
		this.reactions = reactions;
		this.comments = comments;
		this.groups = groups;
	}

	@GetMapping
	String list(@PathVariable long groupId, CurrentUser currentUser, Model model) {
		model.addAttribute("group", this.groups.view(currentUser.id(), groupId));
		model.addAttribute("adventures", this.adventures.list(currentUser.id(), groupId));
		return "adventures/list";
	}

	/**
	 * Quick add (title only). With htmx, the new idea appears in the list in place, and a fresh
	 * form replaces the old one (out of band); a problem is shown in the form instead. Without
	 * JavaScript, it is a normal post and redirect.
	 */
	@PostMapping("/adventures")
	String quickAdd(@PathVariable long groupId, @RequestParam(defaultValue = "") String title,
			@RequestHeader(name = "HX-Request", required = false) String htmx, CurrentUser currentUser, Model model,
			HttpServletResponse response, RedirectAttributes redirect) {
		try {
			this.adventures.create(currentUser.id(), groupId, AdventureForm.titleOnly(title));
		}
		catch (AdventureValidationException ex) {
			String error = ex.fieldErrors().get("title");
			if (htmx == null) {
				redirect.addFlashAttribute("error", error);
				return "redirect:/groups/" + groupId;
			}
			response.setHeader("HX-Retarget", "#quick-add");
			response.setHeader("HX-Reswap", "outerHTML");
			model.addAttribute("groupId", groupId);
			model.addAttribute("quickAddTitle", title);
			model.addAttribute("quickAddError", error);
			return "adventures/list :: quickAdd";
		}
		if (htmx == null) {
			return "redirect:/groups/" + groupId;
		}
		model.addAttribute("groupId", groupId);
		model.addAttribute("adventures", this.adventures.list(currentUser.id(), groupId));
		model.addAttribute("quickAddOutOfBand", true);
		return "adventures/list :: quickAddResult";
	}

	@GetMapping("/adventures/new")
	String newForm(@PathVariable long groupId, CurrentUser currentUser, Model model) {
		model.addAttribute("group", this.groups.view(currentUser.id(), groupId));
		model.addAttribute("form", AdventureForm.titleOnly(""));
		return "adventures/form";
	}

	@PostMapping("/adventures/new")
	String create(@PathVariable long groupId, AdventureForm form, CurrentUser currentUser, Model model) {
		try {
			long adventureId = this.adventures.create(currentUser.id(), groupId, form);
			return "redirect:/groups/" + groupId + "/adventures/" + adventureId;
		}
		catch (AdventureValidationException ex) {
			model.addAttribute("group", this.groups.view(currentUser.id(), groupId));
			model.addAttribute("form", form);
			model.addAttribute("errors", ex.fieldErrors());
			return "adventures/form";
		}
	}

	/**
	 * The detail page. {@code editComment} opens one of the user's comments as a form, for
	 * editing without JavaScript.
	 */
	@GetMapping("/adventures/{adventureId}")
	String detail(@PathVariable long groupId, @PathVariable long adventureId,
			@RequestParam(required = false) Long editComment, CurrentUser currentUser, Model model) {
		model.addAttribute("group", this.groups.view(currentUser.id(), groupId));
		model.addAttribute("adventure", this.adventures.detail(currentUser.id(), groupId, adventureId));
		model.addAttribute("reactions", this.reactions.bar(currentUser.id(), groupId, adventureId));
		model.addAttribute("thread", this.comments.thread(currentUser.id(), groupId, adventureId, editComment));
		return "adventures/detail";
	}

	@GetMapping("/adventures/{adventureId}/edit")
	String editForm(@PathVariable long groupId, @PathVariable long adventureId, CurrentUser currentUser, Model model) {
		model.addAttribute("form", this.adventures.editForm(currentUser.id(), groupId, adventureId));
		return renderEdit(groupId, adventureId, currentUser, model);
	}

	@PostMapping("/adventures/{adventureId}/edit")
	String update(@PathVariable long groupId, @PathVariable long adventureId, AdventureForm form,
			CurrentUser currentUser, Model model, RedirectAttributes redirect) {
		try {
			this.adventures.update(currentUser.id(), groupId, adventureId, form);
			redirect.addFlashAttribute("notice", "Adventure saved.");
			return "redirect:/groups/" + groupId + "/adventures/" + adventureId;
		}
		catch (AdventureValidationException ex) {
			model.addAttribute("form", form);
			model.addAttribute("errors", ex.fieldErrors());
			return renderEdit(groupId, adventureId, currentUser, model);
		}
	}

	@PostMapping("/adventures/{adventureId}/status")
	String changeStatus(@PathVariable long groupId, @PathVariable long adventureId, @RequestParam Status status,
			CurrentUser currentUser, RedirectAttributes redirect) {
		this.adventures.changeStatus(currentUser.id(), groupId, adventureId, status);
		redirect.addFlashAttribute("notice", "Status changed to " + status.label() + ".");
		return "redirect:/groups/" + groupId + "/adventures/" + adventureId;
	}

	@PostMapping("/adventures/{adventureId}/delete")
	String delete(@PathVariable long groupId, @PathVariable long adventureId, CurrentUser currentUser,
			RedirectAttributes redirect) {
		this.adventures.delete(currentUser.id(), groupId, adventureId);
		redirect.addFlashAttribute("notice", "The adventure was deleted.");
		return "redirect:/groups/" + groupId;
	}

	@PostMapping("/adventures/{adventureId}/image")
	String uploadImage(@PathVariable long groupId, @PathVariable long adventureId,
			@RequestParam(name = "image", required = false) MultipartFile image, CurrentUser currentUser,
			RedirectAttributes redirect) {
		try {
			this.adventures.setImage(currentUser.id(), groupId, adventureId, image);
			redirect.addFlashAttribute("notice", "Image saved.");
		}
		catch (ImageRejectedException ex) {
			redirect.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:/groups/" + groupId + "/adventures/" + adventureId + "/edit";
	}

	@PostMapping("/adventures/{adventureId}/image/remove")
	String removeImage(@PathVariable long groupId, @PathVariable long adventureId, CurrentUser currentUser,
			RedirectAttributes redirect) {
		this.adventures.removeImage(currentUser.id(), groupId, adventureId);
		redirect.addFlashAttribute("notice", "Image removed.");
		return "redirect:/groups/" + groupId + "/adventures/" + adventureId + "/edit";
	}

	/**
	 * The image, only through the membership guard; never a static path.
	 */
	@GetMapping("/adventures/{adventureId}/image")
	ResponseEntity<Resource> image(@PathVariable long groupId, @PathVariable long adventureId,
			CurrentUser currentUser) {
		return jpeg(this.adventures.image(currentUser.id(), groupId, adventureId, false));
	}

	@GetMapping("/adventures/{adventureId}/thumbnail")
	ResponseEntity<Resource> thumbnail(@PathVariable long groupId, @PathVariable long adventureId,
			CurrentUser currentUser) {
		return jpeg(this.adventures.image(currentUser.id(), groupId, adventureId, true));
	}

	private String renderEdit(long groupId, long adventureId, CurrentUser currentUser, Model model) {
		model.addAttribute("group", this.groups.view(currentUser.id(), groupId));
		model.addAttribute("adventure", this.adventures.detail(currentUser.id(), groupId, adventureId));
		return "adventures/form";
	}

	private static ResponseEntity<Resource> jpeg(Resource file) {
		return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(IMAGE_CACHE).body(file);
	}

}
