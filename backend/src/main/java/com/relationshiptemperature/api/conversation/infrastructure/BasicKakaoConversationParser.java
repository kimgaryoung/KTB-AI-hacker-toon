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
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 카카오톡 대화 내보내기 txt 를 읽는다. 다음 세 형식을 모두 받는다.
 *
 * <pre>
 * Android : 2023년 6월 23일 오후 5:20, 미뉴 : 나아씻고옴
 * iOS     : 2023. 4. 13. 10:35, 채영 : 안녕          (12/24시간제 모두)
 * 단순형  : 오후 7:23 강명진 안녕                     (날짜 제목 줄이 앞에 필요)
 * </pre>
 *
 * 파일 앞머리(제목, "저장한 날짜 : ...")는 첫 메시지 줄이 나올 때까지 건너뛴다.
 * 메시지는 다음 메시지 줄이나 날짜 구분 줄을 만날 때까지 이어지며, 그 사이의 빈 줄은
 * 본문의 일부로 남긴다.
 */
@Component
@Primary
public class BasicKakaoConversationParser implements KakaoConversationParser {

    private static final ZoneId KAKAO_EXPORT_ZONE = ZoneId.of("Asia/Seoul");

    /** Android: 2023년 6월 23일 오후 5:20, 이름 : 내용 */
    private static final Pattern ANDROID_MESSAGE = Pattern.compile(
            "^(\\d{4})년 (\\d{1,2})월 (\\d{1,2})일 (?:(오전|오후) )?(\\d{1,2}):(\\d{2}), (.+?) :(?: (.*))?$"
    );
    /** iOS: 2023. 4. 13. 10:35, 이름 : 내용 */
    private static final Pattern IOS_MESSAGE = Pattern.compile(
            "^(\\d{4})\\. (\\d{1,2})\\. (\\d{1,2})\\. (?:(오전|오후) )?(\\d{1,2}):(\\d{2}), (.+?) :(?: (.*))?$"
    );
    /** 단순형: 오후 7:23 이름 내용 (앞선 날짜 제목 줄의 날짜를 쓴다) */
    private static final Pattern SIMPLE_MESSAGE = Pattern.compile(
            "^(오전|오후)\\s+(\\d{1,2}):(\\d{2})\\s+(\\S+)\\s(.*)$"
    );
    /** 날짜 제목 줄: 2026년 8월 19일 수요일 (요일은 검증하지 않는다) */
    private static final Pattern DATE_HEADING = Pattern.compile(
            "^(\\d{4})년\\s+(\\d{1,2})월\\s+(\\d{1,2})일\\s+(\\S+)$"
    );
    /** Android 날짜 구분 줄: 2023년 6월 23일 오후 5:20 */
    private static final Pattern ANDROID_DATE_SEPARATOR = Pattern.compile(
            "^(\\d{4})년 (\\d{1,2})월 (\\d{1,2})일 (?:(오전|오후) )?(\\d{1,2}):(\\d{2})$"
    );

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
            LocalDate headingDate = null;
            PendingMessage pending = null;
            boolean started = false;

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stripUtf8Bom(inputStream), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    PendingMessage message = matchMessage(line, headingDate);
                    if (message != null) {
                        addPending(rawMessages, pending);
                        pending = message;
                        started = true;
                        continue;
                    }

                    LocalDate heading = matchDateLine(line);
                    if (heading != null) {
                        addPending(rawMessages, pending);
                        pending = null;
                        headingDate = heading;
                        started = true;
                        continue;
                    }

