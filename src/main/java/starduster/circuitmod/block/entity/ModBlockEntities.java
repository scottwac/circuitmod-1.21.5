package starduster.circuitmod.block.entity;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.ModBlocks;

public class ModBlockEntities {
    // Register our QuarryBlockEntity using FabricBlockEntityTypeBuilder
    public static final BlockEntityType<QuarryBlockEntity> QUARRY_BLOCK_ENTITY = Registry.register(
        Registries.BLOCK_ENTITY_TYPE,
        Identifier.of(Circuitmod.MOD_ID, "quarry_block_entity"),
        FabricBlockEntityTypeBuilder.create(QuarryBlockEntity::new, ModBlocks.QUARRY_BLOCK).build()
    );

    // Add bloomery block entity
    public static final BlockEntityType<BloomeryBlockEntity> BLOOMERY_BLOCK_ENTITY = Registry.register(
        Registries.BLOCK_ENTITY_TYPE,
        Identifier.of(Circuitmod.MOD_ID, "bloomery"),
        FabricBlockEntityTypeBuilder.create(BloomeryBlockEntity::new, ModBlocks.BLOOMERY).build()
    );

    public static void initialize() {
        Circuitmod.LOGGER.info("ModBlockEntities initialized");
    }
} 