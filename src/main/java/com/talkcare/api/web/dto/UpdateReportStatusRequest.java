package com.talkcare.api.web.dto;

import jakarta.validation.constraints.Size;

/** {@code status} accepts codes or labels ("IN_PROGRESS", "In Progress"). */
public record UpdateReportStatusRequest(String status, @Size(max = 2000, message = Inputs.TOO_LONG) String remarks) {

    public UpdateReportStatusRequest {
        remarks = Inputs.trimToNull(remarks);
    }
}
