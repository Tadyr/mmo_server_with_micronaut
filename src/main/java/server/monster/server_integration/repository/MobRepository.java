package server.monster.server_integration.repository;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import server.common.configuration.MongoConfiguration;
import server.common.dto.Location;
import server.common.dto.Motion;
import server.common.mongo.query.MongoDbQueryHelper;
import server.monster.server_integration.model.Monster;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;

@Slf4j
@Singleton
@Repository
public class MobRepository {

    MongoConfiguration configuration;
    MongoClient mongoClient;
    MongoCollection<Monster> mobMotionMongoCollection;

    public MobRepository(MongoConfiguration configuration, MongoClient mongoClient) {
        this.configuration = configuration;
        this.mongoClient = mongoClient;
        prepareCollections();
    }

    public Single<Monster> findMobInstance(String actorId) {
        return Single.fromPublisher(
                mobMotionMongoCollection.find(eq("actorId", actorId)));
    }

    public Single<Monster> findMobById(String mobId) {
        return Single.fromPublisher(mobMotionMongoCollection.find(eq("mobId", mobId)));
    }

    public Single<InsertOneResult> insertMobInstance(Monster mobInstance) {
        return Single.fromPublisher(mobMotionMongoCollection.insertOne(mobInstance));
    }


    public Single<Monster> updateMotionOnly(String actorId, Motion motion) {
        return Single.fromPublisher(
                mobMotionMongoCollection.findOneAndUpdate(
                        eq("actorId", actorId),
                        Updates.combine(
                                Updates.set("motion", motion),
                                Updates.set(
                                        "updatedAt",
                                        Instant.now().truncatedTo(ChronoUnit.MICROS)))));
    }

    public Single<DeleteResult> deleteMobInstance(String actorId) {
        return Single.fromPublisher(
                mobMotionMongoCollection.deleteOne(eq("actorId", actorId)));
    }

    public Single<List<Monster>> getMobsByInstanceIds(Set<String> actorIds) {
        return Flowable.fromPublisher(
                        mobMotionMongoCollection.find(in("actorId", actorIds)))
                .toList();
    }

    public Single<List<Monster>> getMobsNearby(Location location) {
        return MongoDbQueryHelper.nearbyMobMotionFinder(mobMotionMongoCollection, location, 2000);
    }

    public Single<DeleteResult> deleteMobsNotUpdatedWithin(Instant time) {
        return Single.fromPublisher(
                mobMotionMongoCollection.deleteMany(Filters.lt("updatedAt", time)));
    }

    private void prepareCollections() {
        this.mobMotionMongoCollection =
                mongoClient
                        .getDatabase(configuration.getDatabaseName())
                        .getCollection(configuration.getMobInstance(), Monster.class);
    }
}
