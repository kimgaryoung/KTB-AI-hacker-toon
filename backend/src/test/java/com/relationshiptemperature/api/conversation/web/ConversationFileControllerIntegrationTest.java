package com.relationshiptemperature.api.conversation.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.relationshiptemperature.api.auth.application.AppOAuth2User;
import com.relationshiptemperature.api.auth.domain.User;
import com.relationshiptemperature.api.auth.repository.UserRepository;
import com.relationshiptemperature.api.conversation.domain.ConversationMessage;
import com.relationshiptemperature.api.conversation.repository.ConversationFileRepository;
import com.relationshiptemperature.api.conversation.repository.ConversationMessageRepository;
import com.relationshiptemperature.api.relationship.domain.Relationship;
import com.relationshiptemperature.api.relationship.domain.RelationshipType;
import com.relationshiptemperature.api.relationship.repository.RelationshipRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
@AutoConfigureMockMvc
class ConversationFileControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RelationshipRepository relationshipRepository;
    @Autowired ConversationFileRepository fileRepository;
    @Autowired ConversationMessageRepository messageRepository;

    @AfterEach
    void cleanMongoMessages() {
        messageRepository.deleteAll();
    }

    @Test
    void uploadsCsvAndPersistsOrderedNormalizedMessages() throws Exception {
        User user = saveUser("conversation-csv");
        Relationship relationship = saveRelationship(user, "카카오 친구");
        MockMultipartFile file = file("chat.csv", "text/csv", csv());

        mockMvc.perform(multipart("/api/v1/relationships/{id}/conversation-files", relationship.getId())
                        .file(file)
                        .param("source", "KAKAO_TALK")
                        .param("selfParticipantName", "민지")
                        .with(authentication(oauthAuthentication(user)))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.selfParticipantName").value("민지"))
                .andExpect(jsonPath("$.data.otherParticipantName").value("준호"))
                .andExpect(jsonPath("$.data.messageCount").value(2));

        var files = fileRepository.findAll();
        assertThat(files).hasSize(1);
        List<ConversationMessage> messages = messageRepository
                .findAllByConversationFileIdOrderBySequenceNumberAsc(files.getFirst().getId());
        assertThat(messages).extracting(ConversationMessage::getSequenceNumber).containsExactly(0, 1);
        assertThat(messages).extracting(ConversationMessage::getSenderName).containsExactly("민지", "준호");
        assertThat(messages).extracting(ConversationMessage::getContent).containsExactly("안녕", "반가워");
    }

    @Test
    void uploadsTxtWithRequiredSelfParticipantName() throws Exception {
        User user = saveUser("conversation-txt");
        Relationship relationship = saveRelationship(user, "텍스트 친구");
        MockMultipartFile file = file("chat.txt", "text/plain", txt());

        mockMvc.perform(multipart("/api/v1/relationships/{id}/conversation-files", relationship.getId())
                        .file(file)
                        .param("source", "KAKAO_TALK")
                        .param("selfParticipantName", "민지")
                        .with(authentication(oauthAuthentication(user)))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.selfParticipantName").value("민지"))
                .andExpect(jsonPath("$.data.otherParticipantName").value("준호"));
    }

    @Test
    void rejectsUnsupportedExtensionAndMissingSelfParticipantName() throws Exception {
        User user = saveUser("conversation-invalid-request");
        Relationship relationship = saveRelationship(user, "검증");

        mockMvc.perform(multipart("/api/v1/relationships/{id}/conversation-files", relationship.getId())
                        .file(file("chat.pdf", "application/pdf", "not supported"))
                        .param("source", "KAKAO_TALK")
                        .param("selfParticipantName", "민지")
                        .with(authentication(oauthAuthentication(user)))
                        .with(csrf()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_FILE_TYPE"));

        mockMvc.perform(multipart("/api/v1/relationships/{id}/conversation-files", relationship.getId())
                        .file(file("chat.csv", "text/csv", csv()))
                        .param("source", "KAKAO_TALK")
                        .with(authentication(oauthAuthentication(user)))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDuplicateRelationshipAndHash() throws Exception {
        User user = saveUser("conversation-duplicate");
        Relationship relationship = saveRelationship(user, "중복");
        MockMultipartFile file = file("chat.csv", "text/csv", csv());
        upload(user, relationship, file);

        mockMvc.perform(multipart("/api/v1/relationships/{id}/conversation-files", relationship.getId())
                        .file(file("chat.csv", "text/csv", csv()))
                        .param("source", "KAKAO_TALK")
                        .param("selfParticipantName", "민지")
                        .with(authentication(oauthAuthentication(user)))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_CONVERSATION_FILE"));

        assertThat(fileRepository.findAll()).hasSize(1);
    }

    @Test
    void storesOnlyNewMessagesWhenUploadingExtendedConversationExport() throws Exception {
        User user = saveUser("conversation-extended");
        Relationship relationship = saveRelationship(user, "추가 대화");
        upload(user, relationship, file("first.csv", "text/csv", csv()));

        mockMvc.perform(multipart("/api/v1/relationships/{id}/conversation-files", relationship.getId())
                        .file(file("second.csv", "text/csv", extendedCsv()))
                        .param("source", "KAKAO_TALK")
                        .param("selfParticipantName", "민지")
                        .with(authentication(oauthAuthentication(user)))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.messageCount").value(1));

        var files = fileRepository.findAll();
        assertThat(files).hasSize(2);
        var secondFile = files.stream()
                .filter(item -> item.getOriginalFileName().equals("second.csv"))
                .findFirst()
                .orElseThrow();
        List<ConversationMessage> secondMessages = messageRepository
                .findAllByConversationFileIdOrderBySequenceNumberAsc(secondFile.getId());
        assertThat(secondMessages).hasSize(1);
        assertThat(secondMessages.getFirst().getSequenceNumber()).isEqualTo(2);
        assertThat(secondMessages.getFirst().getSenderName()).isEqualTo("민지");
        assertThat(secondMessages.getFirst().getContent()).isEqualTo("추가 메시지");
        assertThat(messageRepository.findAll()).hasSize(3);
    }

    @Test
    void rejectsExtendedUploadWhenItContainsNoNewMessages() throws Exception {
        User user = saveUser("conversation-no-new");
        Relationship relationship = saveRelationship(user, "새 메시지 없음");
        upload(user, relationship, file("first.csv", "text/csv", csv()));

        mockMvc.perform(multipart("/api/v1/relationships/{id}/conversation-files", relationship.getId())
                        .file(file("renamed.csv", "text/csv", csv()))
                        .param("source", "KAKAO_TALK")
                        .param("selfParticipantName", "민지")
                        .with(authentication(oauthAuthentication(user)))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_CONVERSATION_FILE"));

        assertThat(fileRepository.findAll()).hasSize(1);
        assertThat(messageRepository.findAll()).hasSize(2);
    }

    @Test
    void rejectsSelfParticipantMismatchWithoutPersisting() throws Exception {
        User user = saveUser("conversation-self-mismatch");
        Relationship relationship = saveRelationship(user, "이름 불일치");

        mockMvc.perform(multipart("/api/v1/relationships/{id}/conversation-files", relationship.getId())
                        .file(file("chat.csv", "text/csv", csv()))
                        .param("source", "KAKAO_TALK")
                        .param("selfParticipantName", "수진")
                        .with(authentication(oauthAuthentication(user)))
                        .with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("SELF_PARTICIPANT_MISMATCH"));

        assertThat(fileRepository.findAll()).isEmpty();
        assertThat(messageRepository.findAll()).isEmpty();
    }

    @Test
    void rejectsGroupConversationWithExplicitError() throws Exception {
        User user = saveUser("conversation-group");
        Relationship relationship = saveRelationship(user, "단체 대화");
        String groupCsv = "Date,User,Message\n"
                + "2026-08-19 10:00:00,민지,안녕\n"
                + "2026-08-19 10:01:00,준호,반가워\n"
                + "2026-08-19 10:02:00,수진,안녕하세요\n";

        mockMvc.perform(multipart("/api/v1/relationships/{id}/conversation-files", relationship.getId())
                        .file(file("group.csv", "text/csv", groupCsv))
                        .param("source", "KAKAO_TALK")
                        .param("selfParticipantName", "민지")
                        .with(authentication(oauthAuthentication(user)))
                        .with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("GROUP_CHAT_NOT_SUPPORTED"));

        assertThat(fileRepository.findAll()).isEmpty();
        assertThat(messageRepository.findAll()).isEmpty();
    }

    @Test
    void checksRelationshipOwnershipBeforeReadingOrStoringUpload() throws Exception {
        User owner = saveUser("conversation-owner");
        User other = saveUser("conversation-other");
        Relationship relationship = saveRelationship(owner, "소유권");

        mockMvc.perform(multipart("/api/v1/relationships/{id}/conversation-files", relationship.getId())
                        .file(file("chat.csv", "text/csv", csv()))
                        .param("source", "KAKAO_TALK")
                        .param("selfParticipantName", "민지")
                        .with(authentication(oauthAuthentication(other)))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RELATIONSHIP_NOT_FOUND"));

        assertThat(fileRepository.findAll()).isEmpty();
    }

    private void upload(User user, Relationship relationship, MockMultipartFile file) throws Exception {
        mockMvc.perform(multipart("/api/v1/relationships/{id}/conversation-files", relationship.getId())
                        .file(file)
                        .param("source", "KAKAO_TALK")
                        .param("selfParticipantName", "민지")
                        .with(authentication(oauthAuthentication(user)))
                        .with(csrf()))
                .andExpect(status().isCreated());
    }

    private MockMultipartFile file(String name, String contentType, String content) {
        return new MockMultipartFile("file", name, contentType, content.getBytes(StandardCharsets.UTF_8));
    }

    private String csv() {
        return "Date,User,Message\n"
                + "2026-08-19 10:00:00,민지,안녕\n"
                + "2026-08-19 10:01:00,준호,반가워\n";
    }

    private String extendedCsv() {
        return "Date,User,Message\n"
                + "2026-08-19 10:00:00,민지,안녕\n"
                + "2026-08-19 10:01:00,준호,반가워\n"
                + "2026-08-19 10:02:00,민지,추가 메시지\n";
    }

    private String txt() {
        return "2026년 8월 19일 수요일\n"
                + "오전 10:00 민지 안녕\n"
                + "오전 10:01 준호 반가워\n";
    }

    private User saveUser(String subject) {
        return userRepository.save(User.kakao(subject, subject, null));
    }

    private Relationship saveRelationship(User user, String name) {
        return relationshipRepository.save(Relationship.draft(user.getId(), name, RelationshipType.FRIEND));
    }

    private OAuth2AuthenticationToken oauthAuthentication(User user) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var principal = new AppOAuth2User(user.getId(), user.getKakaoSubject(), Map.of(), authorities);
        return new OAuth2AuthenticationToken(principal, authorities, "kakao");
    }
}
