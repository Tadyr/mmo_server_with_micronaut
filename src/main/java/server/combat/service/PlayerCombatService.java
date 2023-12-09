package server.combat.service;

import static server.attribute.stats.types.StatsTypes.PHY_AMP;
import static server.attribute.stats.types.StatsTypes.WEAPON_DAMAGE;

import io.micronaut.websocket.WebSocketSession;
import io.reactivex.rxjava3.core.Single;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import server.attribute.stats.model.Stats;
import server.attribute.stats.types.DamageTypes;
import server.attribute.stats.types.StatsTypes;
import server.combat.model.CombatRequest;
import server.combat.model.CombatData;
import server.common.dto.Motion;
import server.items.equippable.model.EquippedItems;
import server.session.SessionParamHelper;
import server.socket.service.ClientUpdatesService;

@Slf4j
@Singleton
public class PlayerCombatService extends CombatService  {

    @Inject ClientUpdatesService clientUpdatesService;

    Random rand = new Random();

    public void requestAttack(WebSocketSession session, CombatRequest combatRequest) {
        if (combatRequest == null) {
            return;
        }
        CombatData combatData = SessionParamHelper.getActorCombatData(session, SessionParamHelper.getActorId(session));
        combatData.setTargets(combatRequest.getTargets());

        sessionsInCombat.add(SessionParamHelper.getActorId(session));
        attackLoop(session, combatData.getActorId());
    }

    public void requestStopAttack(String actorId) {
        sessionsInCombat.remove(actorId);
    }

    public void tryAttack(WebSocketSession session, Stats target, boolean isMainHand) {
        // Extract relevant combat data
        CombatData combatData = SessionParamHelper.getActorCombatData(session, SessionParamHelper.getActorId(session));
        Map<String, EquippedItems> items = SessionParamHelper.getEquippedItems(session);
        Map<String, Double> derivedStats = SessionParamHelper.getActorDerivedStats(session);

        // Get the equipped weapon
        EquippedItems weapon = isMainHand ? items.get("WEAPON") : items.get("SHIELD");
        if (weapon == null) {
            return;
        }

        int distanceThreshold =
                weapon.getAttackDistance() == null
                        ? 200
                        : (int) (double) weapon.getAttackDistance();

        Motion attackerMotion = SessionParamHelper.getMotion(session);
        boolean valid = validatePositionLocation(combatData, attackerMotion, target.getActorId(),
                distanceThreshold, session);

        if (!valid) {
            return;
        }

        Instant lastHit =
                isMainHand ? combatData.getMainHandLastAttack() : combatData.getOffhandLastAttack();
        // TODO: this is for demo, needs changing
        if (lastHit == null || lastHit.isBefore(Instant.now().minusSeconds(4))) {
            lastHit = Instant.now().minusSeconds(4);
            requestAttackSwing(session, isMainHand);
        }
        Double baseSpeed =
                isMainHand
                        ? combatData.getMainHandAttackSpeed()
                        : combatData.getOffhandAttackSpeed();
        Double characterAttackSpeed = combatData.getActorAttackSpeed();

        // Calculate the actual delay in milliseconds
        long actualDelayInMS = (long) (getAttackTimeDelay(baseSpeed, characterAttackSpeed) * 1000);

        // Calculate the next allowed attack time
        Instant nextAttackTime = lastHit.plusMillis(actualDelayInMS);

        if (nextAttackTime.isBefore(Instant.now())) {
            // The player can attack
            // Get derived stats and equipped items

            if (!combatData.getAttackSent().getOrDefault(isMainHand ? "MAIN" : "OFF", false)) {
                requestAttackSwing(session, isMainHand);
            }

            combatData.getAttackSent().put(isMainHand ? "MAIN" : "OFF", false);

            // Create a damage map (currently only physical damage)
            Map<DamageTypes, Double> damageMap = calculateDamageMap(weapon, derivedStats);
            Stats stats = statsService.takeDamage(target, damageMap);
            if (isMainHand) {
                combatData.setMainHandLastAttack(Instant.now());
            } else {
                combatData.setOffhandLastAttack(Instant.now());
            }

            if (stats.getDerived(StatsTypes.CURRENT_HP) <= 0.0) {
                statsService
                        .deleteStatsFor(stats.getActorId())
                        .doOnError(
                                err ->
                                        log.error(
                                                "Failed to delete stats on death, {}",
                                                err.getMessage()))
                        .subscribe();
                mobInstanceService.handleMobDeath(stats.getActorId());
                clientUpdatesService.notifyServerOfRemovedMobs(Set.of(stats.getActorId()));
                combatData.getTargets().remove(target.getActorId());
            }

            return;
        }

        // Check if the next attack time is before the current time
        if (nextAttackTime.isBefore(Instant.now().plusMillis(100))) {
            // send a swing action as we're about to hit - we don't know if we will hit or miss yet
            requestAttackSwing(session, isMainHand);
        }
    }

    void attackLoop(WebSocketSession session, String actorId) {
        CombatData combatData = SessionParamHelper.getActorCombatData(session, actorId);
        Set<String> targets = combatData.getTargets();

        List<Stats> targetStats = getTargetStats(targets);

        if (targetStats.isEmpty()) {
            log.warn("Target stats empty");
            sessionsInCombat.remove(SessionParamHelper.getActorId(session));
            return;
        }

        targetStats.forEach(
                stat -> {
                    tryAttack(session, stat, true);
                    //            tryAttack(session, stat, "OFF_HAND");
                });

        Single.fromCallable(
                        () -> {
                            attackLoop(session, SessionParamHelper.getActorId(session));
                            return true;
                        })
                .delaySubscription(100, TimeUnit.MILLISECONDS)
                .doOnError(er -> log.error("Error encountered, {}", er.getMessage()))
                .subscribe();
    }

    private void requestAttackSwing(WebSocketSession session, boolean isMainHand) {
        Map<String, EquippedItems> items = SessionParamHelper.getEquippedItems(session);

        // Get the equipped weapon
        EquippedItems weapon = isMainHand ? items.get("WEAPON") : items.get("SHIELD");
        String itemInstanceId = weapon.getItemInstance().getItemInstanceId();

        SessionParamHelper.getActorCombatData(session, SessionParamHelper.getActorId(session))
                .getAttackSent()
                .put(isMainHand ? "MAIN" : "OFF", true);

        requestSessionsToSwingWeapon(itemInstanceId, SessionParamHelper.getActorId(session));
    }

    private Map<DamageTypes, Double> calculateDamageMap(
            EquippedItems weapon, Map<String, Double> derivedStats) {
        // Calculate damage based on weapon and stats
        Map<String, Double> itemEffects = weapon.getItemInstance().getItem().getItemEffects();
        Double damage = itemEffects.get(WEAPON_DAMAGE.getType());
        double amp = derivedStats.get(PHY_AMP.getType());

        double totalDamage = damage * amp * (1 + rand.nextDouble(0.15));

        // Create a damage map (currently only physical damage)
        return Map.of(DamageTypes.PHYSICAL, totalDamage);
    }

    private Double getAttackTimeDelay(Double baseAttackSpeed, Double characterAttackSpeed) {
        // 100 attack speed increases speed by 2x
        return baseAttackSpeed / (1 + (characterAttackSpeed / 100));
    }

}
