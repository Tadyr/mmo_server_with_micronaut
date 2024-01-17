package server.socket.service.synchronisation;

import io.micronaut.scheduling.annotation.Scheduled;
import io.micronaut.websocket.WebSocketSession;
import io.netty.util.internal.ConcurrentSet;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import server.common.dto.Motion;
import server.session.SessionParamHelper;
import server.socket.v1.CommunicationSocket;

@Slf4j
@Singleton
public class SynchroniseSessionService {
    // we need to synchronise certain data for sessions
    // for example, what characters are nearby

    @Inject CommunicationSocket socket;

    @Inject SynchronisePlayerService synchronisePlayerService;

    @Inject SynchroniseMobService synchroniseMobService;

    @Inject SynchroniseDroppedItemsService synchroniseDroppedItemsService;

    @Scheduled(fixedDelay = "500ms")
    public void evaluateNearbyPlayers() {
        ConcurrentSet<WebSocketSession> sessions = socket.getLiveSessions();

        sessions.parallelStream()
                .forEach(
                        session -> {
                            Motion motion = SessionParamHelper.getMotion(session);
                            if (motion == null) {
                                // possibly the motion is not fully initiated
                                return;
                            }

                            synchronisePlayerService.handleSynchronisePlayers(motion, session);
                            synchroniseMobService.handleSynchroniseMobs(motion, session);
                            synchroniseDroppedItemsService.handleSynchroniseDroppedItems(
                                    motion, session);
                        });
    }
}
