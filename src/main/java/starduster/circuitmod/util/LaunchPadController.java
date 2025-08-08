package starduster.circuitmod.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.PlayerEntity;

/**
 * Tracks players currently under launch pad boost to coordinate with other systems (e.g. gravity mixins).
 */
public final class LaunchPadController {
    private static final Map<UUID, Integer> boostingTicksRemaining = new ConcurrentHashMap<>();

    private LaunchPadController() {}

    public static void startBoost(PlayerEntity player, int ticks) {
        boostingTicksRemaining.put(player.getUuid(), Math.max(ticks, 1));
    }

    public static boolean isBoosting(PlayerEntity player) {
        return boostingTicksRemaining.getOrDefault(player.getUuid(), 0) > 0;
    }

    public static void tick(PlayerEntity player) {
        UUID id = player.getUuid();
        boostingTicksRemaining.computeIfPresent(id, (k, v) -> v > 1 ? v - 1 : null);
    }

    public static void clear(PlayerEntity player) {
        boostingTicksRemaining.remove(player.getUuid());
    }
}

