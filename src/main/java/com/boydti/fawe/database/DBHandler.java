package com.boydti.fawe.database;

import com.boydti.fawe.Fawe;
import com.sk89q.worldedit.world.World;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DBHandler {
    public final static DBHandler IMP = new DBHandler();

    private Map<String, RollbackDatabase> databases = new ConcurrentHashMap<>(8, 0.9f, 1);

    /**
     * Set once a database could not be opened. The usual cause is that no SQLite JDBC driver is on
     * the classpath (Bukkit ships one, Forge does not); retrying on every edit would only repeat the
     * failure, so the rollback database stays off until restart and the reason is logged once.
     */
    private volatile boolean unavailable;

    public RollbackDatabase getDatabase(World world) {
        return getDatabase(Fawe.imp().getWorldName(world));
    }

    public RollbackDatabase getDatabase(String world) {
        RollbackDatabase database = databases.get(world);
        if (database != null || unavailable) {
            return database;
        }
        try {
            database = new RollbackDatabase(world);
            databases.put(world, database);
            return database;
        } catch (Throwable e) {
            unavailable = true;
            // Console only: Fawe.debug would show this to whichever player's edit got here first
            Fawe.debugPlain("The FAWE rollback database (/frb, the inspect tool's database lookups) is unavailable: "
                    + e + ". It usually means no SQLite JDBC driver is installed; set history.use-database: false"
                    + " in the FAWE config to silence this.");
            return null;
        }
    }
}
