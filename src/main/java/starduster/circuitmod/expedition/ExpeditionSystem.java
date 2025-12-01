package starduster.circuitmod.expedition;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import starduster.circuitmod.Circuitmod;

/**
 * Bootstraps the expedition system (registry + tick integration).
 */
public final class ExpeditionSystem {
    private ExpeditionSystem() {}

    public static void initialize() {
        Circuitmod.LOGGER.info("[EXPEDITION] Initializing expedition system");
        
        // Initialize loot table
        ExpeditionLootTable.initialize();
        
        // Register tick handler for expedition updates
        ServerTickEvents.END_WORLD_TICK.register(ExpeditionSystem::tickExpeditions);
        
        Circuitmod.LOGGER.info("[EXPEDITION] Expedition system initialized");
    }

    private static void tickExpeditions(ServerWorld world) {
        // Only tick in overworld to avoid duplicate processing
        if (world.getRegistryKey() != World.OVERWORLD) {
            return;
        }
        
        // Only tick every 20 ticks (1 second) to reduce overhead
        if (world.getTime() % 20 != 0) {
            return;
        }
        
        ExpeditionRegistry.get(world).tick(world);
    }
}

