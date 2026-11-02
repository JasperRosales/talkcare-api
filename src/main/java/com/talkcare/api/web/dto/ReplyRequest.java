package com.talkcare.api.web.dto;

import jakarta.validation.constraints.Size;

public record ReplyRequest(@Size(max = 5000, message = Inputs.TOO_LONG) String messageText) {

    public ReplyRequest {
        messageText = Inputs.trimToNull(messageText);
    }
}
