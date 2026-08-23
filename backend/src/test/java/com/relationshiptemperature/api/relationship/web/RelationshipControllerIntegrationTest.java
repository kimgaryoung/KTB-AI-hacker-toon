package com.relationshiptemperature.api.relationship.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.relationshiptemperature.api.auth.application.AppOAuth2User;
import com.relationshiptemperature.api.auth.domain.User;
import com.relationshiptemperature.api.auth.repository.UserRepository;
import com.relationshiptemperature.api.relationship.domain.Relationship;
import com.relationshiptemperature.api.relationship.domain.RelationshipType;
import com.relationshiptemperature.api.relationship.repository.RelationshipRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
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
class RelationshipControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RelationshipRepository relationshipRepository;

    @Test
    void createsRelationshipWithNormalizedName() throws Exception {
        User user = saveUser("kakao-create");

        mockMvc.perform(post("/api/v1/relationships")
                        .with(authentication(oauthAuthentication(user)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  홍길동  ","relationshipType":"FRIEND"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.startsWith("/api/v1/relationships/")))
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.initial").value("길"))
                .andExpect(jsonPath("$.data.relationshipType").value("FRIEND"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        assertThat(relationshipRepository.findAllByUserIdOrderByUpdatedAtDesc(user.getId()))
                .singleElement()
                .extracting(Relationship::getName)
                .isEqualTo("홍길동");
    }

    @Test
    void searchesFiltersAndSortsOwnedRelationships() throws Exception {
        User user = saveUser("kakao-list");
        Relationship active = Relationship.draft(user.getId(), "김길동", RelationshipType.FRIEND);
        active.startAnalysis();
        active.completeAnalysis(81, -12, Instant.parse("2026-08-18T10:00:00Z"));
        relationshipRepository.save(active);
        relationshipRepository.save(Relationship.draft(user.getId(), "이영희", RelationshipType.FAMILY));

        mockMvc.perform(get("/api/v1/relationships")
                        .with(authentication(oauthAuthentication(user)))
                        .param("search", " 길 ")
                        .param("status", "ACTIVE")
                        .param("sort", "ABS_CHANGE_DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("김길동"))
                .andExpect(jsonPath("$.data[0].score").value(81))
                .andExpect(jsonPath("$.data[0].change").value(-12))
                .andExpect(jsonPath("$.meta.hasNext").value(false));
    }

    @Test
    void rejectsBlankNameAndEmptyUpdate() throws Exception {
        User user = saveUser("kakao-invalid-update");
        Relationship relationship = relationshipRepository.save(
                Relationship.draft(user.getId(), "수정 전", RelationshipType.OTHER)
        );

        mockMvc.perform(patch("/api/v1/relationships/{id}", relationship.getId())
                        .with(authentication(oauthAuthentication(user)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        mockMvc.perform(patch("/api/v1/relationships/{id}", relationship.getId())
                        .with(authentication(oauthAuthentication(user)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        assertThat(relationshipRepository.findById(relationship.getId()).orElseThrow().getName())
                .isEqualTo("수정 전");
    }

    @Test
    void hidesOtherUsersRelationshipAsNotFound() throws Exception {
        User owner = saveUser("kakao-owner");
        User other = saveUser("kakao-other");
        Relationship relationship = relationshipRepository.save(
                Relationship.draft(owner.getId(), "소유자 관계", RelationshipType.FRIEND)
        );

        mockMvc.perform(get("/api/v1/relationships/{id}", relationship.getId())
                        .with(authentication(oauthAuthentication(other))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RELATIONSHIP_NOT_FOUND"));
    }

    @Test
    void deletesOwnedRelationshipAndRequiresCsrf() throws Exception {
        User user = saveUser("kakao-delete");
        Relationship relationship = relationshipRepository.save(
                Relationship.draft(user.getId(), "삭제 대상", RelationshipType.OTHER)
        );

        mockMvc.perform(delete("/api/v1/relationships/{id}", relationship.getId())
                        .with(authentication(oauthAuthentication(user))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/relationships/{id}", relationship.getId())
                        .with(authentication(oauthAuthentication(user)))
                        .with(csrf()))
                .andExpect(status().isAccepted());

        assertThat(relationshipRepository.findById(relationship.getId())).isEmpty();
    }

    private User saveUser(String subject) {
        return userRepository.save(User.kakao(subject, subject, null));
    }

    private OAuth2AuthenticationToken oauthAuthentication(User user) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var principal = new AppOAuth2User(user.getId(), user.getKakaoSubject(), Map.of(), authorities);
        return new OAuth2AuthenticationToken(principal, authorities, "kakao");
    }
}
