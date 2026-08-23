package com.relationshiptemperature.api.conversation.infrastructure;

import com.relationshiptemperature.api.common.error.ApiException;
import com.relationshiptemperature.api.common.error.ErrorCode;
import com.relationshiptemperature.api.conversation.application.KakaoConversationParser;
import com.relationshiptemperature.api.conversation.application.ConversationParseException;
import com.relationshiptemperature.api.conversation.application.ParsedConversation;
import com.relationshiptemperature.api.conversation.domain.ConversationParticipantRole;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

@Component
public class KakaoCsvConversationParser implements KakaoConversationParser {

    private static final ZoneId KAKAO_EXPORT_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .get();
    private static final List<String> HEADER = List.of("Date", "User", "Message");
    // 스프레드시트로 만든 파일은 헤더 앞에 제목·설명 행이 붙는 경우가 많다.
    // 그런 앞머리를 이 줄 수까지 건너뛰고 헤더를 찾는다. 못 찾으면 잘못된 파일로 본다.
    private static final int MAX_PREAMBLE_LINES = 20;
    private static final int MAX_HEADER_LINE_CHARS = 64 * 1024;

    @Override
    public ParsedConversation parse(InputStream inputStream, String selfParticipantName) throws IOException {
        return parse(inputStream, selfParticipantName, false);
    }

    @Override
    public ParsedConversation parse(
            InputStream inputStream,
            String selfParticipantName,
            boolean testFixture
    ) throws IOException {
        try {
            String self = normalizedName(selfParticipantName);
            List<RawMessage> rawMessages = new ArrayList<>();
            try (
                    Reader reader = readerAtHeader(
                            new InputStreamReader(stripUtf8Bom(inputStream), StandardCharsets.UTF_8)
                    );
                    CSVParser csv = CSVParser.parse(reader, CSV_FORMAT)
            ) {
                validateHeader(csv.getHeaderNames());
                for (CSVRecord record : csv) {
                    if (record.size() != 3) {
                        throw invalidExport();
                    }
                    // 카카오톡 내보내기는 삭제된 메시지를 Date·User 가 빈 행으로 남긴다.
                    // 보낸 사람이나 본문이 없는 행은 대화로 볼 수 없으므로 건너뛴다.
                    // 파일 전체를 거절하면 수만 건짜리 업로드가 한 행 때문에 실패한다.
                    if (isBlank(record.get("User")) || isBlank(record.get("Message"))) {
                        continue;
                    }
                    rawMessages.add(new RawMessage(
                            parseSentAt(record.get("Date")),
                            normalizedName(record.get("User")),
                            record.get("Message")
                    ));
                }
            }
            return parsedConversation(rawMessages, self, testFixture);
        } catch (ApiException exception) {
            throw exception;
        } catch (DateTimeException | IllegalArgumentException exception) {
            throw invalidExport();
        }
    }

    private InputStream stripUtf8Bom(InputStream inputStream) throws IOException {
        PushbackInputStream stream = new PushbackInputStream(new BufferedInputStream(inputStream), 3);
        byte[] bytes = stream.readNBytes(3);
        if (bytes.length == 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            return stream;
        }
        stream.unread(bytes);
        return stream;
    }

    /**
     * 헤더 행(Date,User,Message)이 나올 때까지 앞머리를 건너뛴 Reader 를 돌려준다.
     * 헤더가 첫 줄이면 아무것도 건너뛰지 않는다.
     */
    private Reader readerAtHeader(Reader source) throws IOException {
        BufferedReader buffered = new BufferedReader(source);
        for (int index = 0; index < MAX_PREAMBLE_LINES; index++) {
            buffered.mark(MAX_HEADER_LINE_CHARS);
            String line = buffered.readLine();
            if (line == null) {
                throw invalidExport();
            }
            if (isHeaderLine(line)) {
                buffered.reset();
                return buffered;
            }
        }
        throw invalidExport();
    }

