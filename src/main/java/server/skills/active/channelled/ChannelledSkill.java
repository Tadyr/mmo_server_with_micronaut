package server.skills.active.channelled;

import lombok.Getter;
import server.combat.model.CombatData;
import server.combat.model.CombatState;
import server.skills.active.ActiveSkill;
import server.skills.model.SkillTarget;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Getter
public abstract class ChannelledSkill extends ActiveSkill {

    private int castTime;

    private boolean allowsMovement;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public ChannelledSkill(String name, String description, Map<String, Double> derived, int cooldown, int castTime,
                           boolean allowsMovement, int maxRange, Map<String, Integer> requirements) {
        super(name, description, derived, cooldown, maxRange, requirements);
        this.castTime = castTime;
        this.allowsMovement = allowsMovement;
    }

    public void interruptChannel(CombatData combatData) {

    }

    public void stopChannel(CombatData combatData) {

    }

    public void startChanneling(ChannelledSkill channelledSkill, CombatData combatData, SkillTarget skillTarget) {
        combatData.setCombatState(CombatState.CHANNELING.getType());

        // Schedule a task to periodically check the channeling status
        ScheduledFuture<?> channelingTask = scheduler.scheduleAtFixedRate(() -> {
            if (!channelingInProgress(combatData)) {
                // Channeling interrupted or completed
                scheduler.shutdownNow();
            }
        }, 0, 100, TimeUnit.MILLISECONDS); // Adjust the interval as needed

        // Schedule a task to execute the skill after the channel time
        scheduler.schedule(() -> {
            if (channelingInProgress(combatData)) {
                channelledSkill.startSkill(combatData, skillTarget);
            }
            combatData.setCombatState(CombatState.IDLE.getType()); // Reset combat state
            channelingTask.cancel(true); // Stop the periodic check
        }, channelledSkill.getCastTime(), TimeUnit.MILLISECONDS);
    }

    private boolean channelingInProgress(CombatData combatData) {
        return combatData.getCombatState().equalsIgnoreCase(CombatState.CHANNELING.getType());
    }

}
