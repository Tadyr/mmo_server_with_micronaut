package server.attribute.stats.listener;

import io.micronaut.configuration.kafka.annotation.KafkaListener;
import io.micronaut.configuration.kafka.annotation.OffsetReset;
import io.micronaut.configuration.kafka.annotation.OffsetStrategy;
import io.micronaut.configuration.kafka.annotation.Topic;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import server.attribute.stats.model.Stats;
import server.socket.model.SocketResponse;
import server.socket.model.SocketResponseType;
import server.socket.service.ClientUpdatesService;

@Slf4j
@KafkaListener(
        groupId = "mmo-server",
        offsetReset = OffsetReset.EARLIEST,
        offsetStrategy = OffsetStrategy.SYNC,
        clientId = "stats_client")
public class StatsListener {

    @Inject ClientUpdatesService clientUpdatesService;

    @Topic("update-actor-stats")
    public void receiveUpdatePlayerAttributes(Stats stats) {
        SocketResponse socketResponse =
                SocketResponse.builder()
                        .messageType(SocketResponseType.STATS_UPDATE.getType())
                        .stats(stats)
                        .build();

        clientUpdatesService.sendUpdateToListeningIncludingSelf(socketResponse, stats.getActorId());
    }
}
