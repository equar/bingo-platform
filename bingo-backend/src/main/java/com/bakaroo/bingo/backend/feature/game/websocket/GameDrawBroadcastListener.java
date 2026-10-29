package com.bakaroo.bingo.backend.feature.game.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Component
public class GameDrawBroadcastListener {

    private final SimpMessagingTemplate messagingTemplate;

    public GameDrawBroadcastListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDrawRecorded(GameDrawRecordedEvent event) {
        messagingTemplate.convertAndSend("/topic/session/" + event.draw().sessionId() + "/draws", event.draw());
    }
}