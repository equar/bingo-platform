package com.bakaroo.bingo.backend.feature.game.websocket;

import static org.mockito.Mockito.verify;

import com.bakaroo.bingo.backend.feature.game.entity.GameSessionState;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class GameSessionStateBroadcastListenerTest {

    @Test
    void broadcastsSessionStateToTheLobbyTopic() {
        var messagingTemplate = org.mockito.Mockito.mock(SimpMessagingTemplate.class);
        var listener = new GameSessionStateBroadcastListener(messagingTemplate);
        var event = new GameSessionStateChangedEvent(42L, GameSessionState.STARTED);

        listener.onSessionStateChanged(event);

        verify(messagingTemplate).convertAndSend("/topic/sessions", event);
    }
}
