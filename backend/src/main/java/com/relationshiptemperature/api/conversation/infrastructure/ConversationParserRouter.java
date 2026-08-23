package com.relationshiptemperature.api.conversation.infrastructure;

import com.relationshiptemperature.api.common.error.ApiException;
import com.relationshiptemperature.api.common.error.ErrorCode;
import com.relationshiptemperature.api.conversation.application.ParsedConversation;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ConversationParserRouter {

    private final KakaoCsvConversationParser csvParser;
    private final BasicKakaoConversationParser txtParser;

    public ConversationParserRouter(KakaoCsvConversationParser csvParser, BasicKakaoConversationParser txtParser) {
        this.csvParser = csvParser;
        this.txtParser = txtParser;
    }

    public ParsedConversation parse(String extension, InputStream inputStream, String selfParticipantName) throws IOException {
        return parse(extension, inputStream, selfParticipantName, false);
    }

    public ParsedConversation parse(
            String extension,
            InputStream inputStream,
            String selfParticipantName,
            boolean testFixture
    ) throws IOException {
        return switch (extension == null ? "" : extension.trim().toLowerCase(Locale.ROOT)) {
            case "csv" -> csvParser.parse(inputStream, selfParticipantName, testFixture);
            case "txt" -> txtParser.parse(inputStream, selfParticipantName, testFixture);
            default -> throw new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        };
    }
}
