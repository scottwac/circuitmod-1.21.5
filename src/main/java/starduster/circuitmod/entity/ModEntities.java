package starduster.circuitmod.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;

/**
 * Registry for all custom entities in the mod
 */
public class ModEntities {
    
    public static final EntityType<RocketEntity> ROCKET = Registry.register(
        Registries.ENTITY_TYPE,
        Identifier.of(Circuitmod.MOD_ID, "rocket"),
        EntityType.Builder.<RocketEntity>create(RocketEntity::new, SpawnGroup.MISC)
            .dimensions(1.5f, 3.0f) // Width, Height
            .maxTrackingRange(80)
            .trackingTickInterval(3)
            .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Circuitmod.MOD_ID, "rocket")))
    );
    
    /**
     * Initialize all entities - call this from the main mod class
     */
    public static void initialize() {
        Circuitmod.LOGGER.info("Registering entities for " + Circuitmod.MOD_ID);
    }
}
