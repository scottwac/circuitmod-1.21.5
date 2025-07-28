package starduster.circuitmod.item;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

public class PulseStickHandler {
    
    /**
     * Initialize the pulse stick tick handler
     */
    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(PulseStickHandler::onServerTick);
    }
    
    /**
     * Called every server tick to maintain pulse velocity
     */
    private static void onServerTick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (PulseStickItem.isPulseActive(player)) {
                handlePulsePlayer(player);
            }
        }
    }
    
    /**
     * Handles a player under pulse effect
     */
    private static void handlePulsePlayer(PlayerEntity player) {
        Vec3d pulseVelocity = PulseStickItem.getPulseVelocity(player);
        
        if (pulseVelocity.equals(Vec3d.ZERO)) {
            return;
        }
        
        // Check if player is on ground, touching blocks, or sneaking to stop
        if (player.isOnGround() || player.horizontalCollision || player.isSneaking()) {
            // Stop the pulse effect when hitting something or sneaking
            PulseStickItem.clearPulseVelocity(player);
            return;
        }
        
        // Maintain horizontal velocity while in air
        Vec3d currentVelocity = player.getVelocity();
        Vec3d newVelocity = new Vec3d(
            pulseVelocity.x,  // Use stored horizontal X velocity
            currentVelocity.y,  // Keep natural Y velocity (gravity/jumping)
            pulseVelocity.z   // Use stored horizontal Z velocity
        );
        
        player.setVelocity(newVelocity);
        player.velocityModified = true;
    }
} 