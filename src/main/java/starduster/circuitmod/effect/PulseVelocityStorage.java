package starduster.circuitmod.effect;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import starduster.circuitmod.Circuitmod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Storage system for pulse velocity data. 
 * This is used alongside the PulseVelocityEffect to store the actual velocity vector.
 */
public class PulseVelocityStorage {
    
    // Map to store velocity data by player UUID
    private static final Map<UUID, Vec3d> PLAYER_VELOCITIES = new HashMap<>();
    
    /**
     * Store velocity for a player
     */
    public static void setVelocity(PlayerEntity player, Vec3d velocity) {
        PLAYER_VELOCITIES.put(player.getUuid(), velocity);
        Circuitmod.LOGGER.info("[PULSE-VELOCITY-STORAGE] Stored velocity {} for player {}", velocity, player.getName().getString());
    }
    
    /**
     * Get stored velocity for a player
     */
    public static Vec3d getVelocity(PlayerEntity player) {
        return PLAYER_VELOCITIES.getOrDefault(player.getUuid(), Vec3d.ZERO);
    }
    
    /**
     * Remove stored velocity for a player
     */
    public static void clearVelocity(PlayerEntity player) {
        PLAYER_VELOCITIES.remove(player.getUuid());
        Circuitmod.LOGGER.info("[PULSE-VELOCITY-STORAGE] Cleared velocity for player {}", player.getName().getString());
    }
    
    /**
     * Check if a player has stored velocity
     */
    public static boolean hasVelocity(PlayerEntity player) {
        return PLAYER_VELOCITIES.containsKey(player.getUuid());
    }
} 