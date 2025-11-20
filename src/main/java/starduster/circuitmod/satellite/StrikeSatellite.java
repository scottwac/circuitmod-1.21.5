package starduster.circuitmod.satellite;

import net.minecraft.entity.TntEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Offensive satellite capable of launching TNT bombardments.
 */
public class StrikeSatellite extends Satellite {
    private static final int DEFAULT_RADIUS = 5;
    private static final int DEFAULT_POWER = 8;
    private static final int BASE_COOLDOWN_TICKS = 20 * 60 * 5; // 5 minutes
    private static final int COOLDOWN_MULTIPLIER = 5;
    private static final int MAX_COOLDOWN_TICKS = BASE_COOLDOWN_TICKS * COOLDOWN_MULTIPLIER;
    private static final long DEFAULT_LIFETIME_MILLIS = TimeUnit.DAYS.toMillis(3);
    private static final Map<String, Integer> TASK_COOLDOWNS = Map.of(
        "strike", MAX_COOLDOWN_TICKS
    );

    private final int strikeRadius;
    private final int strikePower;

    public StrikeSatellite(String accessCode) {
        this(UUID.randomUUID(), accessCode, MAX_COOLDOWN_TICKS, 0, true, DEFAULT_RADIUS, DEFAULT_POWER,
            System.currentTimeMillis(), DEFAULT_LIFETIME_MILLIS);
    }

    private StrikeSatellite(UUID id, String accessCode, int maxCooldown, int rechargeTicks, boolean active,
                            int radius, int power, long createdAtMillis, long lifetimeMillis) {
        super(id, accessCode, maxCooldown, rechargeTicks, active, createdAtMillis, lifetimeMillis);
        this.strikeRadius = radius;
        this.strikePower = power;
    }

    public static StrikeSatellite fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        UUID id = readUuid(nbt, "Id");
        String code = readString(nbt, "AccessCode", "UNKNOWN");
        int recharge = readInt(nbt, "RechargeTicks", 0);
        int maxRecharge = readInt(nbt, "MaxRecharge", MAX_COOLDOWN_TICKS);
        boolean active = readBoolean(nbt, "Active", true);
        int radius = readInt(nbt, "StrikeRadius", DEFAULT_RADIUS);
        int power = readInt(nbt, "StrikePower", DEFAULT_POWER);
        long createdAt = readLong(nbt, "CreatedAtMillis", System.currentTimeMillis());
        long lifetime = readLong(nbt, "LifetimeMillis", DEFAULT_LIFETIME_MILLIS);
        return new StrikeSatellite(id, code, maxRecharge, recharge, active, radius, power, createdAt, lifetime);
    }

    @Override
    public String getTypeId() {
        return "strike";
    }

    @Override
    public SatelliteResult executeSatelliteCommand(String command, String[] args, ServerWorld world, BlockPos terminalPos) {
        if (!command.equalsIgnoreCase("strike")) {
            return SatelliteResult.failure("Strike satellite does not recognize command '" + command + "'");
        }

        if (!isReady()) {
            int seconds = Math.max(1, getRechargeTicks() / 20);
            return SatelliteResult.failure("Satellite charging. Ready in " + seconds + "s.");
        }

        if (args.length < 3) {
            return SatelliteResult.failure("Usage: strike <x> <y> <z>");
        }

        BlockPos targetPos;
        try {
            int x = Integer.parseInt(args[0]);
            int y = Integer.parseInt(args[1]);
            int z = Integer.parseInt(args[2]);
            targetPos = new BlockPos(x, y, z);
        } catch (NumberFormatException ex) {
            return SatelliteResult.failure("Coordinates must be integers.");
        }

        runStrike(world, targetPos);
        startRecharge("strike", TASK_COOLDOWNS);
        return SatelliteResult.success("Orbital strike inbound at " + targetPos.getX() + ", " + targetPos.getY() + ", " + targetPos.getZ());
    }

    private void runStrike(ServerWorld world, BlockPos target) {
        for (int i = 0; i < strikePower; i++) {
            double offsetX = (world.random.nextDouble() - 0.5) * strikeRadius * 2;
            double offsetZ = (world.random.nextDouble() - 0.5) * strikeRadius * 2;
            double spawnY = target.getY() + 30 + world.random.nextInt(15);
            Vec3d spawnPos = new Vec3d(target.getX() + 0.5 + offsetX, spawnY, target.getZ() + 0.5 + offsetZ);
            TntEntity tnt = new TntEntity(world, spawnPos.x, spawnPos.y, spawnPos.z, null);
            tnt.setFuse(40 + world.random.nextInt(20));
            world.spawnEntity(tnt);
        }
    }

    @Override
    protected void writeCustomData(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        nbt.putInt("StrikeRadius", strikeRadius);
        nbt.putInt("StrikePower", strikePower);
    }

    @Override
    public String describe() {
        return "STRIKE Satellite\n" +
            "  Radius : " + strikeRadius + "\n" +
            "  Payload: " + strikePower + " TNT\n" +
            "  " + getStatusLine();
    }
}

