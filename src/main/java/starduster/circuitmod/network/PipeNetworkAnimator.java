package starduster.circuitmod.network;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import starduster.circuitmod.Circuitmod;

/**
 * Simplified animator for hop-by-hop item movement.
 * Each animation represents one hop from pipe to pipe or pipe to inventory.
 */
public class PipeNetworkAnimator {
    
    /**
     * Send an item move animation for a single hop.
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
        
        long startTick = world.getTime();
        
        // Only log occasionally to prevent spam, or for debugging
        if (world.getTime() % 100 == 0) {
            Circuitmod.LOGGER.debug("[ANIMATOR] Animating {} from {} to {} (duration: {} ticks)",
                stack.getItem().getName().getString(), from, to, durationTicks);
        }
        
        // Send to all players tracking the source position
        for (ServerPlayerEntity player : PlayerLookup.tracking(world, from)) {
            try {
                ModNetworking.sendItemMoveAnimation(player, stack, from, to, startTick, durationTicks);
            } catch (Exception e) {
                Circuitmod.LOGGER.error("[ANIMATOR] Failed to send animation to player {}: {}", 
                    player.getName().getString(), e.getMessage());
            }
        }
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
}