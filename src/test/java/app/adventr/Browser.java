package app.adventr;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.web.util.HtmlUtils;

/**
 * A minimal scripted browser for end-to-end login tests against the real Keycloak pages: it
 * follows redirects itself and keeps cookies per host. Unlike {@link java.net.CookieManager},
 * it also sends {@code Secure} cookies over http://localhost, as real browsers do.
 */
public class Browser {

	private static final Pattern SET_COOKIE = Pattern.compile("^([^=]+)=([^;]*)");

	private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

	private final Map<String, Map<String, String>> cookiesByHost = new HashMap<>();

	public Page get(String url) {
		return follow(HttpRequest.newBuilder(URI.create(url)).GET());
	}

	public Page post(String url, Map<String, String> form) {
		String body = form.entrySet()
			.stream()
			.map((e) -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
					+ URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
			.collect(Collectors.joining("&"));
		return follow(HttpRequest.newBuilder(URI.create(url))
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(body)));
	}

	private Page follow(HttpRequest.Builder request) {
		List<String> visited = new ArrayList<>();
		HttpResponse<String> response = send(request);
		visited.add(response.uri().toString());
		for (int i = 0; i < 20 && response.statusCode() / 100 == 3; i++) {
			URI next = response.uri().resolve(response.headers().firstValue("Location").orElseThrow());
			response = send(HttpRequest.newBuilder(next).GET());
			visited.add(next.toString());
		}
		return new Page(response.uri().toString(), response.statusCode(), response.body(), visited);
	}

	private HttpResponse<String> send(HttpRequest.Builder builder) {
		URI uri = builder.build().uri();
		Map<String, String> cookies = this.cookiesByHost.computeIfAbsent(uri.getAuthority(), (k) -> new LinkedHashMap<>());
		if (!cookies.isEmpty()) {
			builder.header("Cookie", cookies.entrySet()
				.stream()
				.map((e) -> e.getKey() + "=" + e.getValue())
				.collect(Collectors.joining("; ")));
		}
		try {
			HttpResponse<String> response = this.client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
			for (String header : response.headers().allValues("Set-Cookie")) {
				Matcher matcher = SET_COOKIE.matcher(header);
				if (matcher.find()) {
					boolean expired = header.contains("Max-Age=0") || matcher.group(2).isEmpty();
					if (expired) {
						cookies.remove(matcher.group(1).trim());
					}
					else {
						cookies.put(matcher.group(1).trim(), matcher.group(2));
					}
				}
			}
			return response;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

	/**
	 * The final page after following redirects, plus every URL visited on the way.
	 */
	public record Page(String url, int status, String body, List<String> visited) {

		/**
		 * The {@code action} of the form with the given id.
		 */
		public String formAction(String formId) {
			return attribute("<form[^>]*id=\"" + formId + "\"[^>]*action=\"([^\"]+)\"");
		}

		/**
		 * The {@code href} of the first link whose URL contains the given text.
		 */
		public String linkContaining(String text) {
			return URI.create(this.url).resolve(attribute("href=\"([^\"]*" + Pattern.quote(text) + "[^\"]*)\"")).toString();
		}

		/**
		 * The value of the named hidden input, e.g. {@code _csrf}.
		 */
		public String inputValue(String name) {
			return attribute("name=\"" + Pattern.quote(name) + "\" value=\"([^\"]*)\"");
		}

		private String attribute(String regex) {
			Matcher matcher = Pattern.compile(regex).matcher(this.body);
			if (!matcher.find()) {
				throw new AssertionError("No match for " + regex + " on " + this.url + ":\n" + this.body);
			}
			return HtmlUtils.htmlUnescape(matcher.group(1));
		}

	}

}