    private boolean isHeaderLine(String line) {
        if (line.isBlank()) {
            return false;
        }
        try (CSVParser probe = CSVParser.parse(new StringReader(line), CSVFormat.DEFAULT)) {
            for (CSVRecord record : probe) {
                return record.size() == HEADER.size()
                        && HEADER.equals(List.of(record.get(0), record.get(1), record.get(2)));
            }
            return false;
        } catch (IOException | IllegalArgumentException exception) {
            return false;
        }
    }

    private void validateHeader(List<String> headerNames) {
        if (!headerNames.equals(HEADER)) {
            throw invalidExport();
        }
    }

    private Instant parseSentAt(String value) {
        return LocalDateTime.parse(value, DATE_FORMAT).atZone(KAKAO_EXPORT_ZONE).toInstant();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ParsedConversation parsedConversation(
            List<RawMessage> rawMessages,
            String requestedSelfParticipantName,
            boolean testFixture
    ) {
        Set<String> participants = new LinkedHashSet<>();
        for (RawMessage message : rawMessages) {
            participants.add(message.senderName());
        }
        if (participants.size() > 2) {
            throw new ConversationParseException(ErrorCode.GROUP_CHAT_NOT_SUPPORTED);
        }
        if (participants.size() != 2) {
            throw new ConversationParseException(ErrorCode.SELF_PARTICIPANT_MISMATCH);
        }
        String selfParticipantName = resolveSelfParticipantName(
                participants, requestedSelfParticipantName, testFixture
        );
        String otherParticipantName = participants.stream()
                .filter(name -> !name.equals(selfParticipantName))
                .findFirst()
                .orElseThrow(KakaoCsvConversationParser::invalidExport);
        List<ParsedConversation.ParsedMessage> messages = new ArrayList<>();
        for (int sequenceNumber = 0; sequenceNumber < rawMessages.size(); sequenceNumber++) {
            RawMessage message = rawMessages.get(sequenceNumber);
            ConversationParticipantRole role = message.senderName().equals(selfParticipantName)
                    ? ConversationParticipantRole.SELF
                    : ConversationParticipantRole.OTHER;
            messages.add(new ParsedConversation.ParsedMessage(
                    sequenceNumber, message.sentAt(), message.senderName(), role, message.content()
            ));
        }
        return new ParsedConversation(messages, selfParticipantName, otherParticipantName);
    }

    private String resolveSelfParticipantName(
            Set<String> participants,
            String requestedSelfParticipantName,
            boolean testFixture
    ) {
        if (participants.contains(requestedSelfParticipantName)) {
            return requestedSelfParticipantName;
        }
        if (!testFixture) {
            if (!participants.contains("본인")) {
                throw new ConversationParseException(ErrorCode.SELF_PARTICIPANT_MISMATCH);
            }
        }
        return participants.contains("본인") ? "본인" : participants.iterator().next();
    }

    private static final Pattern INVISIBLE = Pattern.compile("[\\u200B-\\u200D\\uFEFF]");
    /** 한글·라틴 문자 뒤에 홀로 남은 변이 선택자. 이모지 뒤의 것은 건드리지 않는다. */
    private static final Pattern ORPHAN_VARIATION_SELECTOR =
            Pattern.compile("(?<=[\\p{IsHangul}\\p{IsLatin}])[\\uFE0E\\uFE0F]");

    /** 이름 비교가 보이지 않는 문자 때문에 어긋나지 않도록 정규화한다. */
    private static String normalizedName(String value) {
        if (isBlank(value)) {
            throw invalidExport();
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        normalized = INVISIBLE.matcher(normalized).replaceAll("");
        normalized = ORPHAN_VARIATION_SELECTOR.matcher(normalized).replaceAll("").trim();
        if (normalized.isEmpty()) {
            throw invalidExport();
        }
        return normalized;
    }

    private static ApiException invalidExport() {
        return new ApiException(ErrorCode.INVALID_KAKAO_EXPORT);
    }

    private record RawMessage(Instant sentAt, String senderName, String content) {}
}