                    if (!started) {
                        // 파일 앞머리(제목, "저장한 날짜 : ...")는 버린다.
                        continue;
                    }
                    if (pending == null) {
                        if (!line.isBlank()) {
                            throw invalidExport();
                        }
                        continue;
                    }
                    // 다음 메시지를 만나기 전까지는 빈 줄도 본문의 일부다.
                    pending.content().append('\n').append(line);
                }
            }
            addPending(rawMessages, pending);
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

    private PendingMessage matchMessage(String line, LocalDate headingDate) {
        Matcher android = ANDROID_MESSAGE.matcher(line);
        if (android.matches()) {
            LocalDate date = LocalDate.of(
                    Integer.parseInt(android.group(1)),
                    Integer.parseInt(android.group(2)),
                    Integer.parseInt(android.group(3))
            );
            return pending(date, android.group(4), android.group(5), android.group(6),
                    android.group(7), android.group(8));
        }
        Matcher ios = IOS_MESSAGE.matcher(line);
        if (ios.matches()) {
            LocalDate date = LocalDate.of(
                    Integer.parseInt(ios.group(1)),
                    Integer.parseInt(ios.group(2)),
                    Integer.parseInt(ios.group(3))
            );
            return pending(date, ios.group(4), ios.group(5), ios.group(6), ios.group(7), ios.group(8));
        }
        Matcher simple = SIMPLE_MESSAGE.matcher(line);
        if (simple.matches()) {
            if (headingDate == null) {
                throw invalidExport();
            }
            return pending(headingDate, simple.group(1), simple.group(2), simple.group(3),
                    simple.group(4), simple.group(5));
        }
        return null;
    }

    private PendingMessage pending(
            LocalDate date, String meridiem, String hour, String minute, String sender, String content
    ) {
        return new PendingMessage(
                sentAt(date, meridiem, Integer.parseInt(hour), Integer.parseInt(minute)),
                normalizedName(sender),
                // "이름 :" 처럼 본문이 아예 없는 줄은 group 이 null 이다.
                new StringBuilder(content == null ? "" : content)
        );
    }

    private LocalDate matchDateLine(String line) {
        Matcher separator = ANDROID_DATE_SEPARATOR.matcher(line);
        if (separator.matches()) {
            return LocalDate.of(
                    Integer.parseInt(separator.group(1)),
                    Integer.parseInt(separator.group(2)),
                    Integer.parseInt(separator.group(3))
            );
        }
        Matcher heading = DATE_HEADING.matcher(line);
        if (heading.matches()) {
            return LocalDate.of(
                    Integer.parseInt(heading.group(1)),
                    Integer.parseInt(heading.group(2)),
                    Integer.parseInt(heading.group(3))
            );
        }
        return null;
    }

    /** meridiem 이 null 이면 24시간제로 본다. */
    private Instant sentAt(LocalDate date, String meridiem, int hour, int minute) {
        if (minute > 59) {
            throw invalidExport();
        }
        int resolvedHour;
        if (meridiem == null) {
            if (hour > 23) {
                throw invalidExport();
            }
            resolvedHour = hour;
        } else {
            if (hour < 1 || hour > 12) {
                throw invalidExport();
            }
            resolvedHour = "오전".equals(meridiem)
                    ? (hour == 12 ? 0 : hour)
                    : (hour == 12 ? 12 : hour + 12);
        }
        return LocalDateTime.of(date.getYear(), date.getMonthValue(), date.getDayOfMonth(), resolvedHour, minute)
                .atZone(KAKAO_EXPORT_ZONE)
                .toInstant();
    }

    private void addPending(List<RawMessage> messages, PendingMessage pending) {
        if (pending == null) {
            return;
        }
        // 다음 메시지 앞에 붙은 빈 줄은 본문 끝에 개행만 남기므로 잘라낸다.
        String content = pending.content().toString().stripTrailing();
        if (content.isBlank()) {
            // 본문이 비어 있는 메시지는 대화로 쓸 수 없다. 파일 전체를 거절하는 대신
            // 그 메시지만 건너뛴다. (CSV 파서의 빈 행 처리와 같은 정책)
            return;
        }
        messages.add(new RawMessage(pending.sentAt(), pending.senderName(), content));
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
                .orElseThrow(BasicKakaoConversationParser::invalidExport);
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
    /**
     * 한글·라틴 문자 뒤에 홀로 남은 변이 선택자(U+FE0E/U+FE0F). iOS 내보내기에서
     * "김가령" 뒤에 U+FE0F 만 남는 사례가 있는데, 눈에 보이지 않아 같은 사람이
     * 다른 이름으로 집계된다. 이모지 뒤(예: "❤️")의 변이 선택자는 건드리지 않는다.
     */
    private static final Pattern ORPHAN_VARIATION_SELECTOR =
            Pattern.compile("(?<=[\\p{IsHangul}\\p{IsLatin}])[\\uFE0E\\uFE0F]");

    /** 이름 비교가 보이지 않는 문자 때문에 어긋나지 않도록 정규화한다. */
    private static String normalizedName(String value) {
        if (value == null || value.isBlank()) {
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

    private record PendingMessage(Instant sentAt, String senderName, StringBuilder content) {}
}
