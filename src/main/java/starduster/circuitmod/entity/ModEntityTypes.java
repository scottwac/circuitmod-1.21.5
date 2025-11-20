package starduster.circuitmod.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;

public class ModEntityTypes {
    
    public static final EntityType<MiningExplosiveEntity> MINING_EXPLOSIVE = Registry.register(
        Registries.ENTITY_TYPE,
        Identifier.of(Circuitmod.MOD_ID, "mining_explosive"),
        EntityType.Builder.<MiningExplosiveEntity>create(MiningExplosiveEntity::new, SpawnGroup.MISC)
            .dimensions(0.25F, 0.25F) // Same size as snowball
            .maxTrackingRange(4)
            .trackingTickInterval(10)
            .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Circuitmod.MOD_ID, "mining_explosive")))
    );
    
    public static final EntityType<HovercraftEntity> HOVERCRAFT = Registry.register(
        Registries.ENTITY_TYPE,
        Identifier.of(Circuitmod.MOD_ID, "hovercraft"),
        EntityType.Builder.<HovercraftEntity>create(HovercraftEntity::new, SpawnGroup.MISC)
            .dimensions(3.0F, 3.0F) // Large size to match rocket model
            .maxTrackingRange(10)
            .trackingTickInterval(3)
            .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Circuitmod.MOD_ID, "hovercraft")))
    );
    
    public static final EntityType<MissileEntity> MISSILE = Registry.register(
        Registries.ENTITY_TYPE,
        Identifier.of(Circuitmod.MOD_ID, "missile"),
        EntityType.Builder.<MissileEntity>create(MissileEntity::new, SpawnGroup.MISC)
            .dimensions(0.5F, 1.5F) // Missile dimensions (width, height)
            .maxTrackingRange(80) // Track from far away
            .trackingTickInterval(1) // Update frequently for smooth movement
            .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Circuitmod.MOD_ID, "missile")))
    );
    
    public static final EntityType<SatelliteBeamEntity> SATELLITE_BEAM = Registry.register(
        Registries.ENTITY_TYPE,
        Identifier.of(Circuitmod.MOD_ID, "satellite_beam"),
        EntityType.Builder.<SatelliteBeamEntity>create(SatelliteBeamEntity::new, SpawnGroup.MISC)
            .dimensions(0.5F, 0.5F) // Small size
            .maxTrackingRange(256) // Large tracking range for high altitude beam
            .trackingTickInterval(1) // Update every tick for smooth rendering
            .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Circuitmod.MOD_ID, "satellite_beam")))
    );
    
    public static void initialize() {
        Circuitmod.LOGGER.info("Registering mod entity types");
    }
} 