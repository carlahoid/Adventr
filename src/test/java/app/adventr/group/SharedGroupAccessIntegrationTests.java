package app.adventr.group;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;

import app.adventr.IntegrationTest;
import app.adventr.TestUsers;
import app.adventr.TestUsers.TestUser;
import app.adventr.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@IntegrationTest
class SharedGroupAccessIntegrationTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ClientRegistrationRepository clientRegistrations;

	@Autowired
	private UserRepository users;

	@Autowired
	private MembershipRepository memberships;

	@Autowired
	private GroupAccessService access;

	@Test
	void selfAndCurrentCoMembersAreAllowed() throws Exception {
		TestUsers testUsers = new TestUsers(this.mvc, this.clientRegistrations, this.users);
		TestUser anna = testUsers.create("Anna");
		TestUser ben = testUsers.create("Ben");
		long crew = testUsers.createGroup(anna, "Mountain Crew");
		this.memberships.addOrReactivateMember(crew, ben.id());

		assertThatCode(() -> this.access.requireSharedGroup(anna.id(), anna.id())).doesNotThrowAnyException();
		assertThatCode(() -> this.access.requireSharedGroup(anna.id(), ben.id())).doesNotThrowAnyException();
		assertThatCode(() -> this.access.requireSharedGroup(ben.id(), anna.id())).doesNotThrowAnyException();
	}

	@Test
	void strangersAndFormerCoMembersAreDenied() throws Exception {
		TestUsers testUsers = new TestUsers(this.mvc, this.clientRegistrations, this.users);
		TestUser anna = testUsers.create("Anna");
		TestUser ben = testUsers.create("Ben");
		TestUser mallory = testUsers.create("Mallory");
		long crew = testUsers.createGroup(anna, "Mountain Crew");
		this.memberships.addOrReactivateMember(crew, ben.id());
		testUsers.createGroup(mallory, "Mallory's");

		assertThatExceptionOfType(GroupAccessDeniedException.class)
			.isThrownBy(() -> this.access.requireSharedGroup(mallory.id(), anna.id()));

		this.mvc.perform(post("/groups/" + crew + "/leave").with(ben.login()).with(csrf()));

		assertThatExceptionOfType(GroupAccessDeniedException.class)
			.isThrownBy(() -> this.access.requireSharedGroup(ben.id(), anna.id()));
		assertThatExceptionOfType(GroupAccessDeniedException.class)
			.isThrownBy(() -> this.access.requireSharedGroup(anna.id(), ben.id()));
	}

}
