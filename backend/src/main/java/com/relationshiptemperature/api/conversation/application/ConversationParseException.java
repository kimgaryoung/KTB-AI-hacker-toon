package com.relationshiptemperature.api.conversation.application;

import com.relationshiptemperature.api.common.error.ApiException;
import com.relationshiptemperature.api.common.error.ErrorCode;

/** Internal parse detail retained for upload orchestration without changing the parser's public invalid-export contract. */
public final class ConversationParseException extends ApiException {

    private final ErrorCode semanticCode;

    public ConversationParseException(ErrorCode semanticCode) {
        super(ErrorCode.INVALID_KAKAO_EXPORT);
        this.semanticCode = semanticCode;
    }

    public ErrorCode semanticCode() {
        return semanticCode;
    }
}
