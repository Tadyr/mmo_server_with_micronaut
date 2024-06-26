package server.socket.listener;

import io.micronaut.configuration.kafka.annotation.KafkaListener;
import io.micronaut.configuration.kafka.annotation.OffsetReset;
import io.micronaut.configuration.kafka.annotation.OffsetStrategy;
import io.micronaut.configuration.kafka.annotation.Topic;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import server.items.equippable.model.EquippedItems;
import server.items.inventory.model.ItemInstanceIds;
import server.items.inventory.model.response.GenericInventoryData;
import server.items.model.DroppedItem;
import server.monster.server_integration.model.Monster;
import server.motion.dto.PlayerMotion;
import server.socket.model.SocketResponse;
import server.socket.model.SocketResponseType;
import server.socket.service.ClientUpdatesService;

@Slf4j
@KafkaListener(
        groupId = "mmo-server",
        offsetReset = OffsetReset.EARLIEST,
        offsetStrategy = OffsetStrategy.SYNC,
        clientId = "socket_listener")
public class SocketUpdateListener {

    @Inject ClientUpdatesService clientUpdatesService;

    @Topic("player-motion-update-result")
    void receivePlayerMotionUpdate(PlayerMotion playerMotion) {
        SocketResponse socketResponse =
                SocketResponse.builder()
                        .messageType(SocketResponseType.PLAYER_MOTION_UPDATE.getType())
                        .playerMotion(Map.of(playerMotion.getActorId(), playerMotion))
                        .playerKeys(Set.of(playerMotion.getActorId()))
                        .build();

        log.info("{}", playerMotion);
        clientUpdatesService.sendUpdateToListening(socketResponse, playerMotion.getActorId());
    }

    @Topic("mob-motion-update-result")
    void receiveMobMotionUpdate(Monster monster) {
        SocketResponse socketResponse =
                SocketResponse.builder()
                        .messageType(SocketResponseType.MOB_MOTION_UPDATE.getType())
                        .monsters(Map.of(monster.getActorId(), monster))
                        .mobKeys(Set.of(monster.getActorId()))
                        .build();

        log.info("{}", monster);
        clientUpdatesService.sendUpdateToListeningPlayers(socketResponse, monster.getActorId());
    }

    @Topic("item-added-to-map")
    void itemAddedToMap(DroppedItem droppedItem) {
        SocketResponse socketResponse =
                SocketResponse.builder()
                        .messageType(SocketResponseType.ADD_ITEMS_TO_MAP.getType())
                        .droppedItems(
                                Map.of(
                                        droppedItem.getItemInstance().getItemInstanceId(),
                                        droppedItem))
                        .build();

        clientUpdatesService.sendDroppedItemUpdates(droppedItem);
    }

    @Topic("item-removed-from-map")
    void itemRemovedFromMap(String itemInstanceId) {
        clientUpdatesService.sendItemPickupUpdates(itemInstanceId);
    }

    @Topic("notify-equip-items")
    void notifyItemEquip(List<EquippedItems> equippedItems) {
        if (equippedItems == null || equippedItems.isEmpty()) {
            return;
        }
        String actorId = equippedItems.get(0).getActorId();
        GenericInventoryData equipData = new GenericInventoryData();
        equipData.setActorId(actorId);
        equipData.setEquippedItems(equippedItems);
        SocketResponse socketResponse =
                SocketResponse.builder()
                        .inventoryData(equipData)
                        .messageType(SocketResponseType.ADD_EQUIP_ITEM.getType())
                        .build();

        clientUpdatesService.sendUpdateToListeningIncludingSelf(socketResponse, actorId);
    }

    @Topic("notify-un-equip-items")
    void notifyUnEquipItem(ItemInstanceIds itemInstanceIds) {

        if (itemInstanceIds == null
                || itemInstanceIds.getItemInstanceIds() == null
                || itemInstanceIds.getItemInstanceIds().isEmpty()) {
            return;
        }
        GenericInventoryData equipData = new GenericInventoryData();
        equipData.setActorId(itemInstanceIds.getActorId());
        equipData.setItemInstanceIds(itemInstanceIds.getItemInstanceIds());
        SocketResponse socketResponse =
                SocketResponse.builder()
                        .inventoryData(equipData)
                        .messageType(SocketResponseType.REMOVE_EQUIP_ITEM.getType())
                        .build();

        clientUpdatesService.sendUpdateToListeningIncludingSelf(
                socketResponse, itemInstanceIds.getActorId());
    }
}
