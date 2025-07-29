package starduster.circuitmod.network;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import starduster.circuitmod.Circuitmod;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Simplified animator for hop-by-hop item movement.
 * Each animation represents one hop from pipe to pipe or pipe to inventory.
 * Prevents duplicate animations for the same item movement.
 */
public class PipeNetworkAnimator {
    
    // Track recent animations to prevent duplicates
    private static final ConcurrentHashMap<String, Long> recentAnimations = new ConcurrentHashMap<>();
    private static final long ANIMATION_COOLDOWN = 3; // Minimum ticks between same animation
    
    /**
     * Send an item move animation for a single hop.
     * Includes duplicate prevention to avoid multiple animations for the same movement.
     * 
     * @param world The server world
     * @param stack The item stack being moved
     * @param from The starting position
     * @param to The ending position
     * @param durationTicks The duration of the animation in ticks
     */
    public static void sendMoveAnimation(ServerWorld world, ItemStack stack, BlockPos from, BlockPos to, int durationTicks) {
        // Validate inputs
        if (world == null || stack == null || stack.isEmpty() || from == null || to == null) {
            Circuitmod.LOGGER.warn("[ANIMATOR] Invalid animation parameters - skipping");
            return;
        }
        
        // Don't animate if from and to are the same position
        if (from.equals(to)) {
            return;
        }
        
        // Create unique key for this animation to prevent duplicates
        String animationKey = from.toString() + "->" + to.toString() + ":" + stack.getItem().toString();
        long currentTime = world.getTime();
        
        // Check if we recently sent this same animation
        Long lastAnimationTime = recentAnimations.get(animationKey);
        if (lastAnimationTime != null && (currentTime - lastAnimationTime) < ANIMATION_COOLDOWN) {
            Circuitmod.LOGGER.debug("[ANIMATOR] Skipping duplicate animation: {} (last sent {} ticks ago)", 
                animationKey, currentTime - lastAnimationTime);
            return;
        }
        
        // Record this animation
        recentAnimations.put(animationKey, currentTime);
        
        // Clean up old entries periodically (every 100 ticks)
        if (currentTime % 100 == 0) {
            cleanupOldAnimations(currentTime);
        }
        
        long startTick = currentTime;
        
        // Debug logging
        Circuitmod.LOGGER.debug("[ANIMATOR] Animating {} from {} to {} (duration: {} ticks, tick: {})",
            stack.getItem().getName().getString(), from, to, durationTicks, startTick);
        
        // Send to all players tracking the source position
        int playerCount = 0;
        for (ServerPlayerEntity player : PlayerLookup.tracking(world, from)) {
            try {
                ModNetworking.sendItemMoveAnimation(player, stack, from, to, startTick, durationTicks);
                playerCount++;
            } catch (Exception e) {
                Circuitmod.LOGGER.error("[ANIMATOR] Failed to send animation to player {}: {}", 
                    player.getName().getString(), e.getMessage());
            }
        }
        
        if (playerCount > 0) {
            Circuitmod.LOGGER.debug("[ANIMATOR] Sent animation to {} players", playerCount);
        }
    }
    
    /**
     * Clean up old animation entries to prevent memory leaks.
     */
    private static void cleanupOldAnimations(long currentTime) {
        recentAnimations.entrySet().removeIf(entry -> 
            (currentTime - entry.getValue()) > 200); // Remove entries older than 10 seconds
    }
    
    /**
     * Send animation with standard timing for pipe-to-pipe movement.
     */
    public static void sendPipeToPipeAnimation(ServerWorld world, ItemStack stack, BlockPos from, BlockPos to) {
        sendMoveAnimation(world, stack, from, to, 8); // 8 ticks for pipe movement
    }
    
    /**
     * Send animation with faster timing for extraction/insertion.
     */
    public static void sendExtractionAnimation(ServerWorld world, ItemStack stack, BlockPos from, BlockPos to) {
        sendMoveAnimation(world, stack, from, to, 5); // 5 ticks for extraction/insertion
    }
    
    /**
     * Force clear all animation tracking (for debugging or world unload).
     */
    public static void clearAnimationTracking() {
        recentAnimations.clear();
        Circuitmod.LOGGER.debug("[ANIMATOR] Cleared all animation tracking");
    }
}