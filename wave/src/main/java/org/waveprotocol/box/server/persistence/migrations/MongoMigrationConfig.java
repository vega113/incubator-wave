package org.waveprotocol.box.server.persistence.migrations;

import com.typesafe.config.Config;

public final class MongoMigrationConfig {
  public static final String CHANGESET_PACKAGE =
      "org.waveprotocol.box.server.persistence.migrations.changesets";

  private final String databaseName;
  private final boolean mongoMigrationEnabled;
  private final boolean mongoDeltaStoreEnabled;
  private final boolean mongoSnapshotStoreEnabled;
  private final boolean mongoContactMessageStoreEnabled;
  private final boolean mongoAnalyticsStoreEnabled;

  public MongoMigrationConfig(Config config) {
    String mongoDriver = config.hasPath("core.mongodb_driver")
        ? config.getString("core.mongodb_driver") : "v2";
    boolean v4Driver = "v4".equalsIgnoreCase(mongoDriver);
    boolean coreMongoBacked =
        isMongoStore(config, "core.signer_info_store_type")
            || isMongoStore(config, "core.attachment_store_type")
            || isMongoStore(config, "core.account_store_type")
            || isMongoStore(config, "core.delta_store_type")
            || isMongoStore(config, "core.contact_store_type");

    this.databaseName = config.getString("core.mongodb_database");
    this.mongoMigrationEnabled = v4Driver && coreMongoBacked;
    this.mongoDeltaStoreEnabled = v4Driver && isMongoStore(config, "core.delta_store_type");
    this.mongoSnapshotStoreEnabled = mongoDeltaStoreEnabled;
    this.mongoContactMessageStoreEnabled =
        v4Driver && isMongoStore(config, "core.account_store_type");
    this.mongoAnalyticsStoreEnabled =
        v4Driver
            && isMongoStore(config, "core.account_store_type")
            && config.hasPath("core.analytics_counters_enabled")
            && config.getBoolean("core.analytics_counters_enabled");
  }

  public String getDatabaseName() {
    return databaseName;
  }

  public boolean isMongoMigrationEnabled() {
    return mongoMigrationEnabled;
  }

  public boolean isMongoDeltaStoreEnabled() {
    return mongoDeltaStoreEnabled;
  }

  public boolean isMongoSnapshotStoreEnabled() {
    return mongoSnapshotStoreEnabled;
  }

  public boolean isMongoContactMessageStoreEnabled() {
    return mongoContactMessageStoreEnabled;
  }

  public boolean isMongoAnalyticsStoreEnabled() {
    return mongoAnalyticsStoreEnabled;
  }

  public String getChangesetPackage() {
    return CHANGESET_PACKAGE;
  }

  private static boolean isMongoStore(Config config, String path) {
    return config.hasPath(path) && "mongodb".equalsIgnoreCase(config.getString(path));
  }
}
