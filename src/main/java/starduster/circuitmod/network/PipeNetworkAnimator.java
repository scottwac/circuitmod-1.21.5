package starduster.circuitmod.network;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import starduster.circuitmod.Circuitmod;

import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Enhanced animator for continuous item movement through pipe networks.
 * Updated to work with hop-by-hop system while maintaining backwards compatibility.
 */
public class PipeNetworkAnimator {
    
    // Track recent animations to prevent duplicates
    private static final ConcurrentHashMap<String, Long> recentAnimations = new ConcurrentHashMap<>();
    private static final long ANIMATION_COOLDOWN = 3; // Minimum ticks between same animation
    
    // Track item paths for continuous animations (deprecated but kept for compatibility)
    private static final ConcurrentHashMap<String, ItemPath> activePaths = new ConcurrentHashMap<>();
    
    /**
     * Represents a complete item path through the pipe network
     */
    public static class ItemPath {
        public final ItemStack item;
        public final List<BlockPos> waypoints;
        public final long startTime;
        public final int totalDuration;
        
        public ItemPath(ItemStack item, List<BlockPos> waypoints, long startTime, int totalDuration) {
            this.item = item;
            this.waypoints = new ArrayList<>(waypoints);
            this.startTime = startTime;
            this.totalDuration = totalDuration;
        }
    }
    
    /**
     * DEPRECATED: Start tracking a new item path for continuous animation.
     * This method is kept for backwards compatibility but converted to hop-by-hop.
     */
    @Deprecated
    public static void startItemPath(ServerWorld world, ItemStack stack, BlockPos startPos, List<BlockPos> path) {
        if (world == null || stack == null || stack.isEmpty() || path == null || path.size() < 2) {
            return;
        }
        
        Circuitmod.LOGGER.warn("[ANIMATOR] startItemPath is deprecated in hop-by-hop system. " +
            "Converting to single hop animation. Path size: {}", path.size());
        
        // Convert to simple hop animation for the first segment only
        sendMoveAnimation(world, stack, path.get(0), path.get(1), 8);
    }
    
    /**
     * DEPRECATED: Send a continuous animation that follows the complete item path.
     * Kept for backwards compatibility but not recommended for hop-by-hop system.
     */
    @Deprecated
    private static void sendContinuousPathAnimation(ServerWorld world, ItemStack stack, List<BlockPos> path, long startTime, int duration) {
        Circuitmod.LOGGER.warn("[ANIMATOR] sendContinuousPathAnimation is deprecated - use sendMoveAnimation for individual hops");
        
        // Convert to hop-by-hop animations
        if (path.size() >= 2) {
            sendMoveAnimation(world, stack, path.get(0), path.get(1), 8);
        }
    }
    
    /**
     * Send an item move animation for a single hop - PRIMARY METHOD for hop-by-hop system.
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
        
        // Send to ClientNetworkAnimator directly for integrated servers
        try {
            ClientNetworkAnimator.addAnimation(stack, from, to, startTick, durationTicks);
            Circuitmod.LOGGER.debug("[ANIMATOR] Added animation to ClientNetworkAnimator");
        } catch (Exception e) {
            Circuitmod.LOGGER.debug("[ANIMATOR] Could not directly add to ClientNetworkAnimator (dedicated server): {}", e.getMessage());
        }
        
        // TODO: For dedicated servers, you would send packets to clients here
        // For now, we'll just use the direct approach for integrated servers
        
        /*
        // Send to all players tracking the source position (for dedicated servers)
        int playerCount = 0;
        for (ServerPlayerEntity player : PlayerLookup.tracking(world, from)) {
            try {
                // ModNetworking.sendItemMoveAnimation(player, stack, from, to, startTick, durationTicks);
                playerCount++;
            } catch (Exception e) {
                Circuitmod.LOGGER.error("[ANIMATOR] Failed to send animation to player {}: {}", 
                    player.getName().getString(), e.getMessage());
            }
        }
        
        if (playerCount > 0) {
            Circuitmod.LOGGER.debug("[ANIMATOR] Sent animation to {} players", playerCount);
        }
        */
    }
    
    /**
     * Clean up old animation entries to prevent memory leaks.
     */
    private static void cleanupOldAnimations(long currentTime) {
        recentAnimations.entrySet().removeIf(entry -> 
            (currentTime - entry.getValue()) > 200); // Remove entries older than 10 seconds
        
        // Also clean up old paths
        activePaths.entrySet().removeIf(entry -> 
            (currentTime - entry.getValue().startTime) > 200);
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
        activePaths.clear();
        
        // Also clear client animations
        try {
            ClientNetworkAnimator.clearAllAnimations();
        } catch (Exception e) {
            Circuitmod.LOGGER.debug("[ANIMATOR] Could not clear ClientNetworkAnimator (dedicated server): {}", e.getMessage());
        }
        
        Circuitmod.LOGGER.debug("[ANIMATOR] Cleared all animation tracking");
    }
}