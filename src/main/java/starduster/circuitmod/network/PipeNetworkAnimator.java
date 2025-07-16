package starduster.circuitmod.network;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import starduster.circuitmod.Circuitmod;

/**
 * Helper class for sending item pipe network animations to clients
 */
public class PipeNetworkAnimator {
    
    /**
     * Send an item move animation to all players tracking the source position
     * 
     * @param world The server world
     * @param stack The item stack being moved
     * @param from The starting position
     * @param to The ending position
     * @param startTick The server tick when the animation starts
     * @param durationTicks The duration of the animation in ticks
     */
    public static void sendMoveAnimation(ServerWorld world, ItemStack stack, BlockPos from, BlockPos to, long startTick, int durationTicks) {
        Circuitmod.LOGGER.info("[PIPE-ANIMATOR] Sending move animation: {} from {} to {} at tick {}", 
            stack.getItem().getName().getString(), from, to, startTick);
        
        // Send to all players tracking the source position
        for (ServerPlayerEntity player : PlayerLookup.tracking(world, from)) {
            ModNetworking.sendItemMoveAnimation(player, stack, from, to, startTick, durationTicks);
        }
    }
    
    /**
     * Send an item move animation to all players tracking the source position
     * Uses current world time as start tick
     * 
     * @param world The server world
     * @param stack The item stack being moved
     * @param from The starting position
     * @param to The ending position
     * @param durationTicks The duration of the animation in ticks
     */
    public static void sendMoveAnimation(ServerWorld world, ItemStack stack, BlockPos from, BlockPos to, int durationTicks) {
        long startTick = world.getTime();
        sendMoveAnimation(world, stack, from, to, startTick, durationTicks);
    }
} 