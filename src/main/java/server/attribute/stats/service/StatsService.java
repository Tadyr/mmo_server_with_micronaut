package server.attribute.stats.service;

import com.mongodb.client.result.DeleteResult;
import io.reactivex.rxjava3.core.Single;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import server.attribute.stats.model.Stats;
import server.attribute.stats.repository.ActorStatsRepository;
import server.attribute.stats.types.DamageTypes;
import server.attribute.stats.types.StatsTypes;
import server.combat.model.CombatData;
import server.session.SessionParamHelper;
import server.socket.producer.UpdateProducer;

@Slf4j
@Singleton
public class StatsService {

    @Inject ActorStatsRepository repository;

    @Inject UpdateProducer updateProducer;

    @Inject SessionParamHelper sessionParamHelper;

    public void initializeMobStats(String actorId) {
        Stats mobStats = new Stats();
        // TODO: parameterize from server
        int level = 10;
        double baseAttackSpeed = 5.0;
        double weaponDamage = 20;

        mobStats.setActorId(actorId);

        mobStats.setBaseStats(
                new HashMap<>(
                        Map.of(
                                StatsTypes.STR.getType(), 50,
                                StatsTypes.STA.getType(), 100,
                                StatsTypes.DEX.getType(), 100,
                                StatsTypes.INT.getType(), 100)));

        mobStats.getDerivedStats()
                .putAll(
                        new HashMap<>(
                                Map.of(
                                        StatsTypes.CURRENT_HP.getType(), 100.0,
                                        StatsTypes.CURRENT_MP.getType(), 50.0)));

        mobStats.setBase(StatsTypes.LEVEL, level);
        mobStats.setDerived(StatsTypes.MAIN_HAND_ATTACK_SPEED, baseAttackSpeed);
        mobStats.setDerived(StatsTypes.WEAPON_DAMAGE, weaponDamage);

        mobStats.recalculateDerivedStats();
        mobStats.setDerived(StatsTypes.CURRENT_HP, mobStats.getDerived(StatsTypes.MAX_HP));
        mobStats.setAttributePoints(0);

        repository.updateStats(mobStats.getActorId(), mobStats).subscribe();
        CombatData combatData = new CombatData(actorId);
        sessionParamHelper.setSharedActorCombatData(actorId, combatData);
    }

    public Single<Stats> initializePlayerStats(String actorId) {
        Stats playerStats = new Stats();

        playerStats.setActorId(actorId);

        playerStats.setBaseStats(
                new HashMap<>(
                        Map.of(
                                StatsTypes.STR.getType(), 15,
                                StatsTypes.STA.getType(), 15,
                                StatsTypes.DEX.getType(), 15,
                                StatsTypes.INT.getType(), 15)));

        playerStats
                .getDerivedStats()
                .putAll(
                        new HashMap<>(
                                Map.of(
                                        StatsTypes.CURRENT_HP.getType(), 100.0,
                                        StatsTypes.CURRENT_MP.getType(), 50.0)));

        playerStats.recalculateDerivedStats();

        playerStats.setAttributePoints(0);

        return repository.updateStats(playerStats.getActorId(), playerStats);
    }

    public Single<Stats> getStatsFor(String actorId) {
        return repository.fetchActorStats(actorId);
    }

    public Single<DeleteResult> deleteStatsFor(String actorId) {
        return repository.deleteStats(actorId);
    }

    public void updateItemStats(String actorId, Map<String, Double> itemStats) {
        repository
                .fetchActorStats(actorId)
                .doOnSuccess(
                        stats -> {
                            stats.setItemEffects(itemStats);
                            Map<String, Double> updated = stats.recalculateDerivedStats();
                            handleDifference(updated, stats);
                        })
                .doOnError(err -> log.error("Failed to update item stats, {}", err.getMessage()))
                .blockingSubscribe();
    }

    public Stats takeDamage(Stats stats, Map<DamageTypes, Double> damageMap) {
        // TODO: send stat update once, send map of damage
        damageMap.forEach(
                (k, v) -> {
                    Double currentHp = stats.getDerived(StatsTypes.CURRENT_HP);
                    currentHp -= v;
                    setAndHandleDifference(stats, currentHp, StatsTypes.CURRENT_HP);
                });

        return stats;
    }

    public Stats addHealth(Stats stats, Double amount) {
        Double currentHp = stats.getDerived(StatsTypes.CURRENT_HP);
        currentHp += amount;
        setAndHandleDifference(stats, currentHp, StatsTypes.CURRENT_HP);
        return stats;
    }

    private void setAndHandleDifference(Stats stats, Double val, StatsTypes evalType) {
        stats.getDerivedStats().put(evalType.getType(), val);
        Map<String, Double> updated = Map.of(evalType.getType(), val);
        handleDifference(updated, stats);
    }

    public void applyRegen(String actorName) {
        getStatsFor(actorName)
                .doOnSuccess(
                        stats -> {
                            if (stats == null) {
                                return;
                            }
                            applyRegen(stats);
                        })
                .doOnError(err -> log.error("Failed to apply regen for actor: {}, {}", actorName, err.getMessage()))
                .subscribe();
    }

    public void applyRegen(Stats stats) {
        applyRegen(stats, StatsTypes.HP_REGEN);
        applyRegen(stats, StatsTypes.MP_REGEN);
    }

    private void applyRegen(Stats stats, StatsTypes type) {
        if (!stats.canAct()) {
            return;
        }

        Double regen = stats.getDerived(type);

        StatsTypes evalType =
                type == StatsTypes.MP_REGEN ? StatsTypes.CURRENT_MP : StatsTypes.CURRENT_HP;

        StatsTypes maxType = type == StatsTypes.MP_REGEN ? StatsTypes.MAX_MP : StatsTypes.MAX_HP;

        Double currentVal = stats.getDerived(evalType);
        Double maxVal = stats.getDerived(maxType);

        if (currentVal >= maxVal) {
            return;
        }

        Double res = currentVal + regen;
        setAndHandleDifference(stats, res, evalType);
    }

    void handleDifference(Map<String, Double> updated, Stats stats) {
        if (!updated.isEmpty()) {
            //          TODO: Make this async, its blocking to help with tests only
            repository
                    .updateStats(stats.getActorId(), stats)
                    .doOnError(err -> log.error("Failed to update stats, {}", err.getMessage()))
                    .blockingGet();
            Stats notifyUpdates =
                    Stats.builder().actorId(stats.getActorId()).derivedStats(updated).build();
            updateProducer.updateStats(notifyUpdates);
        }
    }
}
