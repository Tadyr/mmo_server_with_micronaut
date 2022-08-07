package server.player.motion.socket.v1.controller;

import io.micronaut.websocket.WebSocketBroadcaster;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.common.dto.Motion;
import server.player.motion.socket.v1.model.PlayerMotionList;
import server.player.motion.socket.v1.service.PlayerMotionService;

import javax.inject.Inject;

@ServerWebSocket("/v1/player-motion/{map}/{playerName}")
public class PlayerMotionSocket {

    private static final Logger LOG = LoggerFactory.getLogger(PlayerMotionSocket.class);
    private final WebSocketBroadcaster broadcaster;

    @Inject
    PlayerMotionService playerMotionService;

    public PlayerMotionSocket(WebSocketBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @OnOpen
    public Publisher<String> onOpen(String map, String playerName, WebSocketSession session) {
        log("onOpen", session, playerName, map);


        return broadcaster.broadcast(String.format("[%s] Joined %s!", playerName, map));
    }

    @OnMessage
    public Publisher<PlayerMotionList> onMessage(
            String playerName,
            String map,
            Motion message,
            WebSocketSession session) {

        log("onMessage", session, playerName, map);

        playerMotionService.updatePlayerMotion(playerName, message);
        PlayerMotionList res = playerMotionService.getPlayersNearMe(message, playerName);

        return broadcaster.broadcast(res);
    }

    @OnClose
    public Publisher<String> onClose(
            String playerName,
            String map,
            WebSocketSession session) {

        log("onClose", session, playerName, map);
        playerMotionService.disconnectPlayer(playerName);
        return broadcaster.broadcast(String.format("[%s] Leaving %s!", playerName, map));
    }

    private void log(String event, WebSocketSession session, String username, String topic) {
        LOG.info("* WebSocket: {} received for session {} from '{}' regarding '{}'",
                event, session.getId(), username, topic);
    }

//    private Predicate<WebSocketSession> isValid(String map) {
//        return s -> map.equals("all") //broadcast to all users
//                || "all".equals(s.getUriVariables().get("map", String.class, null)) //"all" subscribes to every topic
//                || map.equalsIgnoreCase(s.getUriVariables().get("map", String.class, null)); //intra-topic chat
//    }
}