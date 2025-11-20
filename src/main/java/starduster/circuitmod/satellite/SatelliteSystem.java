package starduster.circuitmod.satellite;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import starduster.circuitmod.Circuitmod;

/**
 * Bootstraps the satellite system (registry + tick integration).
 */
public final class SatelliteSystem {
    private SatelliteSystem() {}

    public static void initialize() {
        // Register satellite types
        SatelliteRegistry.registerType("strike", StrikeSatellite::fromNbt);
        SatelliteRegistry.registerType("scan", ScanSatellite::fromNbt);
        SatelliteRegistry.registerType("mining", MiningSatellite::fromNbt);

        ServerTickEvents.END_WORLD_TICK.register(SatelliteSystem::tickSatellites);
        
        // Create test satellites on server start
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerWorld overworld = server.getWorld(World.OVERWORLD);
            if (overworld != null) {
                createTestSatellites(overworld);
            }
        });
    }

    private static void tickSatellites(ServerWorld world) {
        if (world.getRegistryKey() != World.OVERWORLD) {
            return;
        }
        SatelliteRegistry.get(world).tick();
    }
    
    /**
     * Creates test satellites with access codes 000, 001, and 002 if they don't already exist.
     * 000 = Strike Satellite
     * 001 = Scan Satellite
     * 002 = Mining Satellite
     */
    private static void createTestSatellites(ServerWorld world) {
        SatelliteRegistry registry = SatelliteRegistry.get(world);
        
        // Create test satellites with different types
        createTestSatellite(registry, "strike", "000");
        createTestSatellite(registry, "scan", "001");
        createTestSatellite(registry, "mining", "002");
    }
    
    private static void createTestSatellite(SatelliteRegistry registry, String type, String code) {
        if (registry.findByAccessCode(code).isEmpty()) {
            registry.createSatellite(type, code).ifPresentOrElse(
                satellite -> Circuitmod.LOGGER.info("Created test {} satellite with access code: {}", type.toUpperCase(), code),
                () -> Circuitmod.LOGGER.warn("Failed to create test {} satellite with access code: {}", type, code)
            );
        } else {
            Circuitmod.LOGGER.info("Test satellite with access code {} already exists", code);
        }
    }
}

