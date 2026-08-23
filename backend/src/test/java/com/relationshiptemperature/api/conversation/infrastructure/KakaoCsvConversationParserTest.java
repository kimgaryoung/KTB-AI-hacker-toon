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

class KakaoCsvConversationParserTest {

    private final KakaoCsvConversationParser parser = new KakaoCsvConversationParser();

    @Test
    void parsesBomCsvWithQuotedFieldsAndEmbeddedNewlines() throws Exception {
        String content = """
                \uFEFFDate,User,Message
                2026-08-19 19:23:00,강명진,\"안녕, 이진우\"
                2026-08-19 19:24:01,이진우,\"그가 말했어 \"\"고마워\"\"\"
                2026-08-19 19:25:02,강명진,\"첫 줄
                둘째 줄"
                2026-08-19 19:26:03,이진우,다음 주에 보자
                2026-08-19 19:27:04,강명진,좋아
                2026-08-19 19:28:05,이진우,응
                """;

        var parsed = parser.parse(input(content), " 강명진 ");

        assertThat(parsed.selfParticipantName()).isEqualTo("강명진");
        assertThat(parsed.otherParticipantName()).isEqualTo("이진우");
        assertThat(parsed.messages()).extracting(
                message -> message.sequenceNumber(),
                message -> message.sentAt(),
                message -> message.senderName(),
                message -> message.role(),
                message -> message.content()
        ).containsExactly(
                org.assertj.core.groups.Tuple.tuple(0, Instant.parse("2026-08-19T10:23:00Z"), "강명진", ConversationParticipantRole.SELF, "안녕, 이진우"),
                org.assertj.core.groups.Tuple.tuple(1, Instant.parse("2026-08-19T10:24:01Z"), "이진우", ConversationParticipantRole.OTHER, "그가 말했어 \"고마워\""),
                org.assertj.core.groups.Tuple.tuple(2, Instant.parse("2026-08-19T10:25:02Z"), "강명진", ConversationParticipantRole.SELF, "첫 줄\n둘째 줄"),
                org.assertj.core.groups.Tuple.tuple(3, Instant.parse("2026-08-19T10:26:03Z"), "이진우", ConversationParticipantRole.OTHER, "다음 주에 보자"),
                org.assertj.core.groups.Tuple.tuple(4, Instant.parse("2026-08-19T10:27:04Z"), "강명진", ConversationParticipantRole.SELF, "좋아"),
                org.assertj.core.groups.Tuple.tuple(5, Instant.parse("2026-08-19T10:28:05Z"), "이진우", ConversationParticipantRole.OTHER, "응")
        );
    }

    @Test
    void skipsCsvRowsWithEmptyMessageContent() throws Exception {
        String content = """
                Date,User,Message
                2026-08-19 19:23:00,강명진,안녕
                2026-08-19 19:24:00,이진우,\"\"
                2026-08-19 19:25:00,이진우,\"   \"
                2026-08-19 19:26:00,이진우,반가워
                """;

        var parsed = parser.parse(input(content), "강명진");

        assertThat(parsed.messages()).extracting(
                message -> message.sequenceNumber(), message -> message.content()
        ).containsExactly(
                org.assertj.core.groups.Tuple.tuple(0, "안녕"),
                org.assertj.core.groups.Tuple.tuple(1, "반가워")
        );
    }

    @Test
    void acceptsTestFixtureWithTheExactSelfMarker() throws Exception {
        String content = """
                Date,User,Message
                2026-08-19 19:23:00,본인,안녕
                2026-08-19 19:24:00,이진우,반가워
                """;

        var parsed = parser.parse(input(content), "강명진", true);

        assertThat(parsed.selfParticipantName()).isEqualTo("본인");
        assertThat(parsed.otherParticipantName()).isEqualTo("이진우");
        assertThat(parsed.messages().getFirst().role()).isEqualTo(ConversationParticipantRole.SELF);
    }

    @Test
    void rejectsCsvWithAnExtraHeaderColumn() {
        assertInvalidCsv("""
                Date,User,Message,Attachment
                2026-08-19 19:23:00,강명진,안녕,
                2026-08-19 19:24:00,이진우,반가워,
                """);
    }

    @Test
    void rejectsCsvWithDuplicateHeaderColumns() {
        assertInvalidCsv("""
                Date,User,Message,Message
                2026-08-19 19:23:00,강명진,안녕,중복
                2026-08-19 19:24:00,이진우,반가워,중복
                """);
    }

    @Test
    void rejectsCsvWithAnExtraRecordField() {
        assertInvalidCsv("""
                Date,User,Message
                2026-08-19 19:23:00,강명진,안녕,여분
                2026-08-19 19:24:00,이진우,반가워
                """);
    }

    @Test
    void skipsTitleRowsBeforeHeader() throws Exception {
        String content = """
                카카오톡 대화 내보내기
                저장한 날짜 : 2026-08-19 20:00:00

                Date,User,Message
                2026-08-19 19:23:00,강명진,안녕
                2026-08-19 19:24:00,이진우,반가워
                """;

        var parsed = parser.parse(input(content), "강명진");

        assertThat(parsed.otherParticipantName()).isEqualTo("이진우");
        assertThat(parsed.messages()).hasSize(2);
        assertThat(parsed.messages().getFirst().sequenceNumber()).isZero();
    }

    @Test
    void skipsTitleRowsThatContainCommas() throws Exception {
        String content = """
                강명진, 이진우 님의 대화,,
                ,,
                Date,User,Message
                2026-08-19 19:23:00,강명진,안녕
                2026-08-19 19:24:00,이진우,반가워
                """;

        var parsed = parser.parse(input(content), "강명진");

        assertThat(parsed.messages()).hasSize(2);
    }

