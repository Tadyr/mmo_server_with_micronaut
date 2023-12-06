package server.items.equippable.model.types;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import server.items.equippable.model.EquippedItems;
import server.items.model.ItemInstance;
import server.items.types.ItemType;

@Data
@NoArgsConstructor
@JsonTypeName("GLOVES")
@EqualsAndHashCode(callSuper = false)
public class GlovesSlot extends EquippedItems {

    public GlovesSlot(String actorId, ItemInstance itemInstance) {
        super(actorId, itemInstance, ItemType.GLOVES.getType());
    }
}
