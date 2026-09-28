package app.adventr.invite;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;

import app.adventr.Browser;
import app.adventr.Browser.Page;
import app.adventr.IntegrationTest;
import app.adventr.TestUsers;
import app.adventr.TestUsers.TestUser;
import app.adventr.group.Membership;
import app.adventr.group.MembershipRepository;
import app.adventr.group.Role;
import app.adventr.user.UserRepository;

import static app.adventr.TestcontainersConfiguration.APP_BASE_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class InviteIntegrationTests {

	private static final String PASSWORD = "correct-horse-battery";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ClientRegistrationRepository clientRegistrations;

	@Autowired
	private UserRepository users;

	@Autowired
	private MembershipRepository memberships;

	@Autowired
	private InviteRepository invites;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private KeycloakContainer keycloak;

	private TestUsers testUsers;

	@BeforeEach
	void setUp() {
		this.testUsers = new TestUsers(this.mvc, this.clientRegistrations, this.users);
	}

	// Generate and show

	@Test
	void ownerGeneratesUnguessableSevenDayLink() throws Exception {
		TestUser anna = this.testUsers.create("Anna");
		long crew = this.testUsers.createGroup(anna, "Mountain Crew");
		Instant before = Instant.now();

		String token = generate(anna, crew);

		assertThat(token).matches("[A-Za-z0-9_-]{43}");
		Invite invite = this.invites.findByGroupId(crew).orElseThrow();
		assertThat(invite.getExpiresAt()).isBetween(before.plus(InviteService.VALIDITY),
				Instant.now().plus(InviteService.VALIDITY));
		assertThat(invite.getCreatedBy()).isEqualTo(anna.id());
		this.mvc.perform(get("/groups/" + crew + "/settings").with(anna.login()))
			.andExpect(content().string(containsString("http://localhost/join/" + token)))
			.andExpect(content().string(containsString("Copy link")))
			.andExpect(content().string(containsString("Expires ")))
			.andExpect(content().string(containsString("Create new link")));
	}

	@Test
	void memberSeesAndCanCopyTheLinkButCannotGenerate() throws Exception {
		TestUser anna = this.testUsers.create("Anna");
		TestUser ben = this.testUsers.create("Ben");
		long crew = this.testUsers.createGroup(anna, "Mountain Crew");
		String token = generate(anna, crew);
		join(ben, token);

		this.mvc.perform(get("/groups/" + crew + "/settings").with(ben.login()))
			.andExpect(content().string(containsString("/join/" + token)))
			.andExpect(content().string(containsString("Copy link")))
			.andExpect(content().string(not(containsString("Create new link"))));
		this.mvc.perform(post("/groups/" + crew + "/invite").with(ben.login()).with(csrf()))
			.andExpect(status().isForbidden());
		assertThat(this.invites.findByGroupId(crew).orElseThrow().getToken()).isEqualTo(token);
	}

	@Test
	void nonMemberCannotGenerate() throws Exception {
		TestUser anna = this.testUsers.create("Anna");
		TestUser mallory = this.testUsers.create("Mallory");
		long crew = this.testUsers.createGroup(anna, "Mountain Crew");

		this.mvc.perform(post("/groups/" + crew + "/invite").with(mallory.login()).with(csrf()))
			.andExpect(status().isNotFound());
		assertThat(this.invites.findByGroupId(crew)).isEmpty();
	}

	// Join flow

	@Test
	void joinPageShowsGroupAndJoiningNeedsConfirmation() throws Exception {
		TestUser anna = this.testUsers.create("Anna");
		TestUser ben = this.testUsers.create("Ben");
		long crew = this.testUsers.createGroup(anna, "Mountain Crew");
		String token = generate(anna, crew);

		this.mvc.perform(get("/join/" + token).with(ben.login()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Mountain Crew")))
			.andExpect(content().string(containsString("Join group")));
		assertThat(this.memberships.findByGroupIdAndUserId(crew, ben.id())).as("GET changes nothing").isEmpty();

		this.mvc.perform(post("/join/" + token).with(ben.login()).with(csrf())).andExpect(redirectedUrl("/groups/" + crew));

		Membership membership = this.memberships.findByGroupIdAndUserId(crew, ben.id()).orElseThrow();
		assertThat(membership.getRole()).isEqualTo(Role.MEMBER);
		assertThat(membership.getLeftAt()).isNull();
		this.mvc.perform(get("/groups/" + crew).with(ben.login())).andExpect(status().isOk());
	}

	@Test
	void severalFriendsJoinWithTheSameLink() throws Exception {
		TestUser anna = this.testUsers.create("Anna");
		long crew = this.testUsers.createGroup(anna, "Mountain Crew");
		String token = generate(anna, crew);

		for (String name : new String[] { "Ben", "Cleo", "Dan" }) {
			TestUser friend = this.testUsers.create(name);
			join(friend, token);
			assertThat(this.memberships.findByGroupIdAndUserIdAndLeftAtIsNull(crew, friend.id())).isPresent();
		}
		assertThat(this.memberships.countByGroupIdAndLeftAtIsNull(crew)).isEqualTo(4);
	}

	@Test
	void activeMemberIsRedirectedWithoutDuplicate() throws Exception {
		TestUser anna = this.testUsers.create("Anna");
		TestUser ben = this.testUsers.create("Ben");
		long crew = this.testUsers.createGroup(anna, "Mountain Crew");
		String token = generate(anna, crew);
		join(ben, token);
		Instant joinedAt = this.memberships.findByGroupIdAndUserId(crew, ben.id()).orElseThrow().getJoinedAt();

		this.mvc.perform(get("/join/" + token).with(ben.login())).andExpect(redirectedUrl("/groups/" + crew));
		this.mvc.perform(get("/join/" + token).with(anna.login())).andExpect(redirectedUrl("/groups/" + crew));
		this.mvc.perform(post("/join/" + token).with(ben.login()).with(csrf())).andExpect(redirectedUrl("/groups/" + crew));

		assertThat(this.memberships.countByGroupIdAndLeftAtIsNull(crew)).isEqualTo(2);
		assertThat(this.memberships.findByGroupIdAndUserId(crew, ben.id()).orElseThrow().getJoinedAt()).isEqualTo(joinedAt);
		assertThat(this.memberships.findByGroupIdAndUserId(crew, anna.id()).orElseThrow().getRole()).isEqualTo(Role.OWNER);
	}

	@Test
	void formerMemberRejoinsWithNewJoinDate() throws Exception {
		TestUser anna = this.testUsers.create("Anna");
		TestUser ben = this.testUsers.create("Ben");
		long crew = this.testUsers.createGroup(anna, "Mountain Crew");
		String token = generate(anna, crew);
		join(ben, token);
		Membership first = this.memberships.findByGroupIdAndUserId(crew, ben.id()).orElseThrow();
		this.mvc.perform(post("/groups/" + crew + "/leave").with(ben.login()).with(csrf()));

		this.mvc.perform(get("/join/" + token).with(ben.login()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("You were a member before")));
		join(ben, token);

		Membership rejoined = this.memberships.findByGroupIdAndUserId(crew, ben.id()).orElseThrow();
		assertThat(rejoined.getId()).isEqualTo(first.getId());
		assertThat(rejoined.getLeftAt()).isNull();
		assertThat(rejoined.getRole()).isEqualTo(Role.MEMBER);
		assertThat(rejoined.getJoinedAt()).isAfter(first.getJoinedAt());
	}

	@Test
	void oldLinkIsInvalidAfterRegeneration() throws Exception {
		TestUser anna = this.testUsers.create("Anna");
		TestUser ben = this.testUsers.create("Ben");
		long crew = this.testUsers.createGroup(anna, "Mountain Crew");
		String oldToken = generate(anna, crew);

		String newToken = generate(anna, crew);

		assertThat(newToken).isNotEqualTo(oldToken);
		this.mvc.perform(get("/join/" + oldToken).with(ben.login()))
			.andExpect(status().isNotFound())
			.andExpect(content().string(containsString("This invite link is no longer valid")))
			.andExpect(content().string(not(containsString("Mountain Crew"))));
		this.mvc.perform(post("/join/" + oldToken).with(ben.login()).with(csrf())).andExpect(status().isNotFound());
		assertThat(this.memberships.findByGroupIdAndUserId(crew, ben.id())).isEmpty();
		join(ben, newToken);
		assertThat(this.memberships.findByGroupIdAndUserIdAndLeftAtIsNull(crew, ben.id())).isPresent();
	}

	@Test
	void expiredLinkDoesNotAddAnyone() throws Exception {
		TestUser anna = this.testUsers.create("Anna");
		TestUser ben = this.testUsers.create("Ben");
		long crew = this.testUsers.createGroup(anna, "Mountain Crew");
		String token = generate(anna, crew);
		this.jdbc.update("update invites set expires_at = now() - interval '1 minute' where group_id = ?", crew);

		this.mvc.perform(get("/join/" + token).with(ben.login()))
			.andExpect(status().isGone())
			.andExpect(content().string(containsString("This invite link has expired, ask the group for a new one")))
			.andExpect(content().string(not(containsString("Mountain Crew"))));
		this.mvc.perform(post("/join/" + token).with(ben.login()).with(csrf())).andExpect(status().isGone());

		assertThat(this.memberships.findByGroupIdAndUserId(crew, ben.id())).isEmpty();
		this.mvc.perform(get("/groups/" + crew + "/settings").with(anna.login()))
			.andExpect(content().string(containsString("The invite link has expired")))
			.andExpect(content().string(containsString("Create invite link")));
	}

	@Test
	void linkOfDeletedGroupIsInvalid() throws Exception {
		TestUser anna = this.testUsers.create("Anna");
		TestUser ben = this.testUsers.create("Ben");
		long crew = this.testUsers.createGroup(anna, "Mountain Crew");
		String token = generate(anna, crew);
		this.mvc.perform(post("/groups/" + crew + "/leave").with(anna.login()).with(csrf()));

		this.mvc.perform(get("/join/" + token).with(ben.login())).andExpect(status().isNotFound());
		assertThat(this.invites.findByToken(token)).isEmpty();
	}

	@Test
	void joinRequiresLogin() throws Exception {
		this.mvc.perform(get("/join/some-token"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", endsWith("/oauth2/authorization/keycloak")));
	}

	/**
	 * The full round trip through the real Keycloak pages: a visitor without an account opens
	 * the link, registers, returns to the join page, joins, and lands in the group.
	 */
	@Test
	void newUserRegistersThroughInviteLinkAndLandsInTheGroup() throws Exception {
		TestUser anna = this.testUsers.create("Anna");
		long crew = this.testUsers.createGroup(anna, "Mountain Crew");
		String token = generate(anna, crew);
		Browser browser = new Browser();
		String username = "invitee-" + UUID.randomUUID().toString().substring(0, 8);

		Page login = browser.get(APP_BASE_URL + "/join/" + token);
		assertThat(login.url()).startsWith(this.keycloak.getAuthServerUrl() + "/realms/adventr/");
		Page registration = browser.get(login.linkContaining("login-actions/registration"));
		Page joinPage = browser.post(registration.formAction("kc-register-form"),
				Map.of("username", username, "email", username + "@example.com", "firstName", "Bea", "lastName",
						"Newcomer", "password", PASSWORD, "password-confirm", PASSWORD));

		assertThat(joinPage.url()).startsWith(APP_BASE_URL + "/join/" + token);
		assertThat(joinPage.body()).contains("Mountain Crew").contains("Join group");
		Page group = browser.post(APP_BASE_URL + joinPage.formAction("join-form"),
				Map.of("_csrf", joinPage.inputValue("_csrf")));

		assertThat(group.url()).isEqualTo(APP_BASE_URL + "/groups/" + crew);
		assertThat(group.status()).isEqualTo(200);
		assertThat(group.body()).contains("Welcome to Mountain Crew!").contains("Bea Newcomer");
		String sub = this.keycloak.getKeycloakAdminClient()
			.realm("adventr")
			.users()
			.searchByUsername(username, true)
			.getFirst()
			.getId();
		long userId = this.users.findByKeycloakSub(sub).orElseThrow().getId();
		assertThat(this.memberships.findByGroupIdAndUserIdAndLeftAtIsNull(crew, userId).orElseThrow().getRole())
			.isEqualTo(Role.MEMBER);
	}

	private String generate(TestUser owner, long groupId) throws Exception {
		this.mvc.perform(post("/groups/" + groupId + "/invite").with(owner.login()).with(csrf()))
			.andExpect(redirectedUrl("/groups/" + groupId + "/settings"));
		return this.invites.findByGroupId(groupId).orElseThrow().getToken();
	}

	private void join(TestUser user, String token) throws Exception {
		this.mvc.perform(post("/join/" + token).with(user.login()).with(csrf())).andExpect(status().is3xxRedirection());
	}

}
