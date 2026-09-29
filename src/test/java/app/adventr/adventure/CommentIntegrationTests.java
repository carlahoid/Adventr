package app.adventr.adventure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;

import app.adventr.IntegrationTest;
import app.adventr.TestUsers;
import app.adventr.TestUsers.TestUser;
import app.adventr.group.MembershipRepository;
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
class CommentIntegrationTests {

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

	@Autowired
	private CommentService comments;

	@Autowired
	private CommentRepository commentRepository;

	private TestUsers testUsers;

	private TestUser anna;

	private TestUser ben;

	private TestUser cleo;

	private long crew;

	private long trip;

	@BeforeEach
	void setUp() throws Exception {
		this.testUsers = new TestUsers(this.mvc, this.clientRegistrations, this.users);
		this.anna = this.testUsers.create("Anna");
		this.ben = this.testUsers.create("Ben");
		this.cleo = this.testUsers.create("Cleo");
		this.crew = this.testUsers.createGroup(this.anna, "Mountain Crew");
		this.memberships.addOrReactivateMember(this.crew, this.ben.id());
		this.memberships.addOrReactivateMember(this.crew, this.cleo.id());
		this.trip = this.adventures.create(this.anna.id(), this.crew, AdventureForm.titleOnly("Canoe trip"));
	}

	// Posting

	@Test
	void memberPostsWithHtmxAndTheThreadIsReRendered() throws Exception {
		this.mvc.perform(post(commentsUrl()).param("text", "I'm in, but only in August!")
			.header("HX-Request", "true")
			.with(this.ben.login())
			.with(csrf()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("id=\"comments\"")))
			.andExpect(content().string(containsString("Comments (1)")))
			.andExpect(content().string(containsString("I&#39;m in, but only in August!")))
			.andExpect(content().string(containsString("author-name\">Ben</span>")))
			.andExpect(content().string(not(containsString("<html"))));
	}

