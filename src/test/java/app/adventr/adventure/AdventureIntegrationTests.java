package app.adventr.adventure;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import app.adventr.IntegrationTest;
import app.adventr.TestUsers;
import app.adventr.TestUsers.TestUser;
import app.adventr.TestcontainersConfiguration;
import app.adventr.group.MembershipRepository;
import app.adventr.image.TestImages;
import app.adventr.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AdventureIntegrationTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ClientRegistrationRepository clientRegistrations;

	@Autowired
	private UserRepository users;

	@Autowired
	private MembershipRepository memberships;

	@Autowired
	private AdventureRepository adventures;

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

	// List, sections, and ordering

	@Test
	void emptyGroupShowsTheEmptyState() throws Exception {
		this.mvc.perform(get("/groups/" + this.crew).with(this.anna.login()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("No adventures yet")));
	}

	@Test
	void sectionsAreOrderedPlannedIdeasMemories() throws Exception {
		TestUser cleo = member("Cleo");
		TestUser dan = member("Dan");
		long done = create(this.anna, "Memory hike");
		setStatus(this.anna, done, Status.DONE);
		long plus3 = create(this.anna, "Idea plus three");
		long plus1 = create(this.anna, "Idea plus one");
		long planned = create(this.anna, "Planned canoe");
		setStatus(this.ben, planned, Status.PLANNED);
		react(plus3, List.of(this.anna, this.ben, cleo), ReactionType.UP);
		react(plus1, List.of(this.anna, this.ben), ReactionType.UP);
		react(plus1, List.of(dan), ReactionType.DOWN);

		String page = listPage(this.anna);

		assertInOrder(page, "Planned canoe", "Idea plus three", "Idea plus one", "Memories", "Memory hike");
		assertInOrder(page, "Planned</h2>", "Ideas</h2>", "Memories</h2>");
	}

	@Test
	void plannedAreOrderedByDateWithUndatedLast() throws Exception {
		long undated = create(this.anna, "Undated plan");
		long later = createWithDate(this.anna, "Later plan", "2027-05-01");
		long sooner = createWithDate(this.anna, "Sooner plan", "2027-01-15");
		for (long id : List.of(undated, later, sooner)) {
			setStatus(this.anna, id, Status.PLANNED);
		}

		assertInOrder(listPage(this.anna), "Sooner plan", "Later plan", "Undated plan");
	}

	@Test
	void ideasWithEqualScoreAreNewestFirstAndMemoriesByLatestStatusChange() throws Exception {
		create(this.anna, "Older idea");
		create(this.anna, "Newer idea");
		long first = create(this.anna, "Done first");
		long second = create(this.anna, "Done second");
		setStatus(this.anna, first, Status.DONE);
		setStatus(this.anna, second, Status.DONE);

		String page = listPage(this.anna);
		assertInOrder(page, "Newer idea", "Older idea");
		assertInOrder(page, "Done second", "Done first");
	}

	@Test
	void listShowsCountsOwnReactionAndCommentCount() throws Exception {
		long trip = create(this.anna, "Canoe trip");
		react(trip, List.of(this.anna), ReactionType.UP);
		react(trip, List.of(this.ben), ReactionType.DOWN);
		this.jdbc.sql("insert into comments (adventure_id, author_id, text) values (?, ?, 'Yes!'), (?, ?, 'When?')")
			.params(trip, this.anna.id(), trip, this.ben.id())
			.update();

		String page = listPage(this.anna);

		assertThat(page).containsPattern("<button [^>]*value=\"UP\" class=\"reaction mine\"[^>]*aria-pressed=\"true\"")
			.containsPattern("<button [^>]*value=\"UP\"[^>]*title=\"Anna\"")
			.containsPattern("<button [^>]*value=\"DOWN\" class=\"reaction\"[^>]*aria-pressed=\"false\"")
			.containsPattern("<button [^>]*value=\"DOWN\"[^>]*title=\"Ben\"")
			.contains("<span>2</span>")
			.contains("> comments</span>");
	}

	// Quick add

	@Test
	void quickAddWithHtmxRendersTheListInPlace() throws Exception {
		this.mvc.perform(post("/groups/" + this.crew + "/adventures").param("title", "Canoe trip")
			.header("HX-Request", "true")
			.with(this.anna.login())
			.with(csrf()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("id=\"adventure-sections\"")))
			.andExpect(content().string(containsString("Ideas</h2>")))
			.andExpect(content().string(containsString(">Canoe trip</a>")))
			.andExpect(content().string(containsString("hx-swap-oob=\"true\"")))
			.andExpect(content().string(not(containsString("<html"))));

		Adventure added = this.adventures.findAll()
			.stream()
			.filter((a) -> a.getGroupId() == this.crew)
			.findFirst()
			.orElseThrow();
		assertThat(added.getStatus()).isEqualTo(Status.IDEA);
		assertThat(added.getCreatedBy()).isEqualTo(this.anna.id());
	}

	@Test
	void quickAddWithoutTitleShowsTheErrorInTheForm() throws Exception {
		this.mvc.perform(post("/groups/" + this.crew + "/adventures").param("title", "  ")
			.header("HX-Request", "true")
			.with(this.anna.login())
			.with(csrf()))
			.andExpect(status().isOk())
			.andExpect(header().string("HX-Retarget", "#quick-add"))
			.andExpect(content().string(containsString("Please enter a title")));
	}

	@Test
	void quickAddWithoutJavaScriptRedirects() throws Exception {
		this.mvc.perform(post("/groups/" + this.crew + "/adventures").param("title", "Canoe trip")
			.with(this.anna.login())
			.with(csrf())).andExpect(redirectedUrl("/groups/" + this.crew));

		assertThat(listPage(this.anna)).contains("Canoe trip");
	}

	// Detail, edit, status, delete

	@Test
	void detailShowsSetFieldsOnly() throws Exception {
		long trip = create(this.anna, "Canoe trip", "location", "Lake Tahoe", "link", "https://example.com/canoe");

		this.mvc.perform(get("/groups/" + this.crew + "/adventures/" + trip).with(this.ben.login()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Lake Tahoe")))
			.andExpect(content().string(containsString("rel=\"noopener noreferrer\"")))
			.andExpect(content().string(containsString("class=\"author-name\">Anna</span>")))
			.andExpect(content().string(not(containsString("<dt>Cost</dt>"))))
			.andExpect(content().string(not(containsString("<dt>When</dt>"))))
			.andExpect(content().string(not(containsString(">Edit</a>"))));
	}

	@Test
	void creatorEditsAllFields() throws Exception {
		long trip = create(this.anna, "Canoe trip");

		this.mvc.perform(post("/groups/" + this.crew + "/adventures/" + trip + "/edit").param("title", "Canoe weekend")
			.param("location", "Lake Tahoe")
			.param("dateFrom", "2027-06-01")
			.param("dateTo", "2027-06-03")
			.param("costAmount", "45")
			.with(this.anna.login())
			.with(csrf())).andExpect(redirectedUrl("/groups/" + this.crew + "/adventures/" + trip));

		Adventure saved = this.adventures.findById(trip).orElseThrow();
		assertThat(saved.getTitle()).isEqualTo("Canoe weekend");
		assertThat(saved.getLocation()).isEqualTo("Lake Tahoe");
		assertThat(saved.getUpdatedAt()).isAfter(saved.getCreatedAt());
		this.mvc.perform(get("/groups/" + this.crew + "/adventures/" + trip).with(this.anna.login()))
			.andExpect(content().string(containsString("last updated")));
	}

	@Test
	void otherMemberCannotEdit() throws Exception {
		long trip = create(this.anna, "Canoe trip");

		this.mvc.perform(get("/groups/" + this.crew + "/adventures/" + trip + "/edit").with(this.ben.login()))
			.andExpect(status().isForbidden());
		this.mvc.perform(post("/groups/" + this.crew + "/adventures/" + trip + "/edit").param("title", "Hijacked")
			.with(this.ben.login())
			.with(csrf())).andExpect(status().isForbidden());
		this.mvc.perform(multipart("/groups/" + this.crew + "/adventures/" + trip + "/image")
			.file(jpegUpload())
			.with(this.ben.login())
			.with(csrf())).andExpect(status().isForbidden());

		assertThat(this.adventures.findById(trip).orElseThrow().getTitle()).isEqualTo("Canoe trip");
	}

	@Test
	void invalidFieldsAreRejectedWithMessages() throws Exception {
		long trip = create(this.anna, "Canoe trip");

		this.mvc.perform(post("/groups/" + this.crew + "/adventures/" + trip + "/edit").param("title", "Canoe trip")
			.param("dateFrom", "2027-06-03")
			.param("dateTo", "2027-06-01")
			.param("link", "javascript:alert(1)")
			.with(this.anna.login())
			.with(csrf()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("The end date can&#39;t be before the start date.")))
			.andExpect(content().string(containsString("starting with http:// or https://")));

		Adventure unchanged = this.adventures.findById(trip).orElseThrow();
		assertThat(unchanged.getDateFrom()).isNull();
		assertThat(unchanged.getLink()).isNull();
	}

	@Test
	void anyMemberChangesStatus() throws Exception {
		long trip = create(this.anna, "Canoe trip");

		setStatus(this.ben, trip, Status.DONE);

		Adventure done = this.adventures.findById(trip).orElseThrow();
		assertThat(done.getStatus()).isEqualTo(Status.DONE);
		assertThat(done.getStatusChangedAt()).isAfter(done.getCreatedAt());
		assertInOrder(listPage(this.ben), "Memories</h2>", "Canoe trip");
	}

	@Test
	void ownerDeletesAnotherMembersAdventureWithEverythingAttached() throws Exception {
		long trip = create(this.ben, "Ben's trip");
		upload(this.ben, trip, jpegUpload()).andExpect(flash().attribute("notice", "Image saved."));
		react(trip, List.of(this.anna), ReactionType.UP);
		this.jdbc.sql("insert into comments (adventure_id, author_id, text) values (?, ?, 'Nice')")
			.params(trip, this.anna.id())
			.update();
		List<Path> files = imageFiles();
		assertThat(files).hasSize(2);

		this.mvc.perform(post("/groups/" + this.crew + "/adventures/" + trip + "/delete").with(this.anna.login())
			.with(csrf())).andExpect(redirectedUrl("/groups/" + this.crew));

		assertThat(this.adventures.findById(trip)).isEmpty();
		assertThat(count("reactions", trip)).isZero();
		assertThat(count("comments", trip)).isZero();
		assertThat(files).noneMatch(Files::exists);
	}

	@Test
	void memberCannotDeleteAnotherMembersAdventure() throws Exception {
		long trip = create(this.anna, "Anna's trip");

		this.mvc.perform(post("/groups/" + this.crew + "/adventures/" + trip + "/delete").with(this.ben.login())
			.with(csrf())).andExpect(status().isForbidden());

		assertThat(this.adventures.findById(trip)).isPresent();
	}

	// Authorization and safe rendering

	@Test
	void nonMemberAndOtherGroupIdsGetNotFound() throws Exception {
		TestUser mallory = this.testUsers.create("Mallory");
		long malloryGroup = this.testUsers.createGroup(mallory, "Mallory's");
		long trip = create(this.anna, "Canoe trip");

		this.mvc.perform(get("/groups/" + this.crew + "/adventures/" + trip).with(mallory.login()))
			.andExpect(status().isNotFound());
		this.mvc.perform(post("/groups/" + this.crew + "/adventures/" + trip + "/status").param("status", "DONE")
			.with(mallory.login())
			.with(csrf())).andExpect(status().isNotFound());
		// The adventure id with a group the attacker does belong to.
		this.mvc.perform(get("/groups/" + malloryGroup + "/adventures/" + trip).with(mallory.login()))
			.andExpect(status().isNotFound());
		this.mvc.perform(post("/groups/" + malloryGroup + "/adventures/" + trip + "/delete").with(mallory.login())
			.with(csrf())).andExpect(status().isNotFound());

		assertThat(this.adventures.findById(trip).orElseThrow().getStatus()).isEqualTo(Status.IDEA);
	}

	@Test
	void userContentIsEscaped() throws Exception {
		long trip = create(this.anna, "<script>alert(1)</script>", "description", "<img src=x onerror=alert(2)>");

		this.mvc.perform(get("/groups/" + this.crew).with(this.ben.login()))
			.andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
			.andExpect(content().string(not(containsString("<script>alert(1)"))));
		this.mvc.perform(get("/groups/" + this.crew + "/adventures/" + trip).with(this.ben.login()))
			.andExpect(content().string(containsString("&lt;img src=x onerror=alert(2)&gt;")))
			.andExpect(content().string(not(containsString("<script>alert(1)"))));
	}

	// Images

	@Test
	void uploadedPhotoIsResizedAndServedToMembersOnly() throws Exception {
		long trip = create(this.anna, "Canoe trip");

		upload(this.anna, trip, new MockMultipartFile("image", "photo.jpg", "image/jpeg", TestImages.jpeg(4000, 3000)))
			.andExpect(flash().attribute("notice", "Image saved."));

		MvcResult image = this.mvc.perform(get("/groups/" + this.crew + "/adventures/" + trip + "/image")
			.with(this.ben.login()))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "image/jpeg"))
			.andExpect(header().string("Cache-Control", containsString("private")))
			.andReturn();
		var stored = TestImages.read(image.getResponse().getContentAsByteArray());
		assertThat(stored.getWidth()).isEqualTo(1600);
		assertThat(stored.getHeight()).isEqualTo(1200);
		this.mvc.perform(get("/groups/" + this.crew + "/adventures/" + trip).with(this.ben.login()))
			.andExpect(content().string(containsString("/adventures/" + trip + "/image?v=")));
		this.mvc.perform(get("/groups/" + this.crew).with(this.ben.login()))
			.andExpect(content().string(containsString("/adventures/" + trip + "/thumbnail?v=")));

		TestUser mallory = this.testUsers.create("Mallory");
		this.mvc.perform(get("/groups/" + this.crew + "/adventures/" + trip + "/image").with(mallory.login()))
			.andExpect(status().isNotFound());
		this.mvc.perform(get("/groups/" + this.crew + "/adventures/" + trip + "/thumbnail").with(mallory.login()))
			.andExpect(status().isNotFound());
	}

	@Test
	void renamedPdfIsRejectedAndNothingIsStored() throws Exception {
		long trip = create(this.anna, "Canoe trip");

		upload(this.anna, trip, new MockMultipartFile("image", "photo.jpg", "image/jpeg", TestImages.PDF))
			.andExpect(flash().attribute("error", "Only JPEG, PNG, and WebP images are supported."));

		assertThat(this.adventures.findById(trip).orElseThrow().getImagePath()).isNull();
		assertThat(imageFiles()).isEmpty();
	}

	@Test
	void oversizedFileIsRejectedWithTheLimit() throws Exception {
		long trip = create(this.anna, "Canoe trip");
		byte[] fifteenMegabytes = new byte[15 * 1024 * 1024];
		byte[] jpeg = TestImages.jpeg(100, 100);
		System.arraycopy(jpeg, 0, fifteenMegabytes, 0, jpeg.length);

		upload(this.anna, trip, new MockMultipartFile("image", "big.jpg", "image/jpeg", fifteenMegabytes))
			.andExpect(flash().attribute("error", "Images can be at most 10 MB."));

		assertThat(this.adventures.findById(trip).orElseThrow().getImagePath()).isNull();
	}

	@Test
	void replacingAndRemovingDeleteThePreviousFiles() throws Exception {
		long trip = create(this.anna, "Canoe trip");
		upload(this.anna, trip, jpegUpload());
		List<Path> first = imageFiles();

		upload(this.anna, trip, jpegUpload());
		List<Path> second = imageFiles();
		assertThat(first).noneMatch(Files::exists);
		assertThat(second).hasSize(2).doesNotContainAnyElementsOf(first);

		this.mvc.perform(post("/groups/" + this.crew + "/adventures/" + trip + "/image/remove").with(this.anna.login())
			.with(csrf())).andExpect(flash().attribute("notice", "Image removed."));
		assertThat(imageFiles()).isEmpty();
		assertThat(this.adventures.findById(trip).orElseThrow().getImagePath()).isNull();
	}

	@Test
	void deletingTheGroupRemovesItsImageDirectory() throws Exception {
		long trip = create(this.anna, "Canoe trip");
		upload(this.anna, trip, jpegUpload());
		assertThat(groupImageDirectory()).isDirectory();

		this.mvc.perform(post("/groups/" + this.crew + "/delete").param("confirmName", "Mountain Crew")
			.with(this.anna.login())
			.with(csrf())).andExpect(redirectedUrl("/groups"));

		assertThat(groupImageDirectory()).doesNotExist();
	}

	// Helpers

	private TestUser member(String name) throws Exception {
		TestUser user = this.testUsers.create(name);
		this.memberships.addOrReactivateMember(this.crew, user.id());
		return user;
	}

	private long create(TestUser user, String title, String... fields) throws Exception {
		var request = post("/groups/" + this.crew + "/adventures/new").param("title", title);
		for (int i = 0; i < fields.length; i += 2) {
			request.param(fields[i], fields[i + 1]);
		}
		String location = this.mvc.perform(request.with(user.login()).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andReturn()
			.getResponse()
			.getRedirectedUrl();
		return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
	}

	private long createWithDate(TestUser user, String title, String dateFrom) throws Exception {
		return create(user, title, "dateFrom", dateFrom);
	}

	private void setStatus(TestUser user, long adventureId, Status status) throws Exception {
		this.mvc.perform(post("/groups/" + this.crew + "/adventures/" + adventureId + "/status")
			.param("status", status.name())
			.with(user.login())
			.with(csrf())).andExpect(status().is3xxRedirection());
	}

	private void react(long adventureId, List<TestUser> reactors, ReactionType type) {
		for (TestUser reactor : reactors) {
			this.jdbc.sql("insert into reactions (adventure_id, user_id, type) values (?, ?, ?)")
				.params(adventureId, reactor.id(), type.name())
				.update();
		}
	}

	private org.springframework.test.web.servlet.ResultActions upload(TestUser user, long adventureId,
			MockMultipartFile file) throws Exception {
		return this.mvc.perform(multipart("/groups/" + this.crew + "/adventures/" + adventureId + "/image").file(file)
			.with(user.login())
			.with(csrf()));
	}

	private static MockMultipartFile jpegUpload() {
		return new MockMultipartFile("image", "photo.jpg", "image/jpeg", TestImages.jpeg(800, 600));
	}

	private String listPage(TestUser user) throws Exception {
		return this.mvc.perform(get("/groups/" + this.crew).with(user.login()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	private long count(String table, long adventureId) {
		return this.jdbc.sql("select count(*) from " + table + " where adventure_id = ?")
			.param(adventureId)
			.query(Long.class)
			.single();
	}

	private Path groupImageDirectory() {
		return TestcontainersConfiguration.IMAGES_DIR.resolve(String.valueOf(this.crew));
	}

	private List<Path> imageFiles() throws Exception {
		Path directory = groupImageDirectory();
		if (!Files.isDirectory(directory)) {
			return List.of();
		}
		try (Stream<Path> files = Files.list(directory)) {
			return files.sorted().toList();
		}
	}

	private static void assertInOrder(String page, String... texts) {
		int previous = -1;
		for (String text : texts) {
			int index = page.indexOf(text, previous + 1);
			assertThat(index).as("\"%s\" after position %d", text, previous).isGreaterThan(previous);
			previous = index;
		}
	}

}
