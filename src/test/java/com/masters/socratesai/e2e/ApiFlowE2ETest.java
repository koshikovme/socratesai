package com.masters.socratesai.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiFlowE2ETest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired private com.masters.socratesai.user.repo.UserRepository users;
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Autowired private com.masters.socratesai.security.JwtService jwt;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRejectProtectedTaskCreationWithoutJwt() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Unauthorized task",
                                  "topic": "arrays",
                                  "difficulty": "EASY",
                                  "language": "java",
                                  "description": "desc",
                                  "starterCode": "class Main {}",
                                  "published": false
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void provisionedTeacherCanCreateDraftThroughLegacyFacade() throws Exception {
        String token = registerTeacher("teacher-midterm-1@example.com");

        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Midterm Published Task",
                                  "topic": "loops",
                                  "difficulty": "EASY",
                                  "language": "java",
                                  "description": "Count numbers",
                                  "starterCode": "class Main {}",
                                  "published": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Midterm Published Task"))
                .andExpect(jsonPath("$.published").value(false));
    }

    @Test
    void draftCreatedThroughLegacyFacadeIsNotPublic() throws Exception {
        String token = registerTeacher("teacher-midterm-2@example.com");

        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Visible Public Task",
                                  "topic": "conditions",
                                  "difficulty": "MEDIUM",
                                  "language": "java",
                                  "description": "Check sign",
                                  "starterCode": "class Main {}",
                                  "published": false
                                }
                                """))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get("/api/tasks/public"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("Visible Public Task");
    }

    private String registerTeacher(String email) {
        var teacher = users.save(com.masters.socratesai.user.model.User.builder().email(email).fullName("Synthetic teacher")
                .passwordHash(passwords.encode("test-fixture-password"))
                .role(com.masters.socratesai.user.model.UserRole.TEACHER).createdAt(java.time.LocalDateTime.now())
                .settings(com.masters.socratesai.user.model.UserSettings.builder().build()).build());
        return jwt.generateToken(new com.masters.socratesai.security.SecurityUserDetails(teacher));
    }
}