	@Test
	void blankCommentIsRejected() throws Exception {
		this.mvc.perform(post(commentsUrl()).param("text", "  \n ")
			.header("HX-Request", "true")
			.with(this.ben.login())
			.with(csrf()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Please write something")))
			.andExpect(content().string(containsString("Comments (0)")));
		this.mvc.perform(post(commentsUrl()).param("text", "")
			.with(this.ben.login())
			.with(csrf()))
			.andExpect(redirectedUrl(detailUrl() + "#comments"))
			.andExpect(flash().attribute("commentError", "Please write something before posting."));
		this.mvc.perform(post(commentsUrl()).param("text", "x".repeat(2001)).with(this.ben.login()).with(csrf()))
			.andExpect(flash().attribute("commentError", "Comments can be at most 2000 characters long."));

		assertThat(this.commentRepository.findByAdventureIdOrderByCreatedAtAscIdAsc(this.trip)).isEmpty();
	}

	@Test
	void threadIsOldestFirstWithLineBreaksKeptAndTextEscaped() throws Exception {
		this.comments.post(this.anna.id(), this.crew, this.trip, "First");
		this.comments.post(this.ben.id(), this.crew, this.trip, "Second\nwith a line break");
		this.comments.post(this.cleo.id(), this.crew, this.trip, "<script>alert(1)</script>");

		String page = this.mvc.perform(get(detailUrl()).with(this.anna.login()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(page).contains("Comments (3)");
		assertThat(page.indexOf(">First<")).isLessThan(page.indexOf(">Second\nwith a line break<"));
		assertThat(page.indexOf(">Second\nwith a line break<")).isLessThan(page.indexOf("&lt;script&gt;alert(1)"));
		assertThat(page).doesNotContain("<script>alert(1)");
	}

	@Test
	void listShowsTheCommentCount() throws Exception {
		this.comments.post(this.anna.id(), this.crew, this.trip, "One");
		this.comments.post(this.ben.id(), this.crew, this.trip, "Two");

		AdventureItem item = this.adventures.list(this.anna.id(), this.crew).ideas().getFirst();

		assertThat(item.commentCount()).isEqualTo(2);
	}

	// Editing

	@Test
	void authorEditsAndTheCommentIsMarkedEdited() throws Exception {
		long id = this.comments.post(this.ben.id(), this.crew, this.trip, "Maybe");

		this.mvc.perform(get(commentsUrl() + "/" + id + "/edit").header("HX-Request", "true").with(this.ben.login()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Edit your comment")));
		this.mvc.perform(post(commentsUrl() + "/" + id + "/edit").param("text", "Definitely!")
			.header("HX-Request", "true")
			.with(this.ben.login())
			.with(csrf()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("id=\"comment-" + id + "\"")))
			.andExpect(content().string(containsString("Definitely!")))
			.andExpect(content().string(containsString("(edited)")));

		Comment edited = this.commentRepository.findById(id).orElseThrow();
		assertThat(edited.getText()).isEqualTo("Definitely!");
		assertThat(edited.getEditedAt()).isNotNull();
	}

	@Test
	void editingWithoutJavaScriptOpensTheFormOnTheDetailPage() throws Exception {
		long id = this.comments.post(this.ben.id(), this.crew, this.trip, "Maybe");

		this.mvc.perform(get(detailUrl()).param("editComment", String.valueOf(id)).with(this.ben.login()))
			.andExpect(content().string(containsString("Edit your comment")));
		// Someone else's comment id does not open a form.
		this.mvc.perform(get(detailUrl()).param("editComment", String.valueOf(id)).with(this.anna.login()))
			.andExpect(content().string(not(containsString("Edit your comment"))));
		this.mvc.perform(post(commentsUrl() + "/" + id + "/edit").param("text", "Sure")
			.with(this.ben.login())
			.with(csrf())).andExpect(redirectedUrl(detailUrl() + "#comment-" + id));
	}

	@Test
	void nobodyButTheAuthorCanEditNotEvenTheOwner() throws Exception {
		long id = this.comments.post(this.ben.id(), this.crew, this.trip, "Ben's comment");

		this.mvc.perform(post(commentsUrl() + "/" + id + "/edit").param("text", "Owner was here")
			.with(this.anna.login())
			.with(csrf())).andExpect(status().isForbidden());
		this.mvc.perform(get(commentsUrl() + "/" + id + "/edit").with(this.cleo.login()))
			.andExpect(status().isForbidden());

		assertThat(this.commentRepository.findById(id).orElseThrow().getText()).isEqualTo("Ben's comment");
	}

	// Deleting

	@Test
	void authorDeletesTheirComment() throws Exception {
		long id = this.comments.post(this.ben.id(), this.crew, this.trip, "Oops");

		this.mvc.perform(post(commentsUrl() + "/" + id + "/delete").header("HX-Request", "true")
			.with(this.ben.login())
			.with(csrf()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Comments (0)")));

		assertThat(this.commentRepository.findById(id)).isEmpty();
	}

	@Test
	void ownerDeletesSomeoneElsesComment() throws Exception {
		long id = this.comments.post(this.ben.id(), this.crew, this.trip, "Off topic");

		this.mvc.perform(post(commentsUrl() + "/" + id + "/delete").with(this.anna.login()).with(csrf()))
			.andExpect(redirectedUrl(detailUrl() + "#comments"));

		assertThat(this.commentRepository.findById(id)).isEmpty();
	}

	@Test
	void memberCannotDeleteSomeoneElsesComment() throws Exception {
		long id = this.comments.post(this.ben.id(), this.crew, this.trip, "Ben's comment");

		this.mvc.perform(post(commentsUrl() + "/" + id + "/delete").with(this.cleo.login()).with(csrf()))
			.andExpect(status().isForbidden());

		assertThat(this.commentRepository.findById(id)).isPresent();
	}

	@Test
	void ownerSeesDeleteButOnlyTheAuthorSeesEdit() throws Exception {
		this.comments.post(this.ben.id(), this.crew, this.trip, "Ben's comment");

		this.mvc.perform(get(detailUrl()).with(this.anna.login()))
			.andExpect(content().string(containsString("Delete<span class=\"visually-hidden\"> comment")))
			.andExpect(content().string(not(containsString("Edit<span class=\"visually-hidden\"> comment"))));
		this.mvc.perform(get(detailUrl()).with(this.cleo.login()))
			.andExpect(content().string(not(containsString("Delete<span class=\"visually-hidden\"> comment"))));
	}

	// Authors and access

	@Test
	void formerMembersCommentsStayWithALabel() throws Exception {
		this.comments.post(this.cleo.id(), this.crew, this.trip, "See you all");
		this.mvc.perform(post("/groups/" + this.crew + "/leave").with(this.cleo.login()).with(csrf()));

		this.mvc.perform(get(detailUrl()).with(this.anna.login()))
			.andExpect(content().string(containsString("See you all")))
			.andExpect(content().string(containsString("(former member)")));
	}

	@Test
	void nonMemberAndCommentsOfOtherAdventuresGetNotFound() throws Exception {
		TestUser mallory = this.testUsers.create("Mallory");
		long id = this.comments.post(this.ben.id(), this.crew, this.trip, "Private");
		long other = this.adventures.create(this.anna.id(), this.crew, AdventureForm.titleOnly("Other"));

		this.mvc.perform(post(commentsUrl()).param("text", "Hi").with(mallory.login()).with(csrf()))
			.andExpect(status().isNotFound());
		this.mvc.perform(get(commentsUrl() + "/" + id).with(mallory.login())).andExpect(status().isNotFound());
		// A real comment id, addressed through another adventure of the same group.
		this.mvc.perform(post("/groups/" + this.crew + "/adventures/" + other + "/comments/" + id + "/delete")
			.with(this.anna.login())
			.with(csrf())).andExpect(status().isNotFound());

		assertThat(this.commentRepository.findById(id)).isPresent();
	}

	private String detailUrl() {
		return "/groups/" + this.crew + "/adventures/" + this.trip;
	}

	private String commentsUrl() {
		return detailUrl() + "/comments";
	}

}
