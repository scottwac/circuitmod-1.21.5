package starduster.circuitmod.satellite;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Base class for all satellites that can be controlled through the satellite terminal.
 */
public abstract class Satellite {
    private final UUID id;
    private final String accessCode;
    private final int maxRechargeTicks;
    private int rechargeTicks;
    private boolean active;
    private final long createdAtMillis;
    private final long lifetimeMillis;

    protected Satellite(String accessCode, int maxRechargeTicks, long lifetimeMillis) {
        this(UUID.randomUUID(), accessCode, maxRechargeTicks, 0, true, System.currentTimeMillis(), lifetimeMillis);
    }

    protected Satellite(UUID id, String accessCode, int maxRechargeTicks, int rechargeTicks, boolean active,
                        long createdAtMillis, long lifetimeMillis) {
        this.id = id;
        this.accessCode = accessCode;
        this.maxRechargeTicks = maxRechargeTicks;
        this.rechargeTicks = rechargeTicks;
        this.active = active;
        this.createdAtMillis = createdAtMillis;
        this.lifetimeMillis = lifetimeMillis;
    }

    public UUID getId() {
        return id;
    }

    public String getAccessCode() {
        return accessCode;
    }

    public int getRechargeTicks() {
        return rechargeTicks;
    }

    public int getMaxRechargeTicks() {
        return maxRechargeTicks;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isReady() {
        return active && rechargeTicks <= 0;
    }

    public void tick() {
        if (rechargeTicks > 0) {
            rechargeTicks--;
        }
    }

    protected void startRecharge() {
        this.rechargeTicks = this.maxRechargeTicks;
    }
    
    protected void startRecharge(String taskKey, Map<String, Integer> taskCooldowns) {
        int ticks = taskCooldowns.getOrDefault(taskKey, maxRechargeTicks);
        this.rechargeTicks = ticks;
    }

    public void deactivate() {
        this.active = false;
    }
    
    public double getPowerLevelPercent() {
        if (maxRechargeTicks <= 0) {
            return 100.0;
        }
        double remaining = Math.max(0, maxRechargeTicks - rechargeTicks);
        return Math.min(100.0, (remaining / (double) maxRechargeTicks) * 100.0);
    }
    
    public long getCreatedAtMillis() {
        return createdAtMillis;
    }
    
    public long getLifetimeMillis() {
        return lifetimeMillis;
    }
    
    public long getUptimeMillis(long nowMillis) {
        return Math.max(0, nowMillis - createdAtMillis);
    }
    
    public boolean hasExpired(long nowMillis) {
        if (lifetimeMillis <= 0) {
            return false;
        }
        return nowMillis - createdAtMillis >= lifetimeMillis;
    }
    
    public double getHealthPercent(long nowMillis) {
        if (lifetimeMillis <= 0) {
            return active ? 100.0 : 0.0;
        }
        double remaining = Math.max(0, lifetimeMillis - getUptimeMillis(nowMillis));
        return Math.min(100.0, (remaining / (double) lifetimeMillis) * 100.0);
    }

    public String getStatusLine() {
        if (!active) {
            return "Status: OFFLINE";
        }
        if (isReady()) {
            return "Status: READY | Cooldown: 0s";
        }
        int seconds = (int) Math.ceil(rechargeTicks / 20.0);
        return "Status: RECHARGING (" + seconds + "s)";
    }

    public String describe() {
        return formatLabel() + "\n  " + getStatusLine();
    }

    /**
     * @return unique identifier string for deserialisation.
     */
    public abstract String getTypeId();

    /**
     * Execute a satellite-specific command.
     */
    public abstract SatelliteResult executeSatelliteCommand(String command, String[] args, ServerWorld world, BlockPos terminalPos);

    /**
     * Write shared satellite data.
     */
    public NbtCompound writeNbt(RegistryWrapper.WrapperLookup registries) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("Id", id.toString());
        nbt.putString("AccessCode", accessCode);
        nbt.putInt("RechargeTicks", rechargeTicks);
        nbt.putInt("MaxRecharge", maxRechargeTicks);
        nbt.putBoolean("Active", active);
        nbt.putLong("CreatedAtMillis", createdAtMillis);
        nbt.putLong("LifetimeMillis", lifetimeMillis);
        writeCustomData(nbt, registries);
        return nbt;
    }

    protected abstract void writeCustomData(NbtCompound nbt, RegistryWrapper.WrapperLookup registries);

    protected static UUID readUuid(NbtCompound nbt, String key) {
        return nbt.getString(key).map(s -> {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException e) {
                return UUID.randomUUID();
            }
        }).orElse(UUID.randomUUID());
    }

    protected static String readString(NbtCompound nbt, String key, String fallback) {
        return nbt.getString(key).orElse(fallback);
    }

    protected static int readInt(NbtCompound nbt, String key, int fallback) {
        return nbt.getInt(key, fallback);
    }
    
    protected static long readLong(NbtCompound nbt, String key, long fallback) {
        return nbt.getLong(key, fallback);
    }

    protected static boolean readBoolean(NbtCompound nbt, String key, boolean fallback) {
        return nbt.getBoolean(key, fallback);
    }

    /**
     * Helper result record for satellite executions.
     */
    public record SatelliteResult(boolean success, String message) {
        public static SatelliteResult success(String message) {
            return new SatelliteResult(true, message);
        }

        public static SatelliteResult failure(String message) {
            return new SatelliteResult(false, message);
        }
    }

    public String formatLabel() {
        return getTypeId().toUpperCase(Locale.ROOT) + " | Access " + accessCode;
    }
}

