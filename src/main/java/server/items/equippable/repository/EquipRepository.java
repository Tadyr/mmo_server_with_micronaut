package server.items.equippable.repository;

import static com.mongodb.client.model.Filters.*;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import server.common.configuration.MongoConfiguration;
import server.items.equippable.model.EquippedItems;

@Slf4j
@Singleton
public class EquipRepository {

    // This repository is connected to MongoDB
    MongoConfiguration playerCharacterConfiguration;
    MongoClient mongoClient;
    MongoCollection<EquippedItems> equippedItemsCollection;

    public EquipRepository(
            MongoConfiguration playerCharacterConfiguration, MongoClient mongoClient) {
        this.playerCharacterConfiguration = playerCharacterConfiguration;
        this.mongoClient = mongoClient;
        prepareCollections();
    }

    public Single<EquippedItems> insert(EquippedItems equippedItems) {
        return Single.fromPublisher(equippedItemsCollection.insertOne(equippedItems))
                .map(res -> equippedItems);
    }

    public Single<List<EquippedItems>> getEquippedItemsForCharacter(String actorId) {
        return Flowable.fromPublisher(equippedItemsCollection.find(eq("actorId", actorId)))
                .toList();
    }

    public Single<List<EquippedItems>> getEquippedItemsForCharacters(Set<String> actorIds) {
        return Flowable.fromPublisher(equippedItemsCollection.find(in("actorId", actorIds)))
                .toList();
    }

    public Maybe<EquippedItems> getCharacterItemSlot(String actorId, String slotType) {
        return Flowable.fromPublisher(
                        equippedItemsCollection.find(
                                and(eq("actorId", actorId), eq("category", slotType))))
                .firstElement();
    }

    public Single<DeleteResult> deleteEquippedItem(String itemInstanceId) {
        // TODO: Consider duplicating item instance ID as nested query is slower
        return Single.fromPublisher(
                equippedItemsCollection.deleteOne(
                        eq("itemInstance.itemInstanceId", itemInstanceId)));
    }

    private void prepareCollections() {
        this.equippedItemsCollection =
                mongoClient
                        .getDatabase(playerCharacterConfiguration.getDatabaseName())
                        .getCollection(
                                playerCharacterConfiguration.getEquipCollection(),
                                EquippedItems.class);
    }
}
