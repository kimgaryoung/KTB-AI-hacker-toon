package com.relationshiptemperature.api.conversation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.relationshiptemperature.api.common.error.ApiException;
import com.relationshiptemperature.api.common.error.ErrorCode;
import com.relationshiptemperature.api.conversation.domain.ConversationParticipantRole;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BasicKakaoConversationParserTest {

    private final BasicKakaoConversationParser parser = new BasicKakaoConversationParser();

    @Test
    void parsesDatedKakaoTxtMessagesAndPreservesContinuationLines() throws Exception {
        String content = """
                2026년 8월 19일 수요일
                오후 7:23 강명진 사진 txt파일은 이거야
                두 번째 줄도 같은 메시지야
                오후 7:24 이진우 확인했어
                """;

        var parsed = parser.parse(input(content), "  강명진  ");

        assertThat(parsed.selfParticipantName()).isEqualTo("강명진");
        assertThat(parsed.otherParticipantName()).isEqualTo("이진우");
        assertThat(parsed.messages()).extracting(
                message -> message.sequenceNumber(),
                message -> message.sentAt(),
                message -> message.senderName(),
                message -> message.role(),
                message -> message.content()
        ).containsExactly(
                org.assertj.core.groups.Tuple.tuple(
                        0, Instant.parse("2026-08-19T10:23:00Z"), "강명진",
                        ConversationParticipantRole.SELF, "사진 txt파일은 이거야\n두 번째 줄도 같은 메시지야"
                ),
                org.assertj.core.groups.Tuple.tuple(
                        1, Instant.parse("2026-08-19T10:24:00Z"), "이진우",
                        ConversationParticipantRole.OTHER, "확인했어"
                )
        );
    }

    @Test
    void preservesContinuationLineBeginningWithKoreanMeridiem() throws Exception {
        String content = """
                2026년 8월 19일 수요일
                오후 7:23 강명진 첫 줄
                오후 이 문장은 새 메시지 헤더가 아니야
                오후 7:24 이진우 확인했어
                """;

        var parsed = parser.parse(input(content), "강명진");

        assertThat(parsed.messages().getFirst().content())
                .isEqualTo("첫 줄\n오후 이 문장은 새 메시지 헤더가 아니야");
    }

    @Test
    void preservesContentLeadingWhitespaceAfterTheSenderDelimiter() throws Exception {
        String content = """
                2026년 8월 19일 수요일
                오후 7:23 강명진  첫 글자 앞 공백
                오후 7:24 이진우 확인했어
                """;

        var parsed = parser.parse(input(content), "강명진");

        assertThat(parsed.messages().getFirst().content()).isEqualTo(" 첫 글자 앞 공백");
    }

    @Test
    void rejectsTxtConversationWhenSelfParticipantIsAbsent() {
        assertInvalid("""
                2026년 8월 19일 수요일
                오후 7:23 강명진 안녕
                오후 7:24 이진우 반가워
                """, "박서준");
    }

    @Test
    void acceptsTestFixtureWithTheExactSelfMarker() throws Exception {
        String content = """
                2026년 8월 19일 수요일
                오후 7:23 본인 안녕
                오후 7:24 이진우 반가워
                """;

        var parsed = parser.parse(input(content), "강명진", true);

        assertThat(parsed.selfParticipantName()).isEqualTo("본인");
        assertThat(parsed.otherParticipantName()).isEqualTo("이진우");
        assertThat(parsed.messages().getFirst().role()).isEqualTo(ConversationParticipantRole.SELF);
    }

    @Test
    void rejectsTxtGroupConversation() {
        assertInvalid("""
                2026년 8월 19일 수요일
                오후 7:23 강명진 안녕
                오후 7:24 이진우 반가워
                오후 7:25 박서준 같이 보자
                """, "강명진");
    }

    @Test
    void rejectsTxtWithInvalidDateHeading() {
        assertInvalid("""
                2026년 2월 30일 월요일
                오후 7:23 강명진 안녕
                오후 7:24 이진우 반가워
                """, "강명진");
    }

    @Test
    void rejectsTxtMessageWithEmptyContent() {
        assertInvalid("""
                2026년 8월 19일 수요일
                오후 7:23 강명진
                오후 7:24 이진우 반가워
                """, "강명진");
    }

    private ByteArrayInputStream input(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesAndroidMobileExport() throws Exception {
        String content = """
                미뉴 님과 카카오톡 대화
                저장한 날짜 : 2026년 8월 20일 오전 11:25

                2023년 6월 23일 오후 5:20
                2023년 6월 23일 오후 5:20, 미뉴 : 나아씻고옴
                2023년 6월 23일 오후 5:21, 김가령 : 앗 오케이
                """;

        var parsed = parser.parse(input(content), "김가령");

        assertThat(parsed.otherParticipantName()).isEqualTo("미뉴");
        assertThat(parsed.messages()).extracting(
                message -> message.senderName(), message -> message.content()
        ).containsExactly(
                org.assertj.core.groups.Tuple.tuple("미뉴", "나아씻고옴"),
                org.assertj.core.groups.Tuple.tuple("김가령", "앗 오케이")
        );
        assertThat(parsed.messages().getFirst().sentAt())
                .isEqualTo(java.time.Instant.parse("2023-06-23T08:20:00Z"));
    }

    @Test
    void parsesIosMobileExportWith24HourClock() throws Exception {
        String content = """
                Talk_2026.6.12 13:10-1.txt
                저장한 날짜 : 2026. 6. 12. 13:13


                2023년 4월 13일 목요일
                2023. 4. 13. 10:35, 채영 : 나 몸이 안좋아서
                2023. 4. 13. 10:41, 김가령 : 괜찮아?
                """;

        var parsed = parser.parse(input(content), "김가령");

        assertThat(parsed.otherParticipantName()).isEqualTo("채영");
        assertThat(parsed.messages().getFirst().sentAt())
                .isEqualTo(java.time.Instant.parse("2023-04-13T01:35:00Z"));
    }

    @Test
    void parsesIosMobileExportWith12HourClock() throws Exception {
        String content = """
                Talk_2026.6.12 13:10-1.txt
                저장한 날짜 : 2026. 6. 12. 13:13

                2023. 4. 13. 오후 1:35, 채영 : 안녕
                2023. 4. 13. 오전 12:05, 김가령 : 자정이야
                """;

        var parsed = parser.parse(input(content), "김가령");

        assertThat(parsed.messages().get(0).sentAt())
                .isEqualTo(java.time.Instant.parse("2023-04-13T04:35:00Z"));
        assertThat(parsed.messages().get(1).sentAt())
                .isEqualTo(java.time.Instant.parse("2023-04-12T15:05:00Z"));
    }

    @Test
    void keepsBlankLinesInsideMultilineMobileMessage() throws Exception {
        // 실제 Android 내보내기에서 선물 안내문은 중간에 빈 줄을 포함한 여러 줄이다.
        String content = """
                미뉴 님과 카카오톡 대화

                2024년 1월 9일 오후 12:08, 김가령 : 함께 온 메시지
                생일 축하해

                1월 16일 까지 배송지를 입력해주세요.
                (배송 주소는 나만 볼 수 있어요.)
                2024년 1월 9일 오후 3:06, 미뉴 : 고마워
                """;

        var parsed = parser.parse(input(content), "김가령");

        assertThat(parsed.messages()).hasSize(2);
        assertThat(parsed.messages().getFirst().content()).isEqualTo(
                "함께 온 메시지\n생일 축하해\n\n1월 16일 까지 배송지를 입력해주세요.\n(배송 주소는 나만 볼 수 있어요.)"
        );
    }

    @Test
    void handlesBomAndCrlfFromAndroidExport() throws Exception {
        String content = "\uFEFF미뉴 님과 카카오톡 대화\r\n"
                + "저장한 날짜 : 2026년 8월 20일 오전 11:25\r\n"
                + "\r\n"
                + "2023년 6월 23일 오후 5:20\r\n"
                + "2023년 6월 23일 오후 5:20, 미뉴 : 나아씻고옴\r\n"
                + "2023년 6월 23일 오후 5:21, 김가령 : 앗 오케이\r\n";

        var parsed = parser.parse(input(content), "김가령");

        assertThat(parsed.messages()).hasSize(2);
        assertThat(parsed.otherParticipantName()).isEqualTo("미뉴");
        assertThat(parsed.messages().getFirst().content()).isEqualTo("나아씻고옴");
    }

    @Test
    void doesNotValidateDayOfWeekInDateHeading() throws Exception {
        // 요일이 실제와 달라도 통과한다 (Android 내보내기에는 요일 자체가 없다)
        String content = """
                2026년 8월 19일 금요일
                오후 7:23 강명진 안녕
                오후 7:24 이진우 반가워
                """;

        assertThat(parser.parse(input(content), "강명진").messages()).hasSize(2);
    }

    @Test
    void keepsContentLineThatStartsWithYearButIsNotADateHeading() throws Exception {
        String content = """
                2026년 8월 19일 수요일
                오후 7:23 강명진 첫 줄
                2023년에 우리 처음 만났잖아
                오후 7:24 이진우 맞아
                """;

        var parsed = parser.parse(input(content), "강명진");

        assertThat(parsed.messages()).hasSize(2);
        assertThat(parsed.messages().getFirst().content())
                .isEqualTo("첫 줄\n2023년에 우리 처음 만났잖아");
    }

    @Test
    void ignoresStrayVariationSelectorAfterHangulName() throws Exception {
        // 실제 iOS 내보내기에 "김가령" 뒤에 U+FE0F 만 남는 경우가 있다.
        // 눈에 보이지 않아 같은 사람이 두 명으로 집계된다.
        String content = """
                Talk_2026.6.12 13:10-1.txt
                저장한 날짜 : 2026. 6. 12. 13:13

                2023년 4월 13일 목요일
                2023. 4. 13. 10:35, 자걸녀 : 나 몸이 안좋아
                2023. 4. 13. 10:51, 김가령 : 괜찮아?
                2023. 4. 13. 10:58, 김가령\uFE0F : 밥 뭐 먹징
                """;

        var parsed = parser.parse(input(content), "김가령");

        assertThat(parsed.otherParticipantName()).isEqualTo("자걸녀");
        assertThat(parsed.messages()).hasSize(3);
        assertThat(parsed.messages()).extracting(message -> message.senderName())
                .containsExactly("자걸녀", "김가령", "김가령");
    }

    @Test
    void keepsVariationSelectorThatBelongsToAnEmojiName() throws Exception {
        // "왕왕❤️" 의 U+FE0F 는 ❤ 의 일부이므로 지우면 안 된다.
        String content = """
                2023. 4. 13. 10:35, 왕왕\u2764\uFE0F : 안녕
                2023. 4. 13. 10:51, 김가령 : 응
                """;

        var parsed = parser.parse(input(content), "김가령");

        assertThat(parsed.otherParticipantName()).isEqualTo("왕왕\u2764\uFE0F");
    }

    @Test
    void skipsMobileMessageWithEmptyContent() throws Exception {
        String content = """
                미뉴 님과 카카오톡 대화

                2023년 6월 23일 오후 5:20, 미뉴 : 나아씻고옴
                2023년 6월 23일 오후 5:21, 김가령 : 
                2023년 6월 23일 오후 5:22, 김가령 : 앗 오케이
                """;

        var parsed = parser.parse(input(content), "김가령");

        assertThat(parsed.messages()).extracting(
                message -> message.sequenceNumber(), message -> message.content()
        ).containsExactly(
                org.assertj.core.groups.Tuple.tuple(0, "나아씻고옴"),
                org.assertj.core.groups.Tuple.tuple(1, "앗 오케이")
        );
    }

    @Test
    void skippedEmptyMessageDoesNotRemoveParticipant() throws Exception {
        // 빈 메시지를 건너뛰어 참여자가 1명이 되면 SELF_PARTICIPANT_MISMATCH 가 난다
        String content = """
                2023년 6월 23일 오후 5:20, 미뉴 : 안녕
                2023년 6월 23일 오후 5:21, 김가령 : 
                """;

        assertThatThrownBy(() -> parser.parse(input(content), "김가령"))
                .isInstanceOf(com.relationshiptemperature.api.conversation.application.ConversationParseException.class)
                .extracting(exception -> ((com.relationshiptemperature.api.conversation.application
                        .ConversationParseException) exception).semanticCode())
                .isEqualTo(ErrorCode.SELF_PARTICIPANT_MISMATCH);
    }

    private void assertInvalid(String content, String selfParticipantName) {
        assertThatThrownBy(() -> parser.parse(input(content), selfParticipantName))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.INVALID_KAKAO_EXPORT);
    }
}
