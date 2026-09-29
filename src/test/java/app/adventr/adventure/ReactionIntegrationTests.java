package app.adventr.adventure;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class ReactionIntegrationTests {

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
	private ReactionService reactions;

	@Autowired
	private JdbcClient jdbc;

	private TestUsers testUsers;

	private TestUser anna;

	private TestUser ben;

	private long crew;

	@BeforeEach
	void setUp() throws Exception {
		this.testUsers = new TestUsers(this.mvc, this.clientRegistrations, this.users);
		this.anna = this.testUsers.create("Anna");
		this.ben = this.testUsers.create("Ben");
		this.crew = this.testUsers.createGroup(this.anna, "Mountain Crew");
		this.memberships.addOrReactivateMember(this.crew, this.ben.id());
	}

	// Toggle semantics: all six transitions

	@Test
	void noneToUpAndBack() throws Exception {
		long trip = adventure("Canoe trip");

		click(this.anna, trip, ReactionType.UP);
		assertThat(reaction(trip, this.anna)).isEqualTo("UP");

		click(this.anna, trip, ReactionType.UP);
		assertThat(reaction(trip, this.anna)).isNull();
	}

	@Test
	void noneToDownAndBack() throws Exception {
		long trip = adventure("Canoe trip");

		click(this.anna, trip, ReactionType.DOWN);
		assertThat(reaction(trip, this.anna)).isEqualTo("DOWN");

		click(this.anna, trip, ReactionType.DOWN);
		assertThat(reaction(trip, this.anna)).isNull();
	}

	@Test
	void switchBetweenUpAndDown() throws Exception {
		long trip = adventure("Canoe trip");
		click(this.anna, trip, ReactionType.UP);

		ReactionBar down = this.reactions.toggle(this.anna.id(), this.crew, trip, ReactionType.DOWN);
		assertThat(down.ups()).isZero();
		assertThat(down.downs()).isEqualTo(1);
		assertThat(down.mineDown()).isTrue();

		ReactionBar up = this.reactions.toggle(this.anna.id(), this.crew, trip, ReactionType.UP);
		assertThat(up.ups()).isEqualTo(1);
		assertThat(up.downs()).isZero();
		assertThat(up.mineUp()).isTrue();
		assertThat(rows(trip)).isEqualTo(1);
	}

	@Test
	void reactingNeedsNoComment() throws Exception {
		long trip = adventure("Canoe trip");

		click(this.ben, trip, ReactionType.DOWN);

		assertThat(reaction(trip, this.ben)).isEqualTo("DOWN");
		assertThat(this.jdbc.sql("select count(*) from comments where adventure_id = ?")
			.param(trip)
			.query(Long.class)
			.single()).isZero();
	}

	@Test
	void concurrentDoubleClickLeavesAtMostOneRow() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			for (int round = 0; round < 15; round++) {
				long trip = adventure("Race " + round);
				CountDownLatch start = new CountDownLatch(1);
				List<Future<ReactionBar>> clicks = new ArrayList<>();
				for (int i = 0; i < 2; i++) {
					clicks.add(executor.submit(() -> {
						start.await();
						return this.reactions.toggle(this.anna.id(), this.crew, trip, ReactionType.UP);
					}));
				}
				start.countDown();
				for (Future<ReactionBar> click : clicks) {
					click.get(); // no exception escapes
				}
				assertThat(rows(trip)).isLessThanOrEqualTo(1);
			}
		}
		finally {
			executor.shutdownNow();
		}
	}

	// In-place update and fallback

	@Test
	void htmxClickInTheListReturnsOnlyTheBar() throws Exception {
		long trip = adventure("Canoe trip");

		this.mvc.perform(post("/groups/" + this.crew + "/adventures/" + trip + "/reactions").param("type", "UP")
			.param("view", "list")
			.header("HX-Request", "true")
			.with(this.anna.login())
			.with(csrf()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("id=\"reactions-" + trip + "\"")))
			.andExpect(content().string(containsString("class=\"reaction mine\"")))
			.andExpect(content().string(containsString("aria-pressed=\"true\"")))
			.andExpect(content().string(not(containsString("reactor-names"))))
			.andExpect(content().string(not(containsString("<html"))));
	}

	@Test
	void htmxClickOnTheDetailPageAlsoUpdatesTheNames() throws Exception {
		long trip = adventure("Canoe trip");

		this.mvc.perform(post("/groups/" + this.crew + "/adventures/" + trip + "/reactions").param("type", "UP")
			.param("view", "detail")
			.header("HX-Request", "true")
			.with(this.ben.login())
			.with(csrf()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("reactor-names")))
			.andExpect(content().string(containsString("author-name\">Ben</span>")));
	}

	@Test
	void withoutJavaScriptTheClickRedirectsBack() throws Exception {
		long trip = adventure("Canoe trip");

		this.mvc.perform(post("/groups/" + this.crew + "/adventures/" + trip + "/reactions").param("type", "UP")
			.param("view", "list")
			.with(this.anna.login())
			.with(csrf())).andExpect(redirectedUrl("/groups/" + this.crew + "#reactions-" + trip));
		this.mvc.perform(post("/groups/" + this.crew + "/adventures/" + trip + "/reactions").param("type", "UP")
			.with(this.anna.login())
			.with(csrf())).andExpect(redirectedUrl("/groups/" + this.crew + "/adventures/" + trip));
	}

	// Counts, names, and ordering

	@Test
	void detailListsWhoReactedIncludingFormerMembers() throws Exception {
		TestUser cleo = this.testUsers.create("Cleo");
		this.memberships.addOrReactivateMember(this.crew, cleo.id());
		long trip = adventure("Canoe trip");
		click(this.anna, trip, ReactionType.UP);
		click(this.ben, trip, ReactionType.UP);
		click(cleo, trip, ReactionType.DOWN);
		this.mvc.perform(post("/groups/" + this.crew + "/leave").with(cleo.login()).with(csrf()));

		String page = this.mvc.perform(get("/groups/" + this.crew + "/adventures/" + trip).with(this.anna.login()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(page).containsPattern("(?s)Thumbs up:</span>\\s*<span class=\"author\">.*?author-name\">Anna</span></span>, "
				+ "<span class=\"author\">.*?author-name\">Ben</span></span>");
		assertThat(page).containsPattern("(?s)Thumbs down:</span>\\s*<span class=\"author\">.*?author-name\">Cleo</span><span\\s+"
				+ "class=\"former-member\"> \\(former member\\)</span>");
		assertThat(page).contains("title=\"Cleo (former member)\"");
	}

	@Test
	void countsAreShownSeparately() throws Exception {
		long trip = adventure("Canoe trip");
		click(this.anna, trip, ReactionType.UP);
		click(this.ben, trip, ReactionType.DOWN);

		ReactionBar bar = this.reactions.bar(this.anna.id(), this.crew, trip);
		assertThat(bar.ups()).isEqualTo(1);
		assertThat(bar.downs()).isEqualTo(1);
		assertThat(bar.upNames()).isEqualTo("Anna");
		assertThat(bar.downNames()).isEqualTo("Ben");
	}

	@Test
	void netScoreOrdersTheIdeas() throws Exception {
		long low = adventure("Low idea");
		long high = adventure("High idea");
		long middle = adventure("Middle idea");
		click(this.anna, high, ReactionType.UP);
		click(this.ben, high, ReactionType.UP);
		click(this.anna, middle, ReactionType.UP);
		click(this.anna, low, ReactionType.DOWN);

		List<String> order = this.adventures.list(this.anna.id(), this.crew)
			.ideas()
			.stream()
			.map(AdventureItem::title)
			.toList();

		assertThat(order).containsExactly("High idea", "Middle idea", "Low idea");
	}

	@Test
	void nonMemberCannotReact() throws Exception {
		TestUser mallory = this.testUsers.create("Mallory");
		long trip = adventure("Canoe trip");

		this.mvc.perform(post("/groups/" + this.crew + "/adventures/" + trip + "/reactions").param("type", "UP")
			.with(mallory.login())
			.with(csrf())).andExpect(status().isNotFound());

		assertThat(rows(trip)).isZero();
	}

	private long adventure(String title) {
		return this.adventures.create(this.anna.id(), this.crew, AdventureForm.titleOnly(title));
	}

	private void click(TestUser user, long adventureId, ReactionType type) throws Exception {
		this.mvc.perform(post("/groups/" + this.crew + "/adventures/" + adventureId + "/reactions")
			.param("type", type.name())
			.header("HX-Request", "true")
			.with(user.login())
			.with(csrf())).andExpect(status().isOk());
	}

	private String reaction(long adventureId, TestUser user) {
		return this.jdbc.sql("select type from reactions where adventure_id = ? and user_id = ?")
			.params(adventureId, user.id())
			.query(String.class)
			.optional()
			.orElse(null);
	}

	private long rows(long adventureId) {
		return this.jdbc.sql("select count(*) from reactions where adventure_id = ?")
			.param(adventureId)
			.query(Long.class)
			.single();
	}

}
