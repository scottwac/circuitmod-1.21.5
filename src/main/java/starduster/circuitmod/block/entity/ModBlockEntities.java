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

    // Add Drill block entity
    public static final BlockEntityType<DrillBlockEntity> DRILL_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(Circuitmod.MOD_ID, "drill_block_entity"),
            FabricBlockEntityTypeBuilder.create(DrillBlockEntity::new, ModBlocks.DRILL_BLOCK).build()
    );

    // Add Constructor block entity
    public static final BlockEntityType<ConstructorBlockEntity> CONSTRUCTOR_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(Circuitmod.MOD_ID, "constructor_block_entity"),
            FabricBlockEntityTypeBuilder.create(ConstructorBlockEntity::new, ModBlocks.CONSTRUCTOR_BLOCK).build()
    );

    // Add crusher block entity
    public static final BlockEntityType<CrusherBlockEntity> CRUSHER_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(Circuitmod.MOD_ID, "crusher_block_entity"),
            FabricBlockEntityTypeBuilder.create(CrusherBlockEntity::new, ModBlocks.CRUSHER).build()
    );

    // Add bloomery block entity
    public static final BlockEntityType<BloomeryBlockEntity> BLOOMERY_BLOCK_ENTITY = Registry.register(
        Registries.BLOCK_ENTITY_TYPE,
        Identifier.of(Circuitmod.MOD_ID, "bloomery"),
        FabricBlockEntityTypeBuilder.create(BloomeryBlockEntity::new, ModBlocks.BLOOMERY).build()
    );

    // Add power cable block entity
    public static final BlockEntityType<PowerCableBlockEntity> POWER_CABLE_BLOCK_ENTITY = Registry.register(
        Registries.BLOCK_ENTITY_TYPE,
        Identifier.of(Circuitmod.MOD_ID, "power_cable"),
        FabricBlockEntityTypeBuilder.create(PowerCableBlockEntity::new, ModBlocks.POWER_CABLE).build()
    );

    // Add creative generator block entity
    public static final BlockEntityType<CreativeGeneratorBlockEntity> CREATIVE_GENERATOR_BLOCK_ENTITY = Registry.register(
        Registries.BLOCK_ENTITY_TYPE,
        Identifier.of(Circuitmod.MOD_ID, "creative_generator"),
        FabricBlockEntityTypeBuilder.create(CreativeGeneratorBlockEntity::new, ModBlocks.CREATIVE_GENERATOR).build()
    );

    // Add creative generator block entity
    public static final BlockEntityType<TeslaCoilBlockEntity> TESLA_COIL_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(Circuitmod.MOD_ID, "tesla_coil"),
            FabricBlockEntityTypeBuilder.create(TeslaCoilBlockEntity::new, ModBlocks.TESLA_COIL).build()
    );

    // Add electric carpet generator block entity
    public static final BlockEntityType<ElectricCarpetBlockEntity> ELECTRIC_CARPET_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(Circuitmod.MOD_ID, "electric_carpet"),
            FabricBlockEntityTypeBuilder.create(ElectricCarpetBlockEntity::new, ModBlocks.ELECTRIC_CARPET).build()
    );

    // Add solar panel block entity
    public static final BlockEntityType<GeneratorBlockEntity> GENERATOR_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(Circuitmod.MOD_ID, "generator"),
            FabricBlockEntityTypeBuilder.create(GeneratorBlockEntity::new, ModBlocks.GENERATOR).build()
    );

    // Add solar panel block entity
    public static final BlockEntityType<SolarPanelBlockEntity> SOLAR_PANEL_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(Circuitmod.MOD_ID, "solar_panel"),
            FabricBlockEntityTypeBuilder.create(SolarPanelBlockEntity::new, ModBlocks.SOLAR_PANEL).build()
    );

    // Add reactor block entity
    public static final BlockEntityType<ReactorBlockBlockEntity> REACTOR_BLOCK_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(Circuitmod.MOD_ID, "reactor_block"),
            FabricBlockEntityTypeBuilder.create(ReactorBlockBlockEntity::new, ModBlocks.REACTOR_BLOCK).build()
    );

    // Add creative consumer block entity
    public static final BlockEntityType<CreativeConsumerBlockEntity> CREATIVE_CONSUMER_BLOCK_ENTITY = Registry.register(
        Registries.BLOCK_ENTITY_TYPE,
        Identifier.of(Circuitmod.MOD_ID, "creative_consumer"),
        FabricBlockEntityTypeBuilder.create(CreativeConsumerBlockEntity::new, ModBlocks.CREATIVE_CONSUMER).build()
    );

    // Add battery block entity
    public static final BlockEntityType<BatteryBlockEntity> BATTERY_BLOCK_ENTITY = Registry.register(
        Registries.BLOCK_ENTITY_TYPE,
        Identifier.of(Circuitmod.MOD_ID, "battery"),
        FabricBlockEntityTypeBuilder.create(BatteryBlockEntity::new, ModBlocks.BATTERY).build()
    );

    // Add electric furnace block entity
    public static final BlockEntityType<ElectricFurnaceBlockEntity> ELECTRIC_FURNACE_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(Circuitmod.MOD_ID, "electric_furnace"),
            FabricBlockEntityTypeBuilder.create(ElectricFurnaceBlockEntity::new, ModBlocks.ELECTRIC_FURNACE).build()
    );

    // Add item pipe block entity
    public static final BlockEntityType<ItemPipeBlockEntity> ITEM_PIPE = Registry.register(
        Registries.BLOCK_ENTITY_TYPE,
        Identifier.of(Circuitmod.MOD_ID, "item_pipe"),
        FabricBlockEntityTypeBuilder.create(ItemPipeBlockEntity::new, ModBlocks.ITEM_PIPE).build()
    );

    // Add nuke block entity
    public static final BlockEntityType<NukeBlockEntity> NUKE_BLOCK_ENTITY = Registry.register(
        Registries.BLOCK_ENTITY_TYPE,
        Identifier.of(Circuitmod.MOD_ID, "nuke"),
        FabricBlockEntityTypeBuilder.create(NukeBlockEntity::new, ModBlocks.NUKE).build()
    );

    public static final BlockEntityType<MegaCreativeGeneratorBlockEntity> MEGA_CREATIVE_GENERATOR_BLOCK_ENTITY = Registry.register(
        Registries.BLOCK_ENTITY_TYPE,
        Identifier.of(Circuitmod.MOD_ID, "mega_creative_generator"),
        FabricBlockEntityTypeBuilder.create(MegaCreativeGeneratorBlockEntity::new, ModBlocks.MEGA_CREATIVE_GENERATOR).build()
    );

    public static final BlockEntityType<MassFabricatorBlockEntity> MASS_FABRICATOR_BLOCK_ENTITY = Registry.register(
        Registries.BLOCK_ENTITY_TYPE,
        Identifier.of(Circuitmod.MOD_ID, "mass_fabricator"),
        FabricBlockEntityTypeBuilder.create(MassFabricatorBlockEntity::new, ModBlocks.MASS_FABRICATOR).build()
    );

    // Blueprint system block entities
    public static final BlockEntityType<BlueprintDeskBlockEntity> BLUEPRINT_DESK_BLOCK_ENTITY = Registry.register(
        Registries.BLOCK_ENTITY_TYPE,
        Identifier.of(Circuitmod.MOD_ID, "blueprint_desk"),
        FabricBlockEntityTypeBuilder.create(BlueprintDeskBlockEntity::new, ModBlocks.BLUEPRINT_DESK).build()
    );

    public static void initialize() {
        Circuitmod.LOGGER.info("ModBlockEntities initialized");
    }
} 