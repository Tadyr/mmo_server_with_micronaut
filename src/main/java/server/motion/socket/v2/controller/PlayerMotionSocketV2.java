package server.motion.socket.v2.controller;

import io.micronaut.scheduling.annotation.Scheduled;
import io.micronaut.websocket.WebSocketBroadcaster;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import io.netty.util.internal.ConcurrentSet;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import server.common.dto.Location;
import server.common.dto.Motion;
import server.monster.server_integration.model.Monster;
import server.monster.server_integration.service.MobInstanceService;
import server.motion.dto.MotionResult;
import server.motion.dto.PlayerMotion;
import server.motion.model.MotionMessage;
import server.motion.model.SessionParams;
import server.motion.service.PlayerMotionService;

import java.time.Instant;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// V2 socket will work quite different to V1.
// We will periodically check the state of who's near the player
// the details of who is near is saved in session
// whenever that player/mob makes any motion, the player will be updated

// TODO: https://www.confluent.io/blog/real-time-gaming-infrastructure-kafka-ksqldb-websockets/
@Slf4j
@ServerWebSocket("/v2/actor-updates/{map}/{actorId}/")
public class PlayerMotionSocketV2 {

    private final WebSocketBroadcaster broadcaster;

    private static final Integer DISTANCE_THRESHOLD = 1000;

    @Inject PlayerMotionService playerMotionService;

    @Inject MobInstanceService mobInstanceService;

    ConcurrentSet<WebSocketSession> sessionsList;

    public PlayerMotionSocketV2(WebSocketBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
        sessionsList = new ConcurrentSet<>();
    }

    @OnOpen
    public Publisher<String> onOpen(String map, String actorId, WebSocketSession session) {
        // player could also be server instance
        session.put(SessionParams.TRACKING_PLAYERS.getType(), Set.of());
        session.put(SessionParams.TRACKING_MOBS.getType(), Set.of());
        sessionsList.add(session);

        return broadcaster.broadcast(String.format("[%s] Joined %s!", actorId, map));
    }

    @OnMessage
    public Publisher<MotionResult> onMessage(
            String actorId, String map, MotionMessage message, WebSocketSession session) {

        if (timeToUpdate(
                message, (Instant) session.asMap().get(SessionParams.LAST_UPDATED_AT.getType()))) {
            // update the players motion
            log.info("Updating player {} motion", actorId);
            return handlePlayerUpdate(actorId, map, message, session);
        }

        if (null != message.getActorId() && !message.getActorId().isBlank()) {
            // knowing whether we create or update is more efficient for db

            if (message.getUpdate()) {
                log.info("updating mob with ID: {}", message.getActorId());
                mobInstanceService
                        .updateMobMotion(message.getActorId(), message.getMotion())
                        .doOnError(
                                (error) ->
                                        log.error(
                                                "Error updating mob motion, {}",
                                                error.getMessage()))
                        .doOnSuccess(
                                (success) ->
                                        log.info(
                                                "successfully updated mob, {}",
                                                success.getActorId()))
                        .subscribe();
            } else {
                log.info("creating mob with ID: {}", message.getActorId());
                mobInstanceService
                        .createMob(message.getActorId(), message.getMotion())
                        .doOnError(
                                (error) -> log.error("Error creating mob, {}", error.getMessage()))
                        .doOnSuccess((success) -> log.info("successfully created mob"))
                        .blockingGet();
            }

            return broadcaster.broadcast(
                    mobInstanceService.buildMobMotionResult(
                            message.getActorId(), message.getMotion()),
                    isValid(message.getActorId()));
        }

        return null;
    }

    private Publisher<MotionResult> handlePlayerUpdate(
            String actorId, String map, MotionMessage message, WebSocketSession session) {
        session.put(SessionParams.LAST_UPDATED_AT.getType(), Instant.now());
        PlayerMotion playerMotion =
                playerMotionService.buildPlayerMotion(actorId, map, message.getMotion());
        session.put(SessionParams.MOTION.getType(), playerMotion.getMotion());
        playerMotionService
                .updatePlayerMotion(playerMotion)
                .doOnError(
                        (error) ->
                                log.error("Error updating player motion, {}", error.getMessage()))
                .subscribe();

        MotionResult motionResult = MotionResult.builder().playerMotion(playerMotion).build();

        // only broadcast when required
        return broadcaster.broadcast(motionResult, isValid(actorId));
    }

    private boolean timeToUpdate(MotionMessage message, Instant lastUpdated) {
        if (message.getActorId() != null) {
            return false;
        }

        if (lastUpdated == null) {
            return true;
        }

        return message.getUpdate() // either server tells us to update (there's been motion)
                || Instant.now()
                        .isAfter(
                                lastUpdated.plusMillis(3000)); // or its time to periodically update
    }

    @OnClose
    public Publisher<String> onClose(String actorId, String map, WebSocketSession session) {
        playerMotionService.disconnectPlayer(actorId);

        return broadcaster.broadcast(String.format("[%s] Leaving %s!", actorId, map));
    }

    private Predicate<WebSocketSession> isValid(String playerOrMob) {
        // we will report to player every time they call update about other players nearby
        return s -> {
            Set<String> playersTrackedInSession =
                    (Set<String>) s.asMap().get(SessionParams.TRACKING_PLAYERS.getType());

            Set<String> mobsTrackedInSession =
                    (Set<String>) s.asMap().get(SessionParams.TRACKING_MOBS.getType());

            return playersTrackedInSession.contains(playerOrMob)
                    || mobsTrackedInSession.contains(playerOrMob);
        };
    }

    @Scheduled(fixedDelay = "1s")
    public void syncSessionNearbyData() {

        sessionsList.parallelStream()
                .forEach(
                        session -> {
                            String actorId =
                                    session.getUriVariables().get("actorId", String.class, null);
                            Motion motion =
                                    (Motion) session.asMap().get(SessionParams.MOTION.getType());
                            if (motion == null) {
                                // possibly the motion is not fully initiated
                                return;
                            }
                            // sync nearby players
                            playerMotionService
                                    .getNearbyPlayersAsync(motion, actorId, DISTANCE_THRESHOLD)
                                    .doAfterSuccess(
                                            list -> {
                                                Set<String> actorIds =
                                                        list.stream()
                                                                .map(PlayerMotion::getActorId)
                                                                .collect(Collectors.toSet());
                                                // update the names that we follow
                                                session.put(
                                                        SessionParams.TRACKING_PLAYERS.getType(),
                                                        actorIds);
                                            })
                                    .doOnError(
                                            (error) ->
                                                    log.error(
                                                            "error getting nearby players, {}",
                                                            error.getMessage()))
                                    .subscribe();

                            // sync nearby mobs
                            mobInstanceService
                                    .getMobsNearby(new Location(motion))
                                    .doAfterSuccess(
                                            mobList -> {
                                                Set<String> actorIds =
                                                        mobList.stream()
                                                                .map(Monster::getActorId)
                                                                .collect(Collectors.toSet());
                                                session.put(
                                                        SessionParams.TRACKING_MOBS.getType(),
                                                        actorIds);
                                            })
                                    .doOnError(
                                            (error) ->
                                                    log.error(
                                                            "error getting nearby mobs, {}",
                                                            error.getMessage()))
                                    .subscribe();
                        });
    }
}
