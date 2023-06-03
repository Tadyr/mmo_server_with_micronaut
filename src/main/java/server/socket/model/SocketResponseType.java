package server.socket.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SocketResponseType {
    PLAYER_APPEARANCE("PLAYER_APPEARANCE"),
    REMOVE_PLAYERS("REMOVE_PLAYERS"),
    PLAYER_MOTION_UPDATE("PLAYER_MOTION_UPDATE"),
    MOB_MOTION_UPDATE("MOB_MOTION_UPDATE"),
    REMOVE_MOBS("REMOVE_MOBS"),
    ADD_ITEMS_TO_MAP("ADD_ITEMS_TO_MAP"),
    REMOVE_ITEMS_FROM_MAP("REMOVE_ITEMS_FROM_MAP"),
    INVENTORY_UPDATE("INVENTORY_UPDATE"),
    INVENTORY_ERROR("INVENTORY_ERROR");

    public final String type;
}
