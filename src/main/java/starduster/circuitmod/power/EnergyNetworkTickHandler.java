package starduster.circuitmod.power;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import starduster.circuitmod.Circuitmod;

/**
 * Tick handler for energy networks that performs periodic validation and recovery.
 * This helps prevent network desync issues that can occur when chunks are unloaded and reloaded.
 */
public class EnergyNetworkTickHandler {
    
    // Counter for periodic operations
    private static int tickCounter = 0;
    
    // Validation interval (every 10 seconds = 200 ticks)
    private static final int VALIDATION_INTERVAL = 200;
    
    // Global recovery interval (every 60 seconds = 1200 ticks)
    private static final int RECOVERY_INTERVAL = 1200;
    
    /**
     * Initialize the energy network tick handler
     */
    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(EnergyNetworkTickHandler::onServerTick);
    }
    
    /**
     * Called every server tick to maintain energy network integrity
     */
    private static void onServerTick(MinecraftServer server) {
        tickCounter++;
        
        // Skip if we're still in startup mode
        if (EnergyNetwork.startupMode) {
            return;
        }
        
        // Periodic network validation (every 10 seconds)
        if (tickCounter % VALIDATION_INTERVAL == 0) {
            performPeriodicValidation(server);
        }
        
        // Global recovery operations (every 60 seconds)
        if (tickCounter % RECOVERY_INTERVAL == 0) {
            performGlobalRecovery(server);
        }
    }
    
    /**
     * Performs periodic validation of all energy networks
     */
    private static void performPeriodicValidation(MinecraftServer server) {
        try {
            int totalRepaired = 0;
            
            // Validate networks in all loaded worlds
            for (ServerWorld world : server.getWorlds()) {
                int repaired = EnergyNetworkManager.validateAllNetworks(world);
                totalRepaired += repaired;
            }
            
            if (totalRepaired > 0) {
                Circuitmod.LOGGER.info("[ENERGY-TICK] Periodic validation: {} networks repaired across all worlds", totalRepaired);
            }
            
        } catch (Exception e) {
            Circuitmod.LOGGER.error("[ENERGY-TICK] Error during periodic validation", e);
        }
    }
    
    /**
     * Performs global recovery operations
     */
    private static void performGlobalRecovery(MinecraftServer server) {
        try {
            int totalRecovered = 0;
            
            // Perform global recovery in all loaded worlds
            for (ServerWorld world : server.getWorlds()) {
                int recovered = EnergyNetworkManager.performGlobalRecovery(world);
                totalRecovered += recovered;
            }
            
            if (totalRecovered > 0) {
                Circuitmod.LOGGER.info("[ENERGY-TICK] Global recovery: {} blocks reconnected across all worlds", totalRecovered);
            }
            
            // Log network statistics periodically
            String stats = EnergyNetworkManager.getNetworkStats();
            Circuitmod.LOGGER.debug("[ENERGY-TICK] Network status: {}", stats);
            
        } catch (Exception e) {
            Circuitmod.LOGGER.error("[ENERGY-TICK] Error during global recovery", e);
        }
    }
    
    /**
     * Gets the current tick counter
     */
    public static int getTickCounter() {
        return tickCounter;
    }
    
    /**
     * Resets the tick counter (useful for testing)
     */
    public static void resetTickCounter() {
        tickCounter = 0;
    }
}
