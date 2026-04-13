package org.waveprotocol.box.server.persistence.migrations;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import io.mongock.driver.mongodb.sync.v4.driver.MongoSync4Driver;
import io.mongock.runner.core.executor.MongockRunner;
import io.mongock.runner.standalone.MongockStandalone;
import org.waveprotocol.box.server.persistence.mongodb4.Mongo4DbProvider;
import org.waveprotocol.wave.util.logging.Log;

public final class MongoMigrationRunner {
  private static final Log LOG = Log.get(MongoMigrationRunner.class);

  private final Mongo4DbProvider provider;
  private final MongoMigrationConfig migrationConfig;

  public MongoMigrationRunner(
      Mongo4DbProvider provider,
      MongoMigrationConfig migrationConfig) {
    this.provider = provider;
    this.migrationConfig = migrationConfig;
  }

  public void run() {
    MongoClient mongoClient = provider.provideMongoClient();
    MongoDatabase database = provider.provideMongoDatabase();
    MongoSync4Driver driver =
        MongoSync4Driver.withDefaultLock(mongoClient, migrationConfig.getDatabaseName());

    MongockRunner runner = MongockStandalone.builder()
        .setDriver(driver)
        .setTransactional(false)
        .addMigrationScanPackage(migrationConfig.getChangesetPackage())
        .addDependency(MongoDatabase.class, database)
        .addDependency(MongoMigrationConfig.class, migrationConfig)
        .setMigrationStartedListener(event -> LOG.info("Mongo migrations starting"))
        .setMigrationSuccessListener(event -> LOG.info("Mongo migrations completed successfully"))
        .setMigrationFailureListener(
            event -> LOG.severe("Mongo migrations failed", event.getException()))
        .buildRunner();

    runner.execute();
  }
}
