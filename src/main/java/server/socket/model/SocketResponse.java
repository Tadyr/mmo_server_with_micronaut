package server.socket.model;

import java.util.Map;
import java.util.Set;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import server.attribute.stats.model.Stats;
import server.attribute.status.model.ActorStatus;
import server.combat.model.CombatRequest;
import server.items.inventory.model.response.GenericInventoryData;
import server.items.model.DroppedItem;
import server.monster.server_integration.model.Monster;
import server.motion.dto.PlayerMotion;
import server.player.model.Character;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Serdeable
public class SocketResponse {

    String messageType;

    Set<String> lostPlayers; // players that will no longer be synced (out of range, etc)
    Set<String> lostMobs;

    Set<String> playerKeys;
    Set<String> mobKeys;

    Map<String, PlayerMotion> playerMotion;
    Map<String, Character> playerData;
    Map<String, Monster> monsters;

    Map<String, DroppedItem> droppedItems;
    Set<String> itemInstanceIds;

    GenericInventoryData inventoryData;
    String error;

    CombatRequest combatRequest;

    Stats stats;

    ActorStatus actorStatus;
}
