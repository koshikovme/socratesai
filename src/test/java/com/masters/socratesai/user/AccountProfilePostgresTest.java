package com.masters.socratesai.user;

import com.fasterxml.jackson.databind.*;
import com.masters.socratesai.security.*;
import com.masters.socratesai.user.model.*;
import com.masters.socratesai.user.repo.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Account edits use a disposable owned database, never application or developer databases. */
@Testcontainers @SpringBootTest(properties = {"spring.config.import=", "app.execution.worker.enabled=false"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class AccountProfilePostgresTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtService jwt;
    @Value("${app.jwt.secret}") String signingSecret;
    private final ObjectMapper json = new ObjectMapper();
    private static final String PASSWORD = "Isolated-fixture-password-123";
    private static String passwordHash;
    private User owner;
    private String token;

    @BeforeEach void fixture() {
        if (passwordHash == null) passwordHash = passwords.encode(PASSWORD);
        owner = user(UserRole.STUDENT); token = jwt.generateToken(new SecurityUserDetails(owner));
    }

    @ParameterizedTest @EnumSource(UserRole.class)
    void eachRoleCanEditOnlyItsOwnValidatedProfile(UserRole role) throws Exception {
        owner.setRole(role); users.saveAndFlush(owner);
        token = jwt.generateToken(new SecurityUserDetails(owner));
        var update = form(token); update.put("fullName", "  Алимжан Қошықов  "); update.put("university", "  Test university  "); update.put("groupName", " CS-101 ");
        var result = call(put("/api/users/me/profile"), token, update).andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Алимжан Қошықов")).andExpect(jsonPath("$.university").value("Test university"))
                .andExpect(jsonPath("$.groupName").value("CS-101")).andExpect(jsonPath("$.role").value(role.name()))
                .andExpect(jsonPath("$.authentication").doesNotExist()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("passwordHash", PASSWORD, "currentPassword", "token");
        var stored = users.findById(owner.getId()).orElseThrow();
        assertThat(stored.getPasswordHash()).isEqualTo(passwordHash);
        assertThat(stored.getId()).isEqualTo(owner.getId());
        assertThat(stored.getCreatedAt()).isEqualTo(owner.getCreatedAt());
        var clear = form(token); clear.put("university", "  "); clear.put("groupName", "");
        call(put("/api/users/me/profile"), token, clear).andExpect(status().isOk()).andExpect(jsonPath("$.university").isEmpty()).andExpect(jsonPath("$.groupName").isEmpty());
    }

    @Test void emailChangeRequiresReauthenticationAndCannotClaimAnotherEmail() throws Exception {
        var update = form(token); update.put("email", "other-" + UUID.randomUUID() + "@example.test");
        call(put("/api/users/me/profile"), token, update).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PROFILE_REAUTHENTICATION_REQUIRED"));
        update.put("currentPassword", "private-wrong-password");
        var failure = call(put("/api/users/me/profile"), token, update).andExpect(status().isBadRequest()).andReturn();
        assertThat(failure.getResponse().getContentAsString()).doesNotContain("private-wrong-password", update.get("email").toString(), "password_hash");
        update.put("email", user(UserRole.TEACHER).getEmail().toUpperCase(Locale.ROOT)); update.put("currentPassword", PASSWORD);
        call(put("/api/users/me/profile"), token, update).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_UNAVAILABLE"));
        call(get("/api/users/me"), token, null).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(owner.getEmail()));
    }

    @Test void emailRotationPreservesIdentityAndOldTokensCannotFollowReusedEmail() throws Exception {
        var update = form(token); String oldEmail = owner.getEmail(); String newEmail = "new-" + UUID.randomUUID() + "@example.test";
        update.put("email", newEmail.toUpperCase(Locale.ROOT)); update.put("currentPassword", PASSWORD);
        var result = tree(call(put("/api/users/me/profile"), token, update).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(newEmail)).andReturn());
        String replacement = result.path("authentication").path("token").asText();
        assertThat(replacement).isNotBlank();
        call(get("/api/users/me"), replacement, null).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(owner.getId()));
        call(get("/api/users/me"), token, null).andExpect(status().isUnauthorized());
        call(post("/api/auth/login"), null, Map.of("email", newEmail.toUpperCase(Locale.ROOT), "password", PASSWORD)).andExpect(status().isOk());
        call(post("/api/auth/login"), null, Map.of("email", oldEmail, "password", PASSWORD)).andExpect(status().isUnauthorized());
        call(post("/api/auth/register"), null, Map.of("email", oldEmail, "fullName", "Another learner", "password", "different-fixture-password"))
                .andExpect(status().isOk());
        call(get("/api/users/me"), token, null).andExpect(status().isUnauthorized());
        call(get("/api/users/me"), replacement, null).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(owner.getId()));
    }

    @Test void tokensWithoutImmutableAccountIdentityAreRejected() throws Exception {
        String legacy = Jwts.builder().subject(owner.getEmail()).expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(signingSecret))).compact();
        call(get("/api/users/me"), legacy, null).andExpect(status().isUnauthorized());
    }

    @Test void staleAndConcurrentUpdatesCannotOverwriteAnotherTab() throws Exception {
        var first = form(token); var second = new HashMap<>(first);
        first.put("fullName", "First tab"); second.put("fullName", "Second tab");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var calls = List.of(first, second).stream().map(body -> pool.submit(() -> {
                gate.await(); return call(put("/api/users/me/profile"), token, body).andReturn().getResponse().getStatus();
            })).toList(); gate.countDown();
            assertThat(List.of(calls.get(0).get(10, TimeUnit.SECONDS), calls.get(1).get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        }
        call(put("/api/users/me/profile"), token, first).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test void themeSaveAndProfileSavePreserveEachOthersFields() throws Exception {
        var update = form(token); update.put("university", "Concurrent university");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var profile = pool.submit(() -> { gate.await(); return call(put("/api/users/me/profile"), token, update).andReturn().getResponse().getStatus(); });
            var settings = pool.submit(() -> { gate.await(); return call(put("/api/users/me/settings"), token, Map.of("darkMode", true)).andReturn().getResponse().getStatus(); });
            gate.countDown(); assertThat(profile.get(10, TimeUnit.SECONDS)).isEqualTo(200); assertThat(settings.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        }
        call(get("/api/users/me"), token, null).andExpect(status().isOk()).andExpect(jsonPath("$.darkMode").value(true)).andExpect(jsonPath("$.university").value("Concurrent university"));
    }

    @Test void profileEditingAndAdministrativeRoleChangeCannotOverwriteEachOther() throws Exception {
        var administrator = user(UserRole.ADMIN);
        String adminToken = jwt.generateToken(new SecurityUserDetails(administrator));
        var update = form(token); update.put("fullName", "Edited while role changes");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var profile = pool.submit(() -> { gate.await(); return call(put("/api/users/me/profile"), token, update).andReturn().getResponse().getStatus(); });
            var role = pool.submit(() -> { gate.await(); return call(patch("/api/admin/users/{id}/role", owner.getId()), adminToken,
                    Map.of("expectedRole", "STUDENT", "role", "TEACHER", "reason", "Isolated profile concurrency test")).andReturn().getResponse().getStatus(); });
            gate.countDown(); assertThat(profile.get(10, TimeUnit.SECONDS)).isEqualTo(200); assertThat(role.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        }
        call(get("/api/users/me"), token, null).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.fullName").value("Edited while role changes"));
    }

    @Test void simultaneousEmailClaimsHaveOnlyOneWinner() throws Exception {
        User other = user(UserRole.STUDENT); String otherToken = jwt.generateToken(new SecurityUserDetails(other));
        String target = "claim-" + UUID.randomUUID() + "@example.test";
        var first = form(token); var second = form(otherToken);
        first.put("email", target); second.put("email", target); first.put("currentPassword", PASSWORD); second.put("currentPassword", PASSWORD);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var a = pool.submit(() -> { gate.await(); return call(put("/api/users/me/profile"), token, first).andReturn().getResponse().getStatus(); });
            var b = pool.submit(() -> { gate.await(); return call(put("/api/users/me/profile"), otherToken, second).andReturn().getResponse().getStatus(); });
            gate.countDown(); assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(users.findByEmail(target)).isPresent();
    }

    @Test void validationRejectsSpoofedOwnershipRolesAndOverlongOrInvalidFields() throws Exception {
        call(put("/api/users/me/profile"), null, Map.of()).andExpect(status().isUnauthorized());
        var original = form(token);
        for (String forbidden : List.of("role", "id", "internalAccount", "passwordHash")) {
            var spoof = new HashMap<>(original); spoof.put(forbidden, "ADMIN");
            call(put("/api/users/me/profile"), token, spoof).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        for (var invalid : List.of(Map.of("fullName", " "), Map.of("fullName", "x".repeat(121)), Map.of("email", "invalid"),
                Map.of("university", "x".repeat(121)), Map.of("groupName", "x".repeat(81)), Map.of("profileRevision", "invalid"))) {
            var update = new HashMap<>(original); update.putAll(invalid);
            call(put("/api/users/me/profile"), token, update).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        call(put("/api/users/me/settings"), token, Map.of("preferredLanguage", "invalid")).andExpect(status().isBadRequest());
        call(get("/api/users/me"), token, null).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("STUDENT")).andExpect(jsonPath("$.fullName").value(owner.getFullName()));
    }

    private User user(UserRole role) {
        return users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.test").passwordHash(passwordHash)
                .fullName("Fixture owner").role(role).settings(UserSettings.builder().build()).createdAt(LocalDateTime.now().withNano(0)).build());
    }
    private Map<String,Object> form(String auth) throws Exception {
        JsonNode response = tree(call(get("/api/users/me"), auth, null).andExpect(status().isOk()).andReturn());
        return new HashMap<>(Map.of("fullName", response.path("fullName").asText(), "email", response.path("email").asText(),
                "university", response.path("university").asText(""), "groupName", response.path("groupName").asText(""), "profileRevision", response.path("profileRevision").asText()));
    }
    private ResultActions call(MockHttpServletRequestBuilder request, String auth, Object body) throws Exception {
        if (auth != null) request.header("Authorization", "Bearer " + auth);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        return mvc.perform(request);
    }
    private JsonNode tree(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
}
