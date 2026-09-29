package app.adventr.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;

import app.adventr.IntegrationTest;
import app.adventr.TestUsers;
import app.adventr.TestUsers.TestUser;
import app.adventr.user.Theme;
import app.adventr.user.User;
import app.adventr.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AppearanceIntegrationTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ClientRegistrationRepository clientRegistrations;

	@Autowired
	private UserRepository users;

	private TestUser kim;

	@BeforeEach
	void setUp() throws Exception {
		this.kim = new TestUsers(this.mvc, this.clientRegistrations, this.users).create("Kim");
	}

	@Test
	void themeAndColorPersistAndRenderOnTheNextPage() throws Exception {
		this.mvc.perform(post("/account/appearance").param("theme", "DARK")
			.param("color", "custom")
			.param("customColor", "#C2185B")
			.with(this.kim.login())
			.with(csrf()))
			.andExpect(redirectedUrl("/account#appearance-heading"))
			.andExpect(flash().attribute("notice", "Appearance saved."));

		User saved = user();
		assertThat(saved.getTheme()).isEqualTo(Theme.DARK);
		assertThat(saved.getAccentColor()).isEqualTo("#c2185b");
		String style = AccentColors.derive("#c2185b").style();
		this.mvc.perform(get("/groups").with(this.kim.login()))
			.andExpect(content().string(containsString("data-theme=\"dark\"")))
			.andExpect(content().string(containsString("style=\"" + style + "\"")));
	}

	@Test
	void presetsAreStoredAsTheirHexAndTheDefaultAsNull() throws Exception {
		save("LIGHT", "#1d4ed8");
		assertThat(user().getAccentColor()).isEqualTo("#1d4ed8");
		this.mvc.perform(get("/groups").with(this.kim.login()))
			.andExpect(content().string(containsString("data-theme=\"light\"")))
			.andExpect(content().string(containsString("--accent-l:#1d4ed8;")));

		save("LIGHT", AccentColors.DEFAULT);
		assertThat(user().getAccentColor()).isNull();
	}

	@Test
	void systemThemeOmitsDataTheme() throws Exception {
		save("DARK", "#1d4ed8");
		save("SYSTEM", "#1d4ed8");

		this.mvc.perform(get("/groups").with(this.kim.login()))
			.andExpect(content().string(not(containsString("data-theme="))))
			.andExpect(content().string(containsString("--accent-l:#1d4ed8;")));
	}

	@Test
	void invalidColorsAndThemesAreRejectedAndTheOldValuesKept() throws Exception {
		save("DARK", "#1d4ed8");

		for (String color : new String[] { "red;background:url(x)", "#12345", "", "#1234567" }) {
			this.mvc.perform(post("/account/appearance").param("theme", "LIGHT")
				.param("color", color)
				.with(this.kim.login())
				.with(csrf())).andExpect(flash().attribute("error", containsString("Please choose a color")));
		}
		this.mvc.perform(post("/account/appearance").param("theme", "SEPIA")
			.param("color", "#1d4ed8")
			.with(this.kim.login())
			.with(csrf())).andExpect(flash().attribute("error", containsString("Light, Dark, or System")));

		User unchanged = user();
		assertThat(unchanged.getTheme()).isEqualTo(Theme.DARK);
		assertThat(unchanged.getAccentColor()).isEqualTo("#1d4ed8");
		this.mvc.perform(get("/groups").with(this.kim.login()))
			.andExpect(content().string(not(containsString("url(x)"))));
	}

	@Test
	void resetGoesBackToTheDefaultColorAndKeepsTheTheme() throws Exception {
		save("DARK", "#be185d");

		this.mvc.perform(post("/account/appearance").param("action", "reset-color")
			.with(this.kim.login())
			.with(csrf())).andExpect(flash().attribute("notice", "Primary color reset to the default."));

		assertThat(user().getAccentColor()).isNull();
		assertThat(user().getTheme()).isEqualTo(Theme.DARK);
	}

	@Test
	void previewShowsAdjustedColorsWithoutSaving() throws Exception {
		this.mvc.perform(get("/account/appearance/preview").param("color", "custom")
			.param("customColor", "#ffff00")
			.header("HX-Request", "true")
			.with(this.kim.login()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("id=\"appearance-preview\"")))
			.andExpect(content().string(containsString("Adjusted for readability")))
			.andExpect(content().string(containsString("--p-accent:" + AccentColors.derive("#ffff00").light().accent())))
			.andExpect(content().string(not(containsString("<html"))));

		assertThat(user().getAccentColor()).isNull();
	}

	@Test
	void previewOfInvalidInputDerivesNothing() throws Exception {
		this.mvc.perform(get("/account/appearance/preview").param("color", "red;background:url(x)")
			.with(this.kim.login()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("That isn't a color")))
			.andExpect(content().string(not(containsString("url(x)"))));
	}

	@Test
	void accountPageMarksTheSelectedPresetByMoreThanColor() throws Exception {
		save("SYSTEM", "#4f46e5");

		String page = this.mvc.perform(get("/account").with(this.kim.login()))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(page).containsPattern("value=\"#4f46e5\" checked=\"checked\">")
			.contains("<span>Indigo</span>")
			.contains("class=\"swatch-check\" aria-hidden=\"true\">✓</span>")
			.containsPattern("name=\"theme\" value=\"SYSTEM\" checked=\"checked\"");
	}

	@Test
	void logoutPagesUseTheDefaults() throws Exception {
		this.mvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString("data-theme="))))
			.andExpect(content().string(containsString("style=\"" + AccentColors.derive(null).style() + "\"")));
	}

	private void save(String theme, String color) throws Exception {
		this.mvc.perform(post("/account/appearance").param("theme", theme)
			.param("color", color)
			.with(this.kim.login())
			.with(csrf())).andExpect(flash().attribute("notice", "Appearance saved."));
	}

	private User user() {
		return this.users.findById(this.kim.id()).orElseThrow();
	}

}
