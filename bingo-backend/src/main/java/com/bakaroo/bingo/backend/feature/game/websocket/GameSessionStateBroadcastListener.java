package com.bakaroo.bingo.backend.feature.game.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Component
public class GameSessionStateBroadcastListener {

    private final SimpMessagingTemplate messagingTemplate;

    public GameSessionStateBroadcastListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSessionStateChanged(GameSessionStateChangedEvent event) {
        messagingTemplate.convertAndSend("/topic/sessions", event);
    }
}
