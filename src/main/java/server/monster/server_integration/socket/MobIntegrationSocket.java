package server.monster.server_integration.socket;

import io.micronaut.websocket.WebSocketBroadcaster;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import io.reactivex.rxjava3.core.Single;
import jakarta.inject.Inject;
import org.reactivestreams.Publisher;
import server.common.dto.Location2D;
import server.motion.model.MotionMessage;
import server.motion.model.PlayerMotionList;
import server.motion.service.PlayerMotionService;

import java.util.function.Predicate;

@Deprecated // use CommunicationSocket instead
@ServerWebSocket("/v1/mob-integration/{map}/{serverInstance}/")
public class MobIntegrationSocket {

    private final WebSocketBroadcaster broadcaster;

    private static final Location2D distanceThreshold = new Location2D(30, 30);

    @Inject PlayerMotionService playerMotionService;

    public MobIntegrationSocket(WebSocketBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @OnOpen
    public Publisher<String> onOpen(String map, String serverInstance, WebSocketSession session) {
        // see what mobs are available around to sync up
        return broadcaster.broadcast(String.format("[%s] Joined %s!", serverInstance, map));
    }

    @OnMessage
    public Single<PlayerMotionList> onMessage(
            String serverInstance, String map, MotionMessage message, WebSocketSession session) {

        if (message.getUpdate()) {
            playerMotionService.updatePlayerMotion(serverInstance, message.getMotion());
        }

        return playerMotionService
                .getPlayersNearMe(message.getMotion(), serverInstance)
                .doOnSuccess(broadcaster::broadcast);
    }

    @OnClose
    public Publisher<String> onClose(String serverInstance, String map, WebSocketSession session) {

        return broadcaster.broadcast(String.format("[%s] Leaving %s!", serverInstance, map));
    }

    private Predicate<WebSocketSession> isValid(String actorId) {
        // we will report to player every time they call update about other players nearby
        return s ->
                actorId.equalsIgnoreCase(s.getUriVariables().get("actorId", String.class, null));
    }
}
