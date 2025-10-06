package starduster.circuitmod.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;

import java.util.function.BiConsumer;

/**
 * Registry for all custom entities in the mod
 */
public class ModEntities {
    
    public static final EntityType<RocketEntity> ROCKET = Registry.register(
        Registries.ENTITY_TYPE,
        Identifier.of(Circuitmod.MOD_ID, "rocket"),
        EntityType.Builder.<RocketEntity>create(RocketEntity::new, SpawnGroup.CREATURE)
            .dimensions(1.5f, 3.0f) // Width, Height
            .maxTrackingRange(80)
            .trackingTickInterval(3)
            .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Circuitmod.MOD_ID, "rocket")))
    );
    
    /**
     * Register entity attributes - following the pattern from the example
     */
    public static void registerEntityAttributes(BiConsumer<EntityType<? extends LivingEntity>, DefaultAttributeContainer> registrar) {
        DefaultAttributeContainer.Builder rocketAttribs = AnimalEntity.createMobAttributes()
                .add(EntityAttributes.FOLLOW_RANGE, 16.0)
                .add(EntityAttributes.MAX_HEALTH, 100.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 1.0);

        registrar.accept(ROCKET, rocketAttribs.build());
    }
    
    /**
     * Initialize all entities - call this from the main mod class
     */
    public static void initialize() {
        Circuitmod.LOGGER.info("Registering entities for " + Circuitmod.MOD_ID);
        
        // Register entity attributes using Fabric API
        FabricDefaultAttributeRegistry.register(ROCKET, AnimalEntity.createMobAttributes()
                .add(EntityAttributes.FOLLOW_RANGE, 16.0)
                .add(EntityAttributes.MAX_HEALTH, 100.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 1.0));
    }
}
