package com.talkcare.api.web.dto;

import jakarta.validation.constraints.Size;

/** {@code attachmentData} is an optional {@code data:image/...;base64,...} photo. */
public record SendMessageRequest(@Size(max = 5000, message = Inputs.TOO_LONG) String messageText, String attachmentData) {

    public SendMessageRequest {
        messageText = Inputs.trimToNull(messageText);
        attachmentData = attachmentData == null || attachmentData.isEmpty() ? null : attachmentData;
    }
}