    @Test
    void parsesHeaderOnFirstRowWithoutPreamble() throws Exception {
        String content = """
                Date,User,Message
                2026-08-19 19:23:00,강명진,안녕
                2026-08-19 19:24:00,이진우,반가워
                """;

        assertThat(parser.parse(input(content), "강명진").messages()).hasSize(2);
    }

    @Test
    void rejectsCsvWithoutHeaderRow() {
        assertInvalidCsv("""
                2026-08-19 19:23:00,강명진,안녕
                2026-08-19 19:24:00,이진우,반가워
                """);
    }

    @Test
    void rejectsCsvWhenHeaderAppearsAfterPreambleLimit() {
        StringBuilder content = new StringBuilder();
        for (int index = 0; index < 25; index++) {
            content.append("제목 줄 ").append(index).append('\n');
        }
        content.append("""
                Date,User,Message
                2026-08-19 19:23:00,강명진,안녕
                2026-08-19 19:24:00,이진우,반가워
                """);

        assertInvalidCsv(content.toString());
    }

    @Test
    void skipsRowsWithoutSender() throws Exception {
        // 카카오톡 내보내기는 삭제된 메시지를 Date·User 가 빈 행으로 남긴다.
        String content = """
                Date,User,Message
                2026-08-19 19:23:00,강명진,안녕
                ,,메시지가 삭제되었습니다.
                ,,메시지가 삭제되었습니다.
                2026-08-19 19:24:00,이진우,반가워
                """;

        var parsed = parser.parse(input(content), "강명진");

        assertThat(parsed.otherParticipantName()).isEqualTo("이진우");
        assertThat(parsed.messages()).extracting(
                message -> message.sequenceNumber(),
                message -> message.senderName(),
                message -> message.content()
        ).containsExactly(
                org.assertj.core.groups.Tuple.tuple(0, "강명진", "안녕"),
                org.assertj.core.groups.Tuple.tuple(1, "이진우", "반가워")
        );
    }

    @Test
    void skippedRowsDoNotCountAsThirdParticipant() throws Exception {
        String content = """
                Date,User,Message
                2026-08-19 19:23:00,강명진,안녕
                ,,메시지가 삭제되었습니다.
                2026-08-19 19:24:00,이진우,반가워
                """;

        assertThat(parser.parse(input(content), "강명진").messages()).hasSize(2);
    }

    @Test
    void skipsRowsWhoseSenderIsOnlyWhitespace() throws Exception {
        String content = """
                Date,User,Message
                2026-08-19 19:23:00,강명진,안녕
                2026-08-19 19:23:30,"   ",삭제된 메시지
                2026-08-19 19:24:00,이진우,반가워
                """;

        assertThat(parser.parse(input(content), "강명진").messages()).hasSize(2);
    }

    @Test
    void stillRejectsRowsThatHaveSenderButBrokenDate() {
        assertInvalidCsv("""
                Date,User,Message
                2026-08-19 19:23:00,강명진,안녕
                ,이진우,날짜가 없다
                """);
    }

    @Test
    void routesCsvInputByExtension() throws Exception {
        ConversationParserRouter router = new ConversationParserRouter(
                new KakaoCsvConversationParser(), new BasicKakaoConversationParser()
        );
        String content = """
                Date,User,Message
                2026-08-19 19:23:00,강명진,안녕
                2026-08-19 19:24:00,이진우,반가워
                """;

        var parsed = router.parse("CSV", input(content), "강명진");

        assertThat(parsed.messages()).hasSize(2);
        assertThat(parsed.messages().getFirst().role()).isEqualTo(ConversationParticipantRole.SELF);
    }

    @Test
    void parsesQuotedSampleWithPlaceholderOtherName() throws Exception {
        String content = """
                Date,User,Message
                "2026-08-20 18:00:00","강명진","아까 인스타 스토리에 올린 사진 누구랑 찍은 거야?"
                "2026-08-20 18:02:15","상대방","아, 그거 동아리 남사친이랑 카페 갔을 때 찍은 건데 왜?"
                "2026-08-20 18:03:40","강명진","둘이서만 카페 간 거야? 미리 말도 없이 둘이 만나는 건 좀 아니지 않아?"
                "2026-08-20 18:05:10","상대방","그냥 오랜만에 만난 친구인데 뭘 그렇게 예민하게 굴어?"
                """;

        var parsed = parser.parse(input(content), "강명진");

        assertThat(parsed.selfParticipantName()).isEqualTo("강명진");
        assertThat(parsed.otherParticipantName()).isEqualTo("상대방");
        assertThat(parsed.messages()).hasSize(4);
    }

    @Test
    void ignoresStrayVariationSelectorAfterHangulName() throws Exception {
        String content = """
                Date,User,Message
                2026-08-19 19:23:00,강명진,안녕
                2026-08-19 19:24:00,이진우,반가워
                2026-08-19 19:25:00,이진우\uFE0F,또 왔어
                """;

        var parsed = parser.parse(input(content), "강명진");

        assertThat(parsed.otherParticipantName()).isEqualTo("이진우");
        assertThat(parsed.messages()).hasSize(3);
    }

    private ByteArrayInputStream input(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private void assertInvalidCsv(String content) {
        assertThatThrownBy(() -> parser.parse(input(content), "강명진"))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.INVALID_KAKAO_EXPORT);
    }
}
