package app.adventr.account;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import app.adventr.image.ImageRejectedException;
import app.adventr.user.CurrentUser;
import app.adventr.user.Theme;
import app.adventr.user.UserService;

/**
 * "My account". Every route acts on the logged-in user from the session; none takes a user id,
 * so another user's account cannot even be addressed.
 */
@Controller
@RequestMapping("/account")
class AccountController {

	private final AccountService accounts;

	private final ClientRegistrationRepository clientRegistrations;

	AccountController(AccountService accounts, ClientRegistrationRepository clientRegistrations) {
		this.accounts = accounts;
		this.clientRegistrations = clientRegistrations;
	}

	@GetMapping
	String account(CurrentUser currentUser, @AuthenticationPrincipal OidcUser oidcUser, Model model) {
		AccountView account = this.accounts.view(currentUser.id());
		model.addAttribute("displayName", account.displayName());
		model.addAttribute("bio", account.bio());
		return page(account, oidcUser, model);
	}

	@PostMapping("/profile")
	String updateProfile(@RequestParam(defaultValue = "") String displayName, @RequestParam(defaultValue = "") String bio,
			CurrentUser currentUser, @AuthenticationPrincipal OidcUser oidcUser, Model model,
			RedirectAttributes redirect) {
		try {
			this.accounts.updateProfile(currentUser.id(), displayName, bio);
			redirect.addFlashAttribute("notice", "Profile saved.");
			return "redirect:/account";
		}
		catch (AccountValidationException ex) {
			model.addAttribute("displayName", displayName);
			model.addAttribute("bio", bio);
			model.addAttribute("profileErrors", ex.fieldErrors());
			return page(this.accounts.view(currentUser.id()), oidcUser, model);
		}
	}

	@PostMapping("/profile/reset-name")
	String resetName(CurrentUser currentUser, @AuthenticationPrincipal OidcUser oidcUser, RedirectAttributes redirect) {
		this.accounts.resetDisplayName(currentUser.id(), oidcUser);
		redirect.addFlashAttribute("notice", "You're using your Keycloak name again.");
		return "redirect:/account";
	}

	@PostMapping("/avatar")
	String uploadAvatar(@RequestParam(name = "avatar", required = false) MultipartFile avatar, CurrentUser currentUser,
			RedirectAttributes redirect) {
		try {
			this.accounts.setAvatar(currentUser.id(), avatar);
			redirect.addFlashAttribute("notice", "Profile picture saved.");
		}
		catch (ImageRejectedException ex) {
			redirect.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:/account";
	}

	@PostMapping("/avatar/remove")
	String removeAvatar(CurrentUser currentUser, RedirectAttributes redirect) {
		this.accounts.removeAvatar(currentUser.id());
		redirect.addFlashAttribute("notice", "Profile picture removed.");
		return "redirect:/account";
	}

	/**
	 * Saves theme and color (a preset, or the custom color when "custom" is chosen), or resets
	 * the color. Takes effect on the next page, which the redirect loads.
	 */
	@PostMapping("/appearance")
	String updateAppearance(@RequestParam(defaultValue = "") String theme, @RequestParam(defaultValue = "") String color,
			@RequestParam(defaultValue = "") String customColor, @RequestParam(defaultValue = "") String action,
			CurrentUser currentUser, RedirectAttributes redirect) {
		if ("reset-color".equals(action)) {
			this.accounts.resetColor(currentUser.id());
			redirect.addFlashAttribute("notice", "Primary color reset to the default.");
			return "redirect:/account#appearance-heading";
		}
		try {
			this.accounts.updateAppearance(currentUser.id(), theme, pickedColor(color, customColor));
			redirect.addFlashAttribute("notice", "Appearance saved.");
		}
		catch (AccountValidationException ex) {
			redirect.addFlashAttribute("error", String.join(" ", ex.fieldErrors().values()));
		}
		return "redirect:/account#appearance-heading";
	}

	/**
	 * The preview for the color currently picked in the form; saves nothing.
	 */
	@GetMapping("/appearance/preview")
	String preview(@RequestParam(defaultValue = "") String color, @RequestParam(defaultValue = "") String customColor,
			Model model) {
		model.addAttribute("preview", AppearancePreview.of(pickedColor(color, customColor)));
		return "account/account :: preview";
	}

	private static String pickedColor(String color, String customColor) {
		return "custom".equals(color) ? customColor : color;
	}

	private String page(AccountView account, OidcUser oidcUser, Model model) {
		model.addAttribute("account", account);
		model.addAttribute("keycloakName", UserService.displayNameOf(oidcUser));
		model.addAttribute("keycloakAccountUrl", keycloakAccountUrl());
		model.addAttribute("maxDisplayName", AccountService.MAX_DISPLAY_NAME);
		model.addAttribute("maxBio", AccountService.MAX_BIO);
		model.addAttribute("maxAvatarMegabytes", AvatarStorage.MAX_BYTES / (1024 * 1024));
		model.addAttribute("themes", Theme.values());
		model.addAttribute("presets", AccentPreset.ALL);
		model.addAttribute("presetPicked", AccentPreset.ALL.stream()
			.anyMatch((preset) -> preset.hex().equals(account.effectiveAccentColor())));
		model.addAttribute("preview", AppearancePreview.of(account.effectiveAccentColor()));
		model.addAttribute("profileErrors", model.getAttribute("profileErrors") != null
				? model.getAttribute("profileErrors") : Map.of());
		return "account/account";
	}

	/**
	 * Keycloak's account console, where email, password, and linked Google login are managed:
	 * {@code {issuer}/account}.
	 */
	private String keycloakAccountUrl() {
		String issuer = this.clientRegistrations.findByRegistrationId("keycloak").getProviderDetails().getIssuerUri();
		return issuer.replaceAll("/+$", "") + "/account";
	}

}
