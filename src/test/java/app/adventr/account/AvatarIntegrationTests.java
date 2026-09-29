package app.adventr.account;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import app.adventr.IntegrationTest;
import app.adventr.TestUsers;
import app.adventr.TestUsers.TestUser;
import app.adventr.TestcontainersConfiguration;
import app.adventr.adventure.AdventureForm;
import app.adventr.adventure.AdventureService;
import app.adventr.adventure.CommentService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AvatarIntegrationTests {

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

	private TestUsers testUsers;

	private TestUser kim;

	private TestUser ben;

	private long crew;

	@BeforeEach
	void setUp() throws Exception {
		this.testUsers = new TestUsers(this.mvc, this.clientRegistrations, this.users);
		this.kim = this.testUsers.create("Kim Climber");
		this.ben = this.testUsers.create("Ben");
		this.crew = this.testUsers.createGroup(this.ben, "Mountain Crew");
		this.memberships.addOrReactivateMember(this.crew, this.kim.id());
	}

	@Test
	void uploadedPhotoBecomesASquareWithoutMetadata() throws Exception {
		upload(this.kim, TestImages.jpegWithOrientation(3000, 2000, 6))
			.andExpect(flash().attribute("notice", "Profile picture saved."));

		byte[] served = this.mvc.perform(get(avatarUrl(this.kim)).with(this.kim.login()))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "image/jpeg"))
			.andExpect(header().string("Cache-Control", containsString("private")))
			.andExpect(header().string("Cache-Control", containsString("immutable")))
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
		var image = TestImages.read(served);
		assertThat(image.getWidth()).isEqualTo(256);
		assertThat(image.getHeight()).isEqualTo(256);
		assertThat(new String(served, StandardCharsets.ISO_8859_1)).doesNotContain("Exif");

		String version = avatarVersion(this.kim);
		this.mvc.perform(get("/account").with(this.kim.login()))
			.andExpect(content().string(containsString("/users/" + this.kim.id() + "/avatar?v=" + version)))
			.andExpect(content().string(containsString("Remove picture")));
	}

	@Test
	void replacingAndRemovingDeleteThePreviousFile() throws Exception {
		upload(this.kim, TestImages.jpeg(400, 400));
		Path first = avatarFile(this.kim);
		assertThat(first).exists();

		upload(this.kim, TestImages.png(300, 500));
		Path second = avatarFile(this.kim);
		assertThat(second).exists().isNotEqualTo(first);
		assertThat(first).doesNotExist();

		this.mvc.perform(post("/account/avatar/remove").with(this.kim.login()).with(csrf()))
			.andExpect(flash().attribute("notice", "Profile picture removed."));
		assertThat(second).doesNotExist();
		assertThat(avatarVersion(this.kim)).isNull();
		this.mvc.perform(get(avatarUrl(this.kim)).with(this.kim.login())).andExpect(status().isNotFound());
	}

	@Test
	void renamedPdfAndOversizedFilesAreRejectedAndTheOldAvatarStays() throws Exception {
		upload(this.kim, TestImages.jpeg(300, 300));
		String version = avatarVersion(this.kim);

		upload(this.kim, TestImages.PDF).andExpect(flash().attribute("error", "Only JPEG, PNG, and WebP images are supported."));
		byte[] sixMegabytes = new byte[6 * 1024 * 1024];
		byte[] jpeg = TestImages.jpeg(100, 100);
		System.arraycopy(jpeg, 0, sixMegabytes, 0, jpeg.length);
		upload(this.kim, sixMegabytes).andExpect(flash().attribute("error", "Profile pictures can be at most 5 MB."));

		assertThat(avatarVersion(this.kim)).isEqualTo(version);
	}

	@Test
	void selfAndCoMembersSeeTheAvatarStrangersDoNot() throws Exception {
		upload(this.kim, TestImages.jpeg(300, 300));
		TestUser mallory = this.testUsers.create("Mallory");

		this.mvc.perform(get(avatarUrl(this.kim)).with(this.kim.login())).andExpect(status().isOk());
		this.mvc.perform(get(avatarUrl(this.kim)).with(this.ben.login())).andExpect(status().isOk());
		this.mvc.perform(get(avatarUrl(this.kim)).with(mallory.login())).andExpect(status().isNotFound());
		// Unknown users look the same as strangers.
		this.mvc.perform(get("/users/999999999/avatar").with(mallory.login())).andExpect(status().isNotFound());
	}

	@Test
	void formerCoMembersGetNotFoundAndSeeInitials() throws Exception {
		upload(this.kim, TestImages.jpeg(300, 300));
		long trip = this.adventures.create(this.ben.id(), this.crew, AdventureForm.titleOnly("Canoe trip"));
		this.comments.post(this.kim.id(), this.crew, trip, "Count me in");
		this.mvc.perform(get("/groups/" + this.crew + "/adventures/" + trip).with(this.ben.login()))
			.andExpect(content().string(containsString("/users/" + this.kim.id() + "/avatar?v=")));

		this.mvc.perform(post("/groups/" + this.crew + "/leave").with(this.kim.login()).with(csrf()));

		this.mvc.perform(get(avatarUrl(this.kim)).with(this.ben.login())).andExpect(status().isNotFound());
		this.mvc.perform(get("/groups/" + this.crew + "/adventures/" + trip).with(this.ben.login()))
			.andExpect(content().string(not(containsString("/users/" + this.kim.id() + "/avatar"))))
			.andExpect(content().string(containsString(">KC</span>")))
			.andExpect(content().string(containsString("(former member)")));
	}

	@Test
	void membersWithoutAvatarShowInitialsNextToTheirName() throws Exception {
		this.mvc.perform(get("/groups/" + this.crew + "/settings").with(this.ben.login()))
			.andExpect(content().string(containsString("aria-hidden=\"true\">KC</span>")))
			.andExpect(content().string(containsString("<strong>Kim Climber</strong>")))
			.andExpect(content().string(not(containsString("/avatar?v="))));
		this.mvc.perform(get("/groups").with(this.kim.login()))
			.andExpect(content().string(containsString(">KC</span><span>Kim Climber</span></a>")));
	}

	@Test
	void headerAndMemberListShowTheAvatar() throws Exception {
		upload(this.kim, TestImages.jpeg(300, 300));
		String src = "/users/" + this.kim.id() + "/avatar?v=" + avatarVersion(this.kim);

		this.mvc.perform(get("/groups").with(this.kim.login())).andExpect(content().string(containsString(src)));
		this.mvc.perform(get("/groups/" + this.crew + "/settings").with(this.ben.login()))
			.andExpect(content().string(containsString(src)));
	}

	private ResultActions upload(TestUser user, byte[] content) throws Exception {
		return this.mvc.perform(multipart("/account/avatar")
			.file(new MockMultipartFile("avatar", "me.jpg", "image/jpeg", content))
			.with(user.login())
			.with(csrf()));
	}

	private String avatarVersion(TestUser user) {
		return this.users.findById(user.id()).orElseThrow().getAvatarVersion();
	}

	private Path avatarFile(TestUser user) throws Exception {
		String path = this.users.findById(user.id()).orElseThrow().getAvatarPath();
		assertThat(path).startsWith("avatars/" + user.id() + "/");
		Path file = TestcontainersConfiguration.IMAGES_DIR.resolve(path);
		assertThat(Files.size(file)).isPositive();
		return file;
	}

	private String avatarUrl(TestUser user) {
		return "/users/" + user.id() + "/avatar";
	}

}
