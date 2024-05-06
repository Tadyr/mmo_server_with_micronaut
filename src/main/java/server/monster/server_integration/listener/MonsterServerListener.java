package server.monster.server_integration.listener;

import io.micronaut.configuration.kafka.annotation.KafkaListener;
import io.micronaut.configuration.kafka.annotation.OffsetReset;
import io.micronaut.configuration.kafka.annotation.OffsetStrategy;
import io.micronaut.configuration.kafka.annotation.Topic;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import server.attribute.stats.service.StatsService;
import server.monster.server_integration.model.Monster;
import server.monster.server_integration.producer.MonsterServerProducer;
import server.monster.server_integration.service.MobInstanceService;
import server.motion.repository.ActorMotionRepository;
import server.session.SessionParamHelper;

@Slf4j
@KafkaListener(
        groupId = "mmo-server",
        offsetReset = OffsetReset.EARLIEST,
        offsetStrategy = OffsetStrategy.SYNC,
        clientId = "mob_repo_client")
public class MonsterServerListener {

    @Inject MonsterServerProducer monsterServerProducer;

    @Inject MobInstanceService mobInstanceService;

    @Inject StatsService statsService;

    @Inject SessionParamHelper sessionParamHelper;

    @Inject ActorMotionRepository actorMotionRepository;

    @Topic("create-mob")
    public void receiveCreateMob(Monster monster) {
        mobInstanceService
                .createMob(monster)
                .doOnSuccess(mob -> statsService.initializeMobStats(monster.getActorId()))
                .doOnError(error -> log.error("Error on creating mob, {}", error.getMessage()))
                .subscribe();
    }

    @Topic("mob-motion-update")
    public void receiveUpdateMob(Monster monster) {
        // Add validation
        actorMotionRepository.updateActorMotion(monster.getActorId(), monster.getMotion());
        monsterServerProducer.sendMobUpdateResult(monster);
    }
}
