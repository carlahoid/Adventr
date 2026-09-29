package app.adventr.account;

import java.util.concurrent.TimeUnit;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import app.adventr.user.CurrentUser;

/**
 * Serves avatars to the owner and their current co-members only; everyone else gets 404. There
 * is no public URL for any avatar.
 */
@Controller
class AvatarController {

	/** Avatar URLs carry the avatar version ({@code ?v=}), so a cached image never goes stale. */
	private static final CacheControl AVATAR_CACHE = CacheControl.maxAge(365, TimeUnit.DAYS).cachePrivate().immutable();

	private final AccountService accounts;

	AvatarController(AccountService accounts) {
		this.accounts = accounts;
	}

	@GetMapping("/users/{userId}/avatar")
	ResponseEntity<Resource> avatar(@PathVariable long userId, CurrentUser currentUser) {
		return ResponseEntity.ok()
			.contentType(MediaType.IMAGE_JPEG)
			.cacheControl(AVATAR_CACHE)
			.body(this.accounts.avatar(currentUser.id(), userId));
	}

}
