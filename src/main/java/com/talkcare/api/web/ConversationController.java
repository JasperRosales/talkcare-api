package com.talkcare.api.web;

import com.talkcare.api.domain.Permission;
import com.talkcare.api.domain.account.Account;
import com.talkcare.api.service.ChatService;
import com.talkcare.api.web.dto.ConversationResponse;
import com.talkcare.api.web.dto.MessageResponse;
import com.talkcare.api.web.dto.OpenConversationRequest;
import com.talkcare.api.web.dto.ReplyRequest;
import com.talkcare.api.web.dto.SendMessageRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ChatService chat;

    public ConversationController(ChatService chat) {
        this.chat = chat;
    }

    /** Gets or opens the client's conversation for {@code serviceType}. */
    @PostMapping
    @PreAuthorize("hasAuthority('" + Permission.CONVERSATIONS_CREATE + "')")
    public ConversationResponse open(@AuthenticationPrincipal Account actor, @RequestBody OpenConversationRequest request) {
        return ConversationResponse.from(chat.openConversation(actor, request.serviceType()));
    }

    @GetMapping("/assigned")
    @PreAuthorize("hasAuthority('" + Permission.CONVERSATIONS_READ_ASSIGNED + "')")
    public List<ConversationResponse> assigned(@AuthenticationPrincipal Account actor) {
        return chat.listAssigned(actor).stream().map(ConversationResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ConversationResponse get(@AuthenticationPrincipal Account actor, @PathVariable String id) {
        return ConversationResponse.from(chat.getConversation(actor, id));
    }

    @GetMapping("/{id}/messages")
    public List<MessageResponse> messages(@AuthenticationPrincipal Account actor, @PathVariable String id) {
        return chat.listMessages(actor, id).stream().map(MessageResponse::from).toList();
    }

    /** Client message; returns it together with the automatic acknowledgement. */
    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permission.MESSAGES_SEND + "')")
    public List<MessageResponse> send(@AuthenticationPrincipal Account actor, @PathVariable String id,
                                      @Valid @RequestBody SendMessageRequest request) {
        return chat.sendClientMessage(actor, id, request).stream().map(MessageResponse::from).toList();
    }

    @PostMapping("/{id}/replies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permission.MESSAGES_REPLY + "')")
    public MessageResponse reply(@AuthenticationPrincipal Account actor, @PathVariable String id,
                                 @Valid @RequestBody ReplyRequest request) {
        return MessageResponse.from(chat.sendProfessionalReply(actor, id, request.messageText()));
    }
}
