package server.items.inventory.service;

import com.mongodb.client.result.UpdateResult;
import io.reactivex.rxjava3.core.Single;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Update;
import server.common.dto.Location;
import server.common.dto.Location2D;
import server.items.inventory.model.Inventory;
import server.items.inventory.model.exceptions.InventoryException;
import server.items.inventory.model.response.GenericInventoryData;
import server.items.inventory.repository.InventoryRepository;
import server.items.model.DroppedItem;
import server.items.model.ItemInstance;
import server.items.service.ItemService;
import server.items.equippable.model.exceptions.EquipException;
import server.items.inventory.model.CharacterItem;

@Slf4j
@Singleton
public class InventoryService {

    @Inject InventoryRepository inventoryRepository;
    @Inject ItemService itemService;

    public Single<Inventory> pickupItem(GenericInventoryData request) {
        return itemService.getDroppedItemById(request.getDroppedItemId())
                .doOnError(e -> {
                    log.error("Failed to find dropped item, {}", e.getMessage());
                    throw e;
                })
                .flatMap(droppedItem -> {
                    ItemInstance instance = droppedItem.getItemInstance();
                    return inventoryRepository.getCharacterInventory(request.getCharacterName())
                            .doOnError(e -> log.error("Failed to get character inventory, {}", e.getMessage()))
                            .flatMap(inventory -> {
                                // check for example if inventory is full
                                List<CharacterItem> items = inventory.getCharacterItems();
                                Location2D position =
                                        getNextAvailableSlot(inventory.getMaxSize(), inventory.getCharacterItems());

                                if (position == null) {
                                    // no inventory slots left
                                    throw new InventoryException("No available slots in inventory");
                                }

                                CharacterItem newCharacterItem = new CharacterItem(request.getCharacterName(), position, instance);

                                items.add(newCharacterItem);

                                // delete the dropped item first (this is a x get) to prevent duplication
                                // TODO: make async..
                                itemService.deleteDroppedItem(request.getDroppedItemId()).blockingGet();
                                inventoryRepository.updateInventoryItems(request.getCharacterName(), items).blockingGet();

                                return getInventory(request.getCharacterName());
                            });
                });
    }

    public Single<List<CharacterItem>> unequipItem(String itemInstanceId, String characterName) {
        // this is basically finding the nearest slot and placing item there
        return getInventory(characterName)
                .doOnError(e -> log.error("Failed to get characters inventory, {}", e.getMessage()))
                .map(inventory -> {
                    Location2D loc =
                            getNextAvailableSlot(inventory.getMaxSize(), inventory.getCharacterItems());

                    if (loc == null) {
                        throw new EquipException("Inventory full to unequip item");
                    }

                    List<CharacterItem> items = inventory.getCharacterItems();

                    CharacterItem foundItem =
                            items.stream()
                                    .filter(i -> i.getItemInstance().getItemInstanceId().equals(itemInstanceId))
                                    .findFirst()
                                    .orElse(null);

                    if (foundItem == null) {
                        // this is unexpected, the item should exist in the inventory
                        log.error(
                                "Un-equip item unexpectedly failed for character {} and itemInstanceId {}",
                                characterName,
                                itemInstanceId);
                        throw new EquipException("Un-equip has unexpectedly failed");
                    }

                    foundItem.setLocation(loc);
                    // TODO: Make this async
                    inventoryRepository.updateInventoryItems(characterName, items).blockingGet();

                    return items;
                });
    }

    public Single<DroppedItem> dropItem(
            String characterName, Location2D inventoryLocation, Location location)
            throws InventoryException {
        return inventoryRepository.getCharacterInventory(characterName)
                .doOnError(e -> log.error("Failed to get character inventory, {}", e.getMessage()))
                .flatMap(inventory -> {
                    CharacterItem characterItem = getItemAtLocation(inventoryLocation, inventory);

                    if (characterItem == null) {
                        throw new InventoryException("character item not found");
                    }

                    List<CharacterItem> itemsList = inventory.getCharacterItems();
                    itemsList.remove(characterItem);
                    inventoryRepository.updateInventoryItems(characterName, itemsList);

                    // TODO: if dropItem fails, we need to revert the removal of item from inventory.
                    return itemService.dropExistingItem(
                            characterItem.getItemInstance().getItemInstanceId(), location);
                });

    }

    public Single<Inventory> getInventory(String characterName) {
        return inventoryRepository.getCharacterInventory(characterName);
    }

    public Single<UpdateResult> updateInventoryItems(String characterName, List<CharacterItem> characterItems) {
        return inventoryRepository.updateInventoryItems(characterName, characterItems);
    }

    public Single<Inventory> createInventoryForNewCharacter(String characterName) {
        Inventory inventory = new Inventory();

        inventory.setCharacterName(characterName);
        inventory.setCharacterItems(new ArrayList<>());
        inventory.setGold(0);
        inventory.setMaxSize(new Location2D(4, 10));

        return inventoryRepository.insert(inventory);
    }

    public Single<UpdateResult> updateInventoryMaxSize(Inventory inventory) {
        return inventoryRepository.updateInventoryMaxSize(inventory);
    }

    public Location2D getNextAvailableSlot(Location2D maxSize, List<CharacterItem> items) {
        // Implement this as per your requirement, based on position for example.
        //        Location2D maxSize = inventory.getMaxSize();
        //        List<CharacterItem> items = inventory.getCharacterItems();
        int[][] invArr = new int[maxSize.getX()][maxSize.getY()];

        items.forEach(
                i -> {
                    Location2D loc = i.getLocation();
                    // process only valid locations, ignore 'equipped' items
                    if (loc != null && loc.getX() > -1) {
                        invArr[loc.getX()][loc.getY()] = 1;
                    }
                });

        for (int x = 0; x < maxSize.getY(); x++) {
            for (int y = 0; y < maxSize.getX(); y++) {
                if (invArr[x][y] != 1) {
                    return new Location2D(x, y);
                }
            }
        }

        return null;
    }

    public void clearAllDataForCharacter(String characterName) {
        // This is for test purposes!
        inventoryRepository.deleteAllInventoryDataForCharacter(characterName);
    }

    private CharacterItem getItemAtLocation(Location2D location, Inventory inventory) {
        List<CharacterItem> items = inventory.getCharacterItems();

        Optional<CharacterItem> item =
                items.stream().filter(i -> i.getLocation().equals(location)).findFirst();

        if (item.isPresent()) {
            return item.get();
        } else {
            log.warn("item was not found in the inventory");
            return null;
        }
    }
}
