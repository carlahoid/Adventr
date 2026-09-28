package app.adventr.group;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

import app.adventr.IntegrationTest;
import app.adventr.TestUsers;
import app.adventr.TestUsers.TestUser;
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
@RecordApplicationEvents
class GroupIntegrationTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ClientRegistrationRepository clientRegistrations;

	@Autowired
	private UserRepository users;

	@Autowired
	private GroupRepository groups;

	@Autowired
	private MembershipRepository memberships;

	@Autowired
	private OwnerSuccessionService succession;

	@Autowired
	private AuthorLabels authorLabels;

	@Autowired
	private ApplicationEvents events;

	private TestUsers testUsers;

	@BeforeEach
	void setUp() {
		this.testUsers = new TestUsers(this.mvc, this.clientRegistrations, this.users);
	}

	// Create group and My groups

	@Test
	void creatorBecomesOwnerAndLandsOnTheGroup() throws Exception {
		TestUser anna = this.testUsers.create("Anna");

		this.mvc.perform(post("/groups").param("name", "  Mountain Crew  ").with(anna.login()).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect((result) -> assertThat(result.getResponse().getRedirectedUrl()).matches("/groups/\\d+"));

		GroupSummary group = this.groups.findSummariesForMember(anna.id()).getFirst();
		assertThat(group.name()).isEqualTo("Mountain Crew");
		assertThat(role(group.id(), anna)).isEqualTo(Role.OWNER);
		this.mvc.perform(get("/groups/" + group.id()).with(anna.login()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Mountain Crew")));
	}

	@Test
	void blankOrTooLongNameIsRejected() throws Exception {
		TestUser anna = this.testUsers.create("Anna");

		this.mvc.perform(post("/groups").param("name", "   ").with(anna.login()).with(csrf()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("must be 1 to 80 characters")));
		this.mvc.perform(post("/groups").param("name", "x".repeat(81)).with(anna.login()).with(csrf()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("must be 1 to 80 characters")));

		assertThat(this.groups.findSummariesForMember(anna.id())).isEmpty();
	}

	@Test
	void myGroupsListsActiveGroupsWithMemberCounts() throws Exception {
		TestUser anna = this.testUsers.create("Anna");
		TestUser ben = this.testUsers.create("Ben");
		long crew = this.testUsers.createGroup(anna, "Mountain Crew");
		this.testUsers.createGroup(anna, "Book Club");
		this.memberships.addOrReactivateMember(crew, ben.id());

		this.mvc.perform(get("/groups").with(anna.login()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Mountain Crew")))
			.andExpect(content().string(containsString("2 members")))
			.andExpect(content().string(containsString("Book Club")))
			.andExpect(content().string(containsString("1 member<")));
	}

	@Test
	void userWithoutGroupsSeesEmptyState() throws Exception {
		TestUser anna = this.testUsers.create("Anna");

		this.mvc.perform(get("/groups").with(anna.login()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("ask a friend for an invite link")))
			.andExpect(content().string(not(containsString("group-switcher"))));
	}

	@Test
	void headerSwitcherListsTheUsersGroupsAndMarksTheCurrentOne() throws Exception {
		TestUser anna = this.testUsers.create("Anna");
		long crew = this.testUsers.createGroup(anna, "Mountain Crew");
		long club = this.testUsers.createGroup(anna, "Book Club");

		this.mvc.perform(get("/groups/" + club + "/settings").with(anna.login()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("<summary>Book Club</summary>")))
			.andExpect(content().string(containsString("href=\"/groups/" + crew + "\"")))
			.andExpect(content().string(containsString("href=\"/groups/" + club + "\" aria-current=\"page\"")));
	}

	// Central authorization

	@Test
	void nonMemberGetsNotFound() throws Exception {
		TestUser anna = this.testUsers.create("Anna");
		TestUser mallory = this.testUsers.create("Mallory");
		long crew = this.testUsers.createGroup(anna, "Mountain Crew");

		this.mvc.perform(get("/groups/" + crew).with(mallory.login())).andExpect(status().isNotFound());
		this.mvc.perform(get("/groups/" + crew + "/settings").with(mallory.login()))
			.andExpect(status().isNotFound())
			.andExpect(content().string(not(containsString("Mountain Crew"))));
		this.mvc.perform(post("/groups/" + crew + "/rename").param("name", "Hacked").with(mallory.login()).with(csrf()))
			.andExpect(status().isNotFound());
		this.mvc.perform(post("/groups/" + crew + "/leave").with(mallory.login()).with(csrf()))
			.andExpect(status().isNotFound());
		assertThat(this.groups.findById(crew).orElseThrow().getName()).isEqualTo("Mountain Crew");
	}

	@Test
	void unknownGroupGetsNotFound() throws Exception {
		TestUser anna = this.testUsers.create("Anna");

		this.mvc.perform(get("/groups/999999999").with(anna.login())).andExpect(status().isNotFound());
	}

	@Test
	void formerMemberGetsNotFound() throws Exception {
		TestUser anna = this.testUsers.create("Anna");
		TestUser ben = this.testUsers.create("Ben");
		long crew = this.testUsers.createGroup(anna, "Mountain Crew");
		this.memberships.addOrReactivateMember(crew, ben.id());
		this.mvc.perform(get("/groups/" + crew).with(ben.login())).andExpect(status().isOk());

		this.mvc.perform(post("/groups/" + crew + "/leave").with(ben.login()).with(csrf()))
			.andExpect(redirectedUrl("/groups"));

		this.mvc.perform(get("/groups/" + crew).with(ben.login())).andExpect(status().isNotFound());
		this.mvc.perform(get("/groups/" + crew + "/settings").with(ben.login())).andExpect(status().isNotFound());
	}

	@Test
	void memberAttemptingOwnerActionsGetsForbidden() throws Exception {
		TestUser anna = this.testUsers.create("Anna");
		TestUser ben = this.testUsers.create("Ben");
		TestUser cleo = this.testUsers.create("Cleo");
		long crew = this.testUsers.createGroup(anna, "Mountain Crew");
		this.memberships.addOrReactivateMember(crew, ben.id());
		this.memberships.addOrReactivateMember(crew, cleo.id());

		this.mvc.perform(post("/groups/" + crew + "/members/" + cleo.id() + "/remove").with(ben.login()).with(csrf()))
			.andExpect(status().isForbidden());
		this.mvc.perform(post("/groups/" + crew + "/rename").param("name", "Ben's").with(ben.login()).with(csrf()))
			.andExpect(status().isForbidden());
		this.mvc.perform(post("/groups/" + crew + "/members/" + ben.id() + "/make-owner").with(ben.login()).with(csrf()))
			.andExpect(status().isForbidden());
		this.mvc.perform(post("/groups/" + crew + "/delete").param("confirmName", "Mountain Crew")
			.with(ben.login())
			.with(csrf())).andExpect(status().isForbidden());

		assertThat(role(crew, cleo)).isEqualTo(Role.MEMBER);
		assertThat(active(crew, cleo)).isTrue();
		assertThat(role(crew, anna)).isEqualTo(Role.OWNER);
		assertThat(this.groups.findById(crew).orElseThrow().getName()).isEqualTo("Mountain Crew");
	}

	// Settings page

	@Test
	void settingsShowMembersToEveryoneButOwnerControlsOnlyToTheOwner() throws Exception {
		TestUser anna = this.testUsers.create("Anna");
		TestUser ben = this.testUsers.create("Ben");
		long crew = this.testUsers.createGroup(anna, "Mountain Crew");
		this.memberships.addOrReactivateMember(crew, ben.id());

		this.mvc.perform(get("/groups/" + crew + "/settings").with(ben.login()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Members (2)")))
			.andExpect(content().string(containsString("Anna")))
			.andExpect(content().string(containsString("Owner")))
			.andExpect(content().string(containsString("Joined ")))
			.andExpect(content().string(containsString("Leave group")))
			.andExpect(content().string(not(containsString("Rename"))))
			.andExpect(content().string(not(containsString("Delete group"))));
		this.mvc.perform(get("/groups/" + crew + "/settings").with(anna.login()))
			.andExpect(content().string(containsString("Rename")))
			.andExpect(content().string(containsString("/members/" + ben.id() + "/remove")))
			.andExpect(content().string(containsString("Delete group")));
	}

	@Test
	void ownerRenamesGroup() throws Exception {
		TestUser anna = this.testUsers.create("Anna");
		long crew = this.testUsers.createGroup(anna, "Mountain Crew");

		this.mvc.perform(post("/groups/" + crew + "/rename").param("name", "Mountain Crew 2026")
			.with(anna.login())
			.with(csrf())).andExpect(redirectedUrl("/groups/" + crew + "/settings"));

		this.mvc.perform(get("/groups/" + crew + "/settings").with(anna.login()))
			.andExpect(content().string(containsString("<summary>Mountain Crew 2026</summary>")))
			.andExpect(content().string(containsString("<h1>Mountain Crew 2026</h1>")));
	}

	// Leave, remove, transfer, delete

	@Test
	void ownerCannotLeaveWhileOthersRemain() throws Exception {
		TestUser anna = this.testUsers.create("Anna");
		TestUser ben = this.testUsers.create("Ben");
		long crew = this.testUsers.createGroup(anna, "Mountain Crew");
		this.memberships.addOrReactivateMember(crew, ben.id());

		this.mvc.perform(post("/groups/" + crew + "/leave").with(anna.login()).with(csrf()))
			.andExpect(redirectedUrl("/groups/" + crew + "/settings"))
			.andExpect(flash().attribute("error", containsString("Transfer ownership")));

		assertThat(active(crew, anna)).isTrue();
		assertThat(role(crew, anna)).isEqualTo(Role.OWNER);
	}

	@Test
	void soleOwnerLeavingDeletesTheGroup() throws Exception {
		TestUser anna = this.testUsers.create("Anna");
		long crew = this.testUsers.createGroup(anna, "Mountain Crew");

		this.mvc.perform(post("/groups/" + crew + "/leave").with(anna.login()).with(csrf()))
			.andExpect(redirectedUrl("/groups"));

		assertThat(this.groups.findById(crew)).isEmpty();
		assertThat(this.memberships.findByGroupIdAndUserId(crew, anna.id())).isEmpty();
		assertThat(this.events.stream(GroupDeletedEvent.class)).containsExactly(new GroupDeletedEvent(crew));
	}

	@Test
	void ownerRemovesMemberWhoKeepsTheirRowButLosesAccess() throws Exception {
		TestUser anna = this.testUsers.create("Anna");
		TestUser alex = this.testUsers.create("Alex");
		long crew = this.testUsers.createGroup(anna, "Mountain Crew");
		this.memberships.addOrReactivateMember(crew, alex.id());

		this.mvc.perform(post("/groups/" + crew + "/members/" + alex.id() + "/remove").with(anna.login()).with(csrf()))
			.andExpect(redirectedUrl("/groups/" + crew + "/settings"));

		assertThat(active(crew, alex)).isFalse();
		this.mvc.perform(get("/groups/" + crew).with(alex.login())).andExpect(status().isNotFound());
		this.mvc.perform(get("/groups").with(alex.login()))
			.andExpect(content().string(not(containsString("href=\"/groups/" + crew + "\""))));
	}

	@Test
	void ownerCannotRemoveThemselves() throws Exception {
		TestUser anna = this.testUsers.create("Anna");
		long crew = this.testUsers.createGroup(anna, "Mountain Crew");

		this.mvc.perform(post("/groups/" + crew + "/members/" + anna.id() + "/remove").with(anna.login()).with(csrf()))
			.andExpect(flash().attribute("error", containsString("can't remove yourself")));

		assertThat(active(crew, anna)).isTrue();
	}

	@Test
	void ownerTransfersOwnership() throws Exception {
		TestUser anna = this.testUsers.create("Anna");
		TestUser sam = this.testUsers.create("Sam");
		long crew = this.testUsers.createGroup(anna, "Mountain Crew");
		this.memberships.addOrReactivateMember(crew, sam.id());

		this.mvc.perform(post("/groups/" + crew + "/members/" + sam.id() + "/make-owner").with(anna.login()).with(csrf()))
			.andExpect(redirectedUrl("/groups/" + crew + "/settings"));

		assertThat(role(crew, sam)).isEqualTo(Role.OWNER);
		assertThat(role(crew, anna)).isEqualTo(Role.MEMBER);
		this.mvc.perform(post("/groups/" + crew + "/members/" + sam.id() + "/remove").with(anna.login()).with(csrf()))
			.andExpect(status().isForbidden());
		this.mvc.perform(post("/groups/" + crew + "/leave").with(anna.login()).with(csrf()))
			.andExpect(redirectedUrl("/groups"));
		assertThat(active(crew, anna)).isFalse();
	}

	@Test
	void transferToFormerMemberIsRefused() throws Exception {
		TestUser anna = this.testUsers.create("Anna");
		TestUser sam = this.testUsers.create("Sam");
		long crew = this.testUsers.createGroup(anna, "Mountain Crew");
		this.memberships.addOrReactivateMember(crew, sam.id());
		this.mvc.perform(post("/groups/" + crew + "/leave").with(sam.login()).with(csrf()));

		this.mvc.perform(post("/groups/" + crew + "/members/" + sam.id() + "/make-owner").with(anna.login()).with(csrf()))
			.andExpect(flash().attribute("error", containsString("no longer a member")));

		assertThat(role(crew, anna)).isEqualTo(Role.OWNER);
	}

	@Test
	void deleteRequiresTypingTheGroupName() throws Exception {
		TestUser anna = this.testUsers.create("Anna");
		TestUser ben = this.testUsers.create("Ben");
		long crew = this.testUsers.createGroup(anna, "Mountain Crew");
		this.memberships.addOrReactivateMember(crew, ben.id());

		this.mvc.perform(post("/groups/" + crew + "/delete").param("confirmName", "mountain crew")
			.with(anna.login())
			.with(csrf())).andExpect(flash().attribute("error", containsString("doesn't match")));
		assertThat(this.groups.findById(crew)).isPresent();

		this.mvc.perform(post("/groups/" + crew + "/delete").param("confirmName", "Mountain Crew")
			.with(anna.login())
			.with(csrf())).andExpect(redirectedUrl("/groups"));

		assertThat(this.groups.findById(crew)).isEmpty();
		assertThat(this.memberships.findByGroupIdAndUserId(crew, ben.id())).isEmpty();
		assertThat(this.events.stream(GroupDeletedEvent.class)).containsExactly(new GroupDeletedEvent(crew));
		this.mvc.perform(get("/groups").with(ben.login()))
			.andExpect(content().string(not(containsString("href=\"/groups/" + crew + "\""))));
	}

	// Owner succession and former-member labels

	@Test
	void deletedOwnerIsSucceededByTheLongestStandingMember() throws Exception {
		TestUser anna = this.testUsers.create("Anna");
		TestUser kim = this.testUsers.create("Kim");
		TestUser lee = this.testUsers.create("Lee");
		long crew = this.testUsers.createGroup(anna, "Mountain Crew");
		this.memberships.addOrReactivateMember(crew, kim.id());
		this.memberships.addOrReactivateMember(crew, lee.id());
		long solo = this.testUsers.createGroup(anna, "Solo");

		this.succession.accountDeleted(anna.id());

		assertThat(role(crew, kim)).isEqualTo(Role.OWNER);
		assertThat(role(crew, lee)).isEqualTo(Role.MEMBER);
		assertThat(active(crew, anna)).isFalse();
		assertThat(this.groups.findById(solo)).as("no other members left").isEmpty();
	}

	@Test
	void deletedMemberJustLeaves() throws Exception {
		TestUser anna = this.testUsers.create("Anna");
		TestUser kim = this.testUsers.create("Kim");
		long crew = this.testUsers.createGroup(anna, "Mountain Crew");
		this.memberships.addOrReactivateMember(crew, kim.id());

		this.succession.accountDeleted(kim.id());

		assertThat(active(crew, kim)).isFalse();
		assertThat(role(crew, anna)).isEqualTo(Role.OWNER);
	}

	@Test
	void authorsWhoLeftAreLabelledFormerMembers() throws Exception {
		TestUser anna = this.testUsers.create("Anna");
		TestUser alex = this.testUsers.create("Alex");
		long crew = this.testUsers.createGroup(anna, "Mountain Crew");
		this.memberships.addOrReactivateMember(crew, alex.id());
		this.mvc.perform(post("/groups/" + crew + "/leave").with(alex.login()).with(csrf()));

		var authors = this.authorLabels.authorsIn(crew, java.util.List.of(anna.id(), alex.id()));

		assertThat(authors.get(anna.id()).label()).isEqualTo("Anna");
		assertThat(authors.get(alex.id()).label()).isEqualTo("Alex (former member)");
		assertThat(authors.get(alex.id()).formerMember()).isTrue();
	}

	private Role role(long groupId, TestUser user) {
		return this.memberships.findByGroupIdAndUserId(groupId, user.id()).orElseThrow().getRole();
	}

	private boolean active(long groupId, TestUser user) {
		return this.memberships.findByGroupIdAndUserId(groupId, user.id()).orElseThrow().getLeftAt() == null;
	}

}
