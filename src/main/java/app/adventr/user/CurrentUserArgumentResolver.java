package app.adventr.user;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@link CurrentUser} controller parameters from the OIDC principal.
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

	private final UserService userService;

	public CurrentUserArgumentResolver(UserService userService) {
		this.userService = userService;
	}

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return CurrentUser.class.equals(parameter.getParameterType());
	}

	@Override
	public CurrentUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
			throw new IllegalStateException("CurrentUser requested on an unauthenticated request");
		}
		return this.userService.current(oidcUser);
	}

}
