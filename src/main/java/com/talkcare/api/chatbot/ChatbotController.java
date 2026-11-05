package com.talkcare.api.chatbot;

import com.talkcare.api.domain.Permission;
import com.talkcare.api.domain.account.Account;
import java.time.Instant;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** TalkCare Chatbot for clients: {@code /api/chatbot}. */
@RestController
@RequestMapping("/api/chatbot")
@PreAuthorize("hasAuthority('" + Permission.CONVERSATIONS_CREATE + "')")
public class ChatbotController {

    public record SendRequest(String message) {
    }

    public record MessageView(String senderRole, String messageText, Instant createdAt) {
        static MessageView from(ChatTurn turn) {
            return new MessageView(turn.fromUser() ? "CLIENT" : "BOT", turn.text(), turn.createdAt());
        }
    }

    public record ChatbotResponse(String greeting, List<MessageView> messages) {
        static ChatbotResponse of(List<ChatTurn> turns) {
            return new ChatbotResponse(ChatbotService.GREETING, turns.stream().map(MessageView::from).toList());
        }
    }

    private final ChatbotService chatbot;

    public ChatbotController(ChatbotService chatbot) {
        this.chatbot = chatbot;
    }

    @GetMapping("/messages")
    public ChatbotResponse messages(@AuthenticationPrincipal Account actor) {
        return ChatbotResponse.of(chatbot.history(actor));
    }

    /** Sends a message; answers with the whole conversation including the bot's reply. */
    @PostMapping("/messages")
    public ChatbotResponse send(@AuthenticationPrincipal Account actor, @RequestBody SendRequest request) {
        return ChatbotResponse.of(chatbot.send(actor, request == null ? null : request.message()));
    }

    /** Starts a new conversation. */
    @PostMapping("/reset")
    public ChatbotResponse reset(@AuthenticationPrincipal Account actor) {
        chatbot.clear(actor);
        return ChatbotResponse.of(List.of());
    }
}
