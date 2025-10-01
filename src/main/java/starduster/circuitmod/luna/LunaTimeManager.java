package starduster.circuitmod.luna;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages the extended lunar day/night cycle for the Luna dimension.
 * The Luna sun moves 8 times slower than the normal sun, creating a 192,000 tick full cycle.
 * Night time and stars only appear when the Luna sun sets below the horizon.
 */
public class LunaTimeManager {
    private static final Map<ServerWorld, LunaTimeManager> worldManagers = new HashMap<>();
    private static final long NORMAL_DAY_TIME = 12000;  // Normal daytime duration
    private static final long NORMAL_NIGHT_TIME = 12000; // Normal nighttime duration
    private static final long LUNA_DAY_TIME = 96000;    // 8x longer day (12000 * 8)
    private static final long LUNA_NIGHT_TIME = 96000;   // 8x longer night (12000 * 8)
    
    private final ServerWorld world;
    private DayPartTimeRate dayTimeRate;
    private DayPartTimeRate nightTimeRate;
    
    private LunaTimeManager(ServerWorld world) {
        this.world = world;
        this.dayTimeRate = new DayPartTimeRate(LUNA_DAY_TIME, NORMAL_DAY_TIME);
        this.nightTimeRate = new DayPartTimeRate(LUNA_NIGHT_TIME, NORMAL_NIGHT_TIME);
    }
    
    /**
     * Gets or creates the LunaTimeManager for the given world.
     */
    public static LunaTimeManager getInstance(ServerWorld world) {
        // Only apply to Luna dimension
        if (!isLunaDimension(world)) {
            return null;
        }
        
        LunaTimeManager manager = worldManagers.get(world);
        if (manager == null) {
            manager = new LunaTimeManager(world);
            worldManagers.put(world, manager);
            starduster.circuitmod.Circuitmod.LOGGER.info("[LUNA-TIME] Created new LunaTimeManager for Luna dimension. Day duration: {} ticks, Night duration: {} ticks", 
                LUNA_DAY_TIME, LUNA_NIGHT_TIME);
        }
        return manager;
    }
    
    /**
     * Clears the manager for a world (called on world unload).
     */
    public static void clear(ServerWorld world) {
        worldManagers.remove(world);
    }
    
    private static long lastLogTime = 0;
    
    /**
     * Called every tick to update the time in the Luna dimension.
     */
    public void tickTime() {
        if (isDay()) {
            dayTimeRate.apply(world);
        } else {
            nightTimeRate.apply(world);
        }
        
        // Log every 200 ticks (10 seconds) for debugging
        if (world.getTime() % 200 == 0 && world.getTime() != lastLogTime) {
            lastLogTime = world.getTime();
            starduster.circuitmod.Circuitmod.LOGGER.info("[LUNA-TIME] Time: {} | Day time: {} | Is Day: {} | Rate: increment={} every {} ticks", 
                world.getTime(), world.getTimeOfDay(), isDay(), getTimeRate().getIncrement(), getTimeRate().getIncrementModulus());
        }
    }
    
    /**
     * Checks if it's currently daytime in the Luna cycle (0-96000 of the 192,000 tick cycle).
     */
    public boolean isDay() {
        long lunaTime = world.getTimeOfDay() % (LUNA_DAY_TIME + LUNA_NIGHT_TIME); // 192,000 tick cycle
        return lunaTime < LUNA_DAY_TIME; // Day is 0-96000
    }
    
    /**
     * Checks if this manager is using custom time rates (always true for Luna).
     */
    public boolean isNormalTimeRate() {
        return false; // Luna always uses custom time rate
    }
    
    /**
     * Gets the current time rate based on day/night.
     */
    public DayPartTimeRate getTimeRate() {
        return isDay() ? dayTimeRate : nightTimeRate;
    }
    
    /**
     * Checks if the given world is the Luna dimension.
     */
    private static boolean isLunaDimension(ServerWorld world) {
        return world.getRegistryKey().getValue().equals(Identifier.of("circuitmod", "luna"));
    }
    
    /**
     * Saves manager data to NBT (for future persistence implementation).
     */
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putLong("daytime", dayTimeRate.duration);
        nbt.putLong("nighttime", nightTimeRate.duration);
        return nbt;
    }
    
    /**
     * Loads manager data from NBT (for future persistence implementation).
     */
    public void readNbt(NbtCompound nbt) {
        if (nbt.contains("daytime")) {
            long dayDuration = nbt.getLong("daytime", LUNA_DAY_TIME);
            this.dayTimeRate = new DayPartTimeRate(dayDuration, NORMAL_DAY_TIME);
        }
        
        if (nbt.contains("nighttime")) {
            long nightDuration = nbt.getLong("nighttime", LUNA_NIGHT_TIME);
            this.nightTimeRate = new DayPartTimeRate(nightDuration, NORMAL_NIGHT_TIME);
        }
    }
    
    /**
     * Represents the time rate for a portion of the day (day or night).
     */
    public static class DayPartTimeRate {
        private final long duration;           // How many server ticks this period should last
        private final long incrementModulus;   // How often to increment time
        private final double increment;        // How much to increment each time
        private final boolean isNormal;        // Whether this is normal speed
        private double dayTime = -1;          // Fractional day time tracker
        
        private DayPartTimeRate(long tickDuration, long normal) {
            this.isNormal = tickDuration == normal;
            this.duration = tickDuration;
            
            if (isNormal) {
                // Normal speed: increment by 1 every tick
                incrementModulus = 1;
                increment = 1;
            } else if (tickDuration < normal) {
                // Faster than normal: increment more than 1 per tick
                incrementModulus = 1;
                increment = (double) normal / tickDuration;
            } else {
                // Slower than normal: increment less than 1, or skip ticks
                double modulus = (double) tickDuration / normal;
                long roundedModulus;
                
                if (MathHelper.floor(modulus) != modulus) {
                    roundedModulus = MathHelper.lfloor(modulus);
                    increment = roundedModulus / modulus;
                } else {
                    roundedModulus = (long) modulus;
                    increment = 1;
                }
                incrementModulus = roundedModulus;
            }
        }
        
        private void apply(ServerWorld world) {
            long currentTime = MathHelper.lfloor(dayTime);
            if (currentTime != world.getTimeOfDay()) {
                dayTime = world.getTimeOfDay();
                currentTime = world.getTimeOfDay();
            }
            
            // Only increment on the right tick
            if (world.getTime() % incrementModulus == 0) {
                dayTime += increment;
                long newTime = MathHelper.lfloor(dayTime);
                
                if (newTime != currentTime) {
                    world.setTimeOfDay(newTime);
                }
            }
        }
        
        public long getDuration() {
            return duration;
        }
        
        public long getIncrementModulus() {
            return incrementModulus;
        }
        
        public double getIncrement() {
            return increment;
        }
    }
}

