package com.talkcare.api.chatbot;

import com.talkcare.api.domain.account.Account;
import com.talkcare.api.error.TooManyRequestsException;
import com.talkcare.api.error.ValidationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * The TalkCare Chatbot. Each client's conversation is kept in memory on the server (it is not
 * stored in the database and is cleared on logout or after two idle hours). Every message passes
 * {@link ChatGuardrails} before and after Gemini, and per-account and global rate limits protect
 * the Gemini quota.
 */
@Service
public class ChatbotService {

    public static final String GREETING = """
            Hi, I'm the TalkCare Chatbot. I can listen, share coping tips, explain your rights in general \
            terms, and help you use TalkCare. I'm not a lawyer or therapist; for that, message JusticeCare \
            or ThoMatt. If you're in danger, call 911 or the NCMH Crisis Hotline 1553.""";

    /** Past messages sent to Gemini as context, and the most kept per conversation. */
    private static final int CONTEXT_TURNS = 12;
    private static final int MAX_STORED_TURNS = 60;
    private static final Duration MIN_INTERVAL = Duration.ofSeconds(3);
    private static final Duration IDLE_EXPIRY = Duration.ofHours(2);
    private static final int PER_ACCOUNT_PER_HOUR = 40;
    private static final int GLOBAL_PER_MINUTE = 60;

    private final ChatGuardrails guardrails;
    private final GeminiClient gemini;
    private final Clock clock = Clock.systemUTC();
    private final Map<Integer, Conversation> conversations = new ConcurrentHashMap<>();
    private final Deque<Instant> globalCalls = new ArrayDeque<>();

    public ChatbotService(ChatGuardrails guardrails, GeminiClient gemini) {
        this.guardrails = guardrails;
        this.gemini = gemini;
    }

    public List<ChatTurn> history(Account actor) {
        evictIdle();
        Conversation conversation = conversations.get(actor.id());
        if (conversation == null) return List.of();
        return conversation.locked(() -> List.copyOf(conversation.turns));
    }

    public void clear(Account actor) {
        conversations.remove(actor.id());
    }

    /** Adds the client's message and the bot's answer; returns the whole conversation. */
    public List<ChatTurn> send(Account actor, String message) {
        String text = message == null ? "" : message.strip();
        if (text.isEmpty()) throw new ValidationException("Please type a message.");
        if (text.length() > ChatGuardrails.MAX_INPUT_CHARS) {
            throw new ValidationException("Please keep your message under " + ChatGuardrails.MAX_INPUT_CHARS + " characters.");
        }
        evictIdle();
        Conversation conversation = conversations.computeIfAbsent(actor.id(), id -> new Conversation());
        Instant now = clock.instant();
        ChatGuardrails.Verdict verdict = guardrails.check(text);

        // Checks and bookkeeping under the lock; the Gemini call itself runs without it.
        List<ChatTurn> context = conversation.locked(() -> {
            if (conversation.busy) throw new TooManyRequestsException("Please wait for the current answer first.");
            checkAccountLimits(conversation, now);
            conversation.sentAt.addLast(now);
            conversation.lastActive = now;
            if (!verdict.allowed()) {
                add(conversation, new ChatTurn(true, text, now));
                add(conversation, new ChatTurn(false, verdict.reply(), clock.instant()));
                return null;
            }
            reserveGlobalCall(now);
            conversation.busy = true;
            List<ChatTurn> turns = new ArrayList<>(recent(conversation.turns));
            turns.add(new ChatTurn(true, text, now));
            return turns;
        });
        if (context == null) return history(actor);

        String reply;
        try {
            reply = guardrails.screenReply(gemini.generate(guardrails.systemPrompt(), context));
        } finally {
            conversation.locked(() -> conversation.busy = false);
        }
        return conversation.locked(() -> {
            // Only stored once Gemini answered, so a failed attempt can simply be retried.
            add(conversation, new ChatTurn(true, text, now));
            add(conversation, new ChatTurn(false, reply, clock.instant()));
            conversation.lastActive = clock.instant();
            return List.copyOf(conversation.turns);
        });
    }

    private void checkAccountLimits(Conversation conversation, Instant now) {
        while (!conversation.sentAt.isEmpty() && conversation.sentAt.peekFirst().isBefore(now.minus(Duration.ofHours(1)))) {
            conversation.sentAt.removeFirst();
        }
        Instant last = conversation.sentAt.peekLast();
        if (last != null && Duration.between(last, now).compareTo(MIN_INTERVAL) < 0) {
            throw new TooManyRequestsException("You're sending messages too quickly. Please wait a few seconds.");
        }
        if (conversation.sentAt.size() >= PER_ACCOUNT_PER_HOUR) {
            throw new TooManyRequestsException("You've reached the chatbot limit for now. Please try again later, "
                    + "or message ThoMatt or JusticeCare from Services.");
        }
    }

    private void reserveGlobalCall(Instant now) {
        synchronized (globalCalls) {
            while (!globalCalls.isEmpty() && globalCalls.peekFirst().isBefore(now.minus(Duration.ofMinutes(1)))) {
                globalCalls.removeFirst();
            }
            if (globalCalls.size() >= GLOBAL_PER_MINUTE) {
                throw new TooManyRequestsException("The TalkCare Chatbot is busy right now. Please try again in a minute.");
            }
            globalCalls.addLast(now);
        }
    }

    /** The last few turns, starting with a user turn as Gemini requires. */
    private static List<ChatTurn> recent(List<ChatTurn> turns) {
        List<ChatTurn> recent = turns.subList(Math.max(0, turns.size() - CONTEXT_TURNS), turns.size());
        int start = 0;
        while (start < recent.size() && !recent.get(start).fromUser()) start++;
        return recent.subList(start, recent.size());
    }

    private static void add(Conversation conversation, ChatTurn turn) {
        conversation.turns.add(turn);
        while (conversation.turns.size() > MAX_STORED_TURNS) conversation.turns.removeFirst();
    }

    private void evictIdle() {
        Instant cutoff = clock.instant().minus(IDLE_EXPIRY);
        conversations.values().removeIf(c -> c.lastActive.isBefore(cutoff));
    }

    /** Guarded by {@code lock} (a ReentrantLock, so waiting virtual threads are not pinned). */
    private final class Conversation {
        final ReentrantLock lock = new ReentrantLock();
        final List<ChatTurn> turns = new ArrayList<>();
        final Deque<Instant> sentAt = new ArrayDeque<>();
        volatile Instant lastActive = clock.instant();
        boolean busy;

        <T> T locked(Supplier<T> action) {
            lock.lock();
            try {
                return action.get();
            } finally {
                lock.unlock();
            }
        }
    }
}
