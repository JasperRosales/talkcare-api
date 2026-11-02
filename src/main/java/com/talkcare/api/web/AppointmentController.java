package com.talkcare.api.web;

import com.talkcare.api.domain.account.Account;
import com.talkcare.api.service.AppointmentService;
import com.talkcare.api.service.InputRules;
import com.talkcare.api.web.dto.AppointmentResponse;
import com.talkcare.api.web.dto.BookAppointmentRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Client sessions with their assigned professional. */
@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointments;

    public AppointmentController(AppointmentService appointments) {
        this.appointments = appointments;
    }

    @GetMapping
    public List<AppointmentResponse> list(@AuthenticationPrincipal Account actor,
                                          @RequestParam(required = false) String date) {
        return appointments.list(actor, date).stream().map(AppointmentResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse book(@AuthenticationPrincipal Account actor, @Valid @RequestBody BookAppointmentRequest request) {
        return AppointmentResponse.from(appointments.book(actor, request));
    }

    /** Body: {@code {"status": "CONFIRMED" | "COMPLETED" | "CANCELLED" | "NO_SHOW"}}. */
    @PatchMapping("/{id}/status")
    public AppointmentResponse updateStatus(@AuthenticationPrincipal Account actor, @PathVariable String id,
                                            @RequestBody Map<String, String> body) {
        return AppointmentResponse.from(appointments.updateStatus(actor, InputRules.positiveId(id, "session id"),
                body == null ? null : body.get("status")));
    }
}
