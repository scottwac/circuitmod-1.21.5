package starduster.circuitmod.item;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import starduster.circuitmod.effect.ModStatusEffects;
import starduster.circuitmod.effect.PulseVelocityStorage;

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
            // Check if player has the status effect
            if (player.hasStatusEffect(ModStatusEffects.PULSE_VELOCITY)) {
                handlePulsePlayer(player);
            } 
            // Cleanup: If player has velocity data but no status effect, clean it up
            else if (PulseVelocityStorage.hasVelocity(player)) {
                System.out.println("[PULSE-STICK-DEBUG] Cleaning up orphaned velocity data for: " + player.getName().getString());
                PulseVelocityStorage.clearVelocity(player);
            }
        }
    }
    
    /**
     * Handles a player under pulse effect
     */
    private static void handlePulsePlayer(PlayerEntity player) {
        Vec3d pulseVelocity = PulseStickItem.getPulseVelocity(player);
        
        System.out.println("[PULSE-STICK-DEBUG] handlePulsePlayer called for: " + player.getName().getString() + 
                          " | Stored velocity: " + pulseVelocity + 
                          " | Has effect: " + player.hasStatusEffect(ModStatusEffects.PULSE_VELOCITY));
        
        if (pulseVelocity.equals(Vec3d.ZERO)) {
            System.out.println("[PULSE-STICK-DEBUG] Pulse velocity is zero, clearing effect");
            PulseStickItem.clearPulseVelocity(player);
            return;
        }
        
        // Check if player is on ground, touching blocks, or sneaking to stop
        if (player.isOnGround() || player.horizontalCollision || player.isSneaking()) {
            // Stop the pulse effect when hitting something or sneaking
            System.out.println("[PULSE-STICK-DEBUG] Stopping pulse - OnGround: " + player.isOnGround() + 
                              " | HorizontalCollision: " + player.horizontalCollision + 
                              " | Sneaking: " + player.isSneaking());
            PulseStickItem.clearPulseVelocity(player);
            return;
        }
        
        // Maintain horizontal velocity while in air, compensating for air resistance
        Vec3d currentVelocity = player.getVelocity();
        
        // Apply a boost factor to compensate for Minecraft's air resistance
        // The 1.02 multiplier counters the ~2% velocity loss per tick
        double boostFactor = 1.02;
        
        Vec3d newVelocity = new Vec3d(
            pulseVelocity.x * boostFactor,  // Boosted horizontal X velocity
            currentVelocity.y,              // Keep natural Y velocity (gravity/jumping)
            pulseVelocity.z * boostFactor   // Boosted horizontal Z velocity
        );
        
        // Debug: Print velocity information every 10 ticks (twice per second)
        if (player.age % 10 == 0) {
            double currentHorizontalSpeed = Math.sqrt(currentVelocity.x * currentVelocity.x + currentVelocity.z * currentVelocity.z);
            double newHorizontalSpeed = Math.sqrt(newVelocity.x * newVelocity.x + newVelocity.z * newVelocity.z);
            double storedHorizontalSpeed = Math.sqrt(pulseVelocity.x * pulseVelocity.x + pulseVelocity.z * pulseVelocity.z);
            
            System.out.printf("[PULSE-STICK] %s - Current: %.3f | Stored: %.3f | Applied: %.3f | Y: %.3f%n", 
                player.getName().getString(),
                currentHorizontalSpeed,
                storedHorizontalSpeed, 
                newHorizontalSpeed,
                currentVelocity.y
            );
        }
        
        player.setVelocity(newVelocity);
        player.velocityModified = true;
    }
} 