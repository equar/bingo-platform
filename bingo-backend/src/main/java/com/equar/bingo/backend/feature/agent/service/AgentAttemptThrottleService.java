package com.equar.bingo.backend.feature.agent.service;

import com.equar.bingo.backend.common.exception.TooManyRequestsException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class AgentAttemptThrottleService {

    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    public void requireAllowed(String key, int limit, Duration window, String code, String message) {
        Instant now = Instant.now();
        Deque<Instant> deque = attempts.computeIfAbsent(key, unused -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst().isBefore(now.minus(window))) {
                deque.removeFirst();
            }
            if (deque.size() >= limit) {
                throw new TooManyRequestsException(code, message);
            }
            deque.addLast(now);
        }
    }

    public void clear(String key) {
        attempts.remove(key);
    }
}