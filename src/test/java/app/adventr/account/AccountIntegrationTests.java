package app.adventr.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;

import app.adventr.IntegrationTest;
import app.adventr.TestUsers;
import app.adventr.TestUsers.TestUser;
import app.adventr.adventure.AdventureForm;
import app.adventr.adventure.AdventureService;
import app.adventr.group.MembershipRepository;
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
class AccountIntegrationTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ClientRegistrationRepository clientRegistrations;

	@Autowired
	private UserRepository users;

	@Autowired
	private MembershipRepository memberships;

	@Autowired
	private AdventureService adventures;

	private TestUsers testUsers;

	private TestUser kim;

	private TestUser ben;

	private long crew;

	@BeforeEach
	void setUp() throws Exception {
		this.testUsers = new TestUsers(this.mvc, this.clientRegistrations, this.users);
		this.kim = this.testUsers.create("Kim");
		this.ben = this.testUsers.create("Ben");
		this.crew = this.testUsers.createGroup(this.ben, "Mountain Crew");
		this.memberships.addOrReactivateMember(this.crew, this.kim.id());
	}

	@Test
	void pageShowsTheOwnSettingsAndTheKeycloakAccountLink() throws Exception {
		this.mvc.perform(get("/account").with(this.kim.login()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("My account")))
			.andExpect(content().string(containsString("value=\"Kim\"")))
			.andExpect(content().string(containsString("/realms/adventr/account\"")))
			.andExpect(content().string(containsString("Manage email, password and sign-in")))
			.andExpect(content().string(containsString("href=\"/account\"")));
	}

	@Test
	void unauthenticatedVisitorIsSentToLogin() throws Exception {
		this.mvc.perform(get("/account")).andExpect(redirectedUrl("/oauth2/authorization/keycloak"));
	}

	@Test
	void postWithoutCsrfIsForbidden() throws Exception {
		this.mvc.perform(post("/account/profile").param("displayName", "Hacked").with(this.kim.login()))
			.andExpect(status().isForbidden());

		assertThat(user(this.kim).getDisplayName()).isEqualTo("Kim");
	}

	@Test
	void newNameShowsInHeaderMemberListAndAuthorLabels() throws Exception {
		long trip = this.adventures.create(this.kim.id(), this.crew, AdventureForm.titleOnly("Canoe trip"));

		this.mvc.perform(post("/account/profile").param("displayName", "  Kim the Climber ")
			.param("bio", "Always up for a hike")
			.with(this.kim.login())
			.with(csrf()))
			.andExpect(redirectedUrl("/account"))
			.andExpect(flash().attribute("notice", "Profile saved."));

		User saved = user(this.kim);
		assertThat(saved.getDisplayName()).isEqualTo("Kim the Climber");
		assertThat(saved.isDisplayNameCustom()).isTrue();
		this.mvc.perform(get("/groups").with(this.kim.login()))
			.andExpect(content().string(containsString("<span>Kim the Climber</span></a>")));
		this.mvc.perform(get("/groups/" + this.crew + "/settings").with(this.ben.login()))
			.andExpect(content().string(containsString("Kim the Climber")))
			.andExpect(content().string(containsString("Always up for a hike")));
		this.mvc.perform(get("/groups/" + this.crew + "/adventures/" + trip).with(this.ben.login()))
			.andExpect(content().string(containsString("author-name\">Kim the Climber</span>")));
	}

	@Test
	void savingOnlyTheBioKeepsTheNameInSync() throws Exception {
		this.mvc.perform(post("/account/profile").param("displayName", "Kim")
			.param("bio", "Line one\nline two")
			.with(this.kim.login())
			.with(csrf())).andExpect(redirectedUrl("/account"));

		User saved = user(this.kim);
		assertThat(saved.isDisplayNameCustom()).isFalse();
		assertThat(saved.getBio()).isEqualTo("Line one line two");
	}

	@Test
	void invalidNamesAndBiosAreRejected() throws Exception {
		for (String name : new String[] { "", "   ", "x".repeat(61), "Kim\nthe Climber" }) {
			this.mvc.perform(post("/account/profile").param("displayName", name)
				.with(this.kim.login())
				.with(csrf()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("id=\"displayName-error\"")));
		}
		this.mvc.perform(post("/account/profile").param("displayName", "Kim")
			.param("bio", "b".repeat(161))
			.with(this.kim.login())
			.with(csrf()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("The bio can be at most 160 characters long.")));

		User unchanged = user(this.kim);
		assertThat(unchanged.getDisplayName()).isEqualTo("Kim");
		assertThat(unchanged.getBio()).isNull();
		assertThat(this.mvc.perform(post("/account/profile").param("displayName", "x".repeat(60))
			.with(this.kim.login())
			.with(csrf())).andReturn().getResponse().getRedirectedUrl()).isEqualTo("/account");
	}

	@Test
	void aTamperedUserIdIsIgnored() throws Exception {
		this.mvc.perform(post("/account/profile").param("displayName", "Taken over")
			.param("userId", String.valueOf(this.ben.id()))
			.param("id", String.valueOf(this.ben.id()))
			.with(this.kim.login())
			.with(csrf())).andExpect(redirectedUrl("/account"));

		assertThat(user(this.ben).getDisplayName()).isEqualTo("Ben");
		assertThat(user(this.kim).getDisplayName()).isEqualTo("Taken over");
	}

	@Test
	void bioMarkupIsShownAsText() throws Exception {
		this.mvc.perform(post("/account/profile").param("displayName", "Kim")
			.param("bio", "<script>alert(1)</script>")
			.with(this.kim.login())
			.with(csrf()));

		this.mvc.perform(get("/groups/" + this.crew + "/settings").with(this.ben.login()))
			.andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
			.andExpect(content().string(not(containsString("<script>alert(1)"))));
	}

	@Test
	void resetGoesBackToTheKeycloakName() throws Exception {
		this.mvc.perform(post("/account/profile").param("displayName", "Kim the Climber")
			.with(this.kim.login())
			.with(csrf()));
		this.mvc.perform(get("/account").with(this.kim.login()))
			.andExpect(content().string(containsString("Use my Keycloak name")))
			.andExpect(content().string(containsString("(Kim)")));

		this.mvc.perform(post("/account/profile/reset-name").with(this.kim.login()).with(csrf()))
			.andExpect(redirectedUrl("/account"));

		User reset = user(this.kim);
		assertThat(reset.getDisplayName()).isEqualTo("Kim");
		assertThat(reset.isDisplayNameCustom()).isFalse();
		this.mvc.perform(get("/account").with(this.kim.login()))
			.andExpect(content().string(not(containsString("Use my Keycloak name"))));
	}

	private User user(TestUser testUser) {
		return this.users.findById(testUser.id()).orElseThrow();
	}

}
