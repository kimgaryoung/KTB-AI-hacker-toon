package com.relationshiptemperature.api.checkin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.relationshiptemperature.api.auth.application.AppOAuth2User;
import com.relationshiptemperature.api.auth.domain.User;
import com.relationshiptemperature.api.auth.repository.UserRepository;
import com.relationshiptemperature.api.checkin.domain.CheckIn;
import com.relationshiptemperature.api.checkin.domain.CheckInAnswer;
import com.relationshiptemperature.api.checkin.domain.QuestionCode;
import com.relationshiptemperature.api.checkin.repository.CheckInAnswerRepository;
import com.relationshiptemperature.api.checkin.repository.CheckInRepository;
import com.relationshiptemperature.api.relationship.domain.Relationship;
import com.relationshiptemperature.api.relationship.domain.RelationshipType;
import com.relationshiptemperature.api.relationship.repository.RelationshipRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
@AutoConfigureMockMvc
class CheckInControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RelationshipRepository relationshipRepository;

    @Autowired
    CheckInRepository checkInRepository;

    @Autowired
    CheckInAnswerRepository answerRepository;

    @Test
    void createsThenUpdatesSameWeeksCheckIn() throws Exception {
        User user = saveUser("checkin-upsert");
        Relationship relationship = saveRelationship(user, "홍길동");

        mockMvc.perform(post("/api/v1/relationships/{id}/check-ins", relationship.getId())
                        .with(authentication(oauthAuthentication(user)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(5, 4)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.relationshipId").value(relationship.getId().toString()))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.updatedAt").exists())
                .andExpect(jsonPath("$.data.answers[0].questionCode").value("RELATIONSHIP_FEELING"))
                .andExpect(jsonPath("$.data.answers[0].score").value(5))
                .andExpect(jsonPath("$.data.answers[1].questionCode").value("CONVERSATION_COMFORT"))
                .andExpect(jsonPath("$.data.answers[1].score").value(4));

        CheckIn first = checkInRepository.findAllByUserIdAndRelationshipIdOrderByWeekStartDesc(
                user.getId(), relationship.getId()
        ).getFirst();

        mockMvc.perform(post("/api/v1/relationships/{id}/check-ins", relationship.getId())
                        .with(authentication(oauthAuthentication(user)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(7, 6)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(first.getId().toString()))
                .andExpect(jsonPath("$.data.updatedAt").exists())
                .andExpect(jsonPath("$.data.answers[0].score").value(7))
                .andExpect(jsonPath("$.data.answers[1].score").value(6));

        assertThat(checkInRepository.findAllByUserIdAndRelationshipIdOrderByWeekStartDesc(
                user.getId(), relationship.getId()
        )).hasSize(1);
        assertThat(answerRepository.findAllByCheckInIdIn(List.of(first.getId())))
                .extracting(CheckInAnswer::getScore)
                .containsExactlyInAnyOrder(7, 6);
    }

    @Test
    void returnsWeeklyHistoryNewestFirstAndSupportsDateRange() throws Exception {
        User user = saveUser("checkin-history");
        Relationship relationship = saveRelationship(user, "주차 이력");
        LocalDate thisWeek = LocalDate.now(ZoneId.of(user.getTimezone()))
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        saveCheckIn(user, relationship, thisWeek.minusWeeks(1), 3, 2);
        saveCheckIn(user, relationship, thisWeek, 6, 5);

        mockMvc.perform(get("/api/v1/relationships/{id}/check-ins", relationship.getId())
                        .with(authentication(oauthAuthentication(user)))
                        .param("from", thisWeek.minusWeeks(1).toString())
                        .param("to", thisWeek.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].weekStart").value(thisWeek.toString()))
                .andExpect(jsonPath("$.data[0].answers[0].score").value(6))
                .andExpect(jsonPath("$.data[1].weekStart").value(thisWeek.minusWeeks(1).toString()))
                .andExpect(jsonPath("$.meta.hasNext").value(false));
    }

    @Test
    void rejectsDuplicateMissingAndOutOfRangeAnswers() throws Exception {
        User user = saveUser("checkin-invalid");
        Relationship relationship = saveRelationship(user, "입력 검증");

        mockMvc.perform(post("/api/v1/relationships/{id}/check-ins", relationship.getId())
                        .with(authentication(oauthAuthentication(user)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answers":[
                                  {"questionCode":"RELATIONSHIP_FEELING","score":5},
                                  {"questionCode":"RELATIONSHIP_FEELING","score":4}
                                ]}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("CHECK_IN_INCOMPLETE"));

        mockMvc.perform(post("/api/v1/relationships/{id}/check-ins", relationship.getId())
                        .with(authentication(oauthAuthentication(user)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answers":[
                                  {"questionCode":"RELATIONSHIP_FEELING","score":8},
                                  {"questionCode":"CONVERSATION_COMFORT","score":4}
                                ]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void protectsOwnershipAndRequiresCsrf() throws Exception {
        User owner = saveUser("checkin-owner");
        User other = saveUser("checkin-other");
        Relationship relationship = saveRelationship(owner, "소유권");

        mockMvc.perform(post("/api/v1/relationships/{id}/check-ins", relationship.getId())
                        .with(authentication(oauthAuthentication(other)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(5, 4)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RELATIONSHIP_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/relationships/{id}/check-ins", relationship.getId())
                        .with(authentication(oauthAuthentication(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(5, 4)))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsReversedHistoryRange() throws Exception {
        User user = saveUser("checkin-range");
        Relationship relationship = saveRelationship(user, "기간 검증");

        mockMvc.perform(get("/api/v1/relationships/{id}/check-ins", relationship.getId())
                        .with(authentication(oauthAuthentication(user)))
                        .param("from", "2026-08-18")
                        .param("to", "2026-08-11"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    private User saveUser(String subject) {
        return userRepository.save(User.kakao(subject, subject, null));
    }

    private Relationship saveRelationship(User user, String name) {
        return relationshipRepository.save(Relationship.draft(user.getId(), name, RelationshipType.FRIEND));
    }

    private void saveCheckIn(
            User user,
            Relationship relationship,
            LocalDate weekStart,
            int feeling,
            int comfort
    ) {
        CheckIn checkIn = checkInRepository.save(new CheckIn(user.getId(), relationship.getId(), weekStart));
        answerRepository.save(new CheckInAnswer(checkIn.getId(), QuestionCode.RELATIONSHIP_FEELING, feeling));
        answerRepository.save(new CheckInAnswer(checkIn.getId(), QuestionCode.CONVERSATION_COMFORT, comfort));
    }

    private String requestBody(int feeling, int comfort) {
        return """
                {"answers":[
                  {"questionCode":"RELATIONSHIP_FEELING","score":%d},
                  {"questionCode":"CONVERSATION_COMFORT","score":%d}
                ]}
                """.formatted(feeling, comfort);
    }

    private OAuth2AuthenticationToken oauthAuthentication(User user) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var principal = new AppOAuth2User(user.getId(), user.getKakaoSubject(), Map.of(), authorities);
        return new OAuth2AuthenticationToken(principal, authorities, "kakao");
    }
}
