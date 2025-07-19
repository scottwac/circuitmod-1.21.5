package starduster.circuitmod.block;

import java.util.function.Function;

import net.minecraft.block.*;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import net.minecraft.util.math.intprovider.UniformIntProvider;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.machines.*;
import starduster.circuitmod.block.networkblocks.BatteryBlock;
import starduster.circuitmod.block.networkblocks.PowerCableBlock;

import net.minecraft.registry.Registries;


public final class ModBlocks {
    // Register our QuarryBlock with a custom block class
    public static final Block QUARRY_BLOCK = register("quarry_block", QuarryBlock::new, Block.Settings.create().strength(3.0f, 5.0f).requiresTool());

    // Register drill block
    public static final Block DRILL_BLOCK = register(
            "drill_block",
            DrillBlock::new,
            Block.Settings.create()
                    .requiresTool()
                    .strength(3.0f, 5.0f)
    );

    // Register laser mining drill block
    public static final Block LASER_MINING_DRILL_BLOCK = register(
            "laser_mining_drill_block",
            LaserMiningDrillBlock::new,
            Block.Settings.create()
                    .requiresTool()
                    .strength(3.0f, 5.0f)
    );

    // Register constructor block
    public static final Block CONSTRUCTOR_BLOCK = register(
            "constructor_block",
            ConstructorBlock::new,
            Block.Settings.create()
                    .requiresTool()
                    .strength(3.0f, 5.0f)
    );

    // Register blueprint desk block
    public static final Block BLUEPRINT_DESK = register(
            "blueprint_desk",
            BlueprintDeskBlock::new,
            Block.Settings.create()
                    .requiresTool()
                    .strength(2.0f, 3.0f)
    );

    // Register bloomery block
    public static final Block BLOOMERY = register(
        "bloomery", 
        BloomeryBlock::new,
        Block.Settings.create()
            .requiresTool()
            .strength(1.5f, 3.0f)
            .luminance(state -> 13) // Light when active, would need a blockstate for this
    );

    // Register electric furnace block
    public static final Block ELECTRIC_FURNACE = register(
            "electric_furnace",
            ElectricFurnace::new,
            Block.Settings.create()
                    .requiresTool()
                    .strength(3.0f, 5.0f)
                    .luminance(state -> 13) // Light when active, would need a blockstate for this
    );

    // Register crusher block
    public static final Block CRUSHER = register(
            "crusher",
            CrusherBlock::new,
            Block.Settings.create()
                    .requiresTool()
                    .strength(3.0f, 5.0f)
    );

    // Register crusher block
    public static final Block TESLA_COIL = register(
            "tesla_coil",
            TeslaCoil::new,
            Block.Settings.create()
                    .requiresTool()
                    .strength(3.0f, 3.0f)
                    .nonOpaque()
    );

    // Register electric carpet block
    public static final Block ELECTRIC_CARPET = register(
            "electric_carpet",
            ElectricCarpet::new,
            Block.Settings.create()
                    .strength(0.1f,0.4f)
                    .nonOpaque()
    );

    // Register creative generator block
    public static final Block CREATIVE_GENERATOR = register(
        "creative_generator",
        CreativeGeneratorBlock::new,
        Block.Settings.create()
            .strength(3.5f)
            .luminance(state -> 5) // Slight glow
    );

    // Register creative consumer block
    public static final Block CREATIVE_CONSUMER = register(
        "creative_consumer",
        CreativeConsumerBlock::new,
        Block.Settings.create()
            .strength(3.5f)
    );

    // Register fuel generator block
    public static final Block GENERATOR = register(
            "generator",
            Generator::new,
            Block.Settings.create()
                    .requiresTool()
                    .strength(3.0f, 5.0f)
    );

    // Register solar panel block
    public static final Block SOLAR_PANEL = register(
            "solar_panel",
            SolarPanel::new,
            Block.Settings.create()
                    .requiresTool()
                    .strength(3.0f, 5.0f)
    );

    // Register solar panel block
    public static final Block REACTOR_BLOCK = register(
            "reactor_block",
            ReactorBlock::new,
            Block.Settings.create()
                    .requiresTool()
                    .strength(3.0f, 4.0f) //Slightly less blast resistance for the reactor
    );

    // Register battery block
    public static final Block BATTERY = register(
        "battery",
        BatteryBlock::new,
        Block.Settings.create()
            .strength(3.5f)
    );

    public static final Block ITEM_PIPE = register(
        "item_pipe",
        ItemPipeBlock::new,
        Block.Settings.create()
            .strength(0.3f, 0.3f)
            .nonOpaque()
            .sounds(BlockSoundGroup.GLASS)
    );

    // Register power cable block
    public static final Block POWER_CABLE = register(
            "power_cable",
            PowerCableBlock::new,
            Block.Settings.create()
                    .strength(0.4f, 0.8f)
                    .nonOpaque() // So we can see through the cable
                    .sounds(BlockSoundGroup.WOOL)
    );

    public static final Block NUKE = register(
            "nuke",
            Nuke::new,
            Block.Settings.create()
                    .strength(4.0f, 10.0f)
                    .nonOpaque()
    );

    public static final Block MEGA_CREATIVE_GENERATOR = register(
        "mega_creative_generator",
        MegaCreativeGeneratorBlock::new,
        Block.Settings.create()
            .strength(3.5f)
            .luminance(state -> 5)
    );

    public static final Block MASS_FABRICATOR = register(
        "mass_fabricator",
        MassFabricatorBlock::new,
        Block.Settings.create().strength(4.0f).requiresTool()
    );

    


    /**
     * ORES
     */

    //STONE = register("stone",AbstractBlock.Settings.create().mapColor(MapColor.STONE_GRAY).instrument(NoteBlockInstrument.BASEDRUM).requiresTool().strength(1.5F, 6.0F));
    //IRON_ORE = register("iron_ore", (settings) -> new ExperienceDroppingBlock(ConstantIntProvider.create(0), settings), AbstractBlock.Settings.create().mapColor(MapColor.STONE_GRAY).instrument(NoteBlockInstrument.BASEDRUM).requiresTool().strength(3.0F, 3.0F));

    public static final Block BAUXITE_ORE = register(
            "bauxite_ore", (settings) ->
                    new ExperienceDroppingBlock(UniformIntProvider.create(0,3), settings), AbstractBlock.Settings.create()
                    .strength(3.0f, 3.0f)
                    .requiresTool()
    );
    public static final Block DEEPSLATE_BAUXITE_ORE = register(
            "deepslate_bauxite_ore", (settings) ->
                    new ExperienceDroppingBlock(UniformIntProvider.create(0,3), settings), AbstractBlock.Settings.create()
                    .strength(4.5f, 3.0f)
                    .sounds(BlockSoundGroup.DEEPSLATE)
                    .requiresTool()
    );

    public static final Block LEAD_ORE = register(
            "lead_ore", (settings) ->
                    new ExperienceDroppingBlock(UniformIntProvider.create(0,2), settings), AbstractBlock.Settings.create()
                    .strength(3.0f, 3.0f)
                    .requiresTool()
    );
    public static final Block DEEPSLATE_LEAD_ORE = register(
            "deepslate_lead_ore", (settings) ->
                    new ExperienceDroppingBlock(UniformIntProvider.create(0,2), settings), AbstractBlock.Settings.create()
                    .strength(4.5f, 3.0f)
                    .sounds(BlockSoundGroup.DEEPSLATE)
                    .requiresTool()
    );

    public static final Block URANIUM_ORE = register(
            "uranium_ore", (settings) ->
                    new ExperienceDroppingBlock(UniformIntProvider.create(3,5), settings), AbstractBlock.Settings.create()
                    .strength(3.0f, 3.0f)
                    .requiresTool()
                    .luminance(state -> 2) // Slight glow
    );
    public static final Block DEEPSLATE_URANIUM_ORE = register(
            "deepslate_uranium_ore", (settings) ->
                    new ExperienceDroppingBlock(UniformIntProvider.create(3,5), settings), AbstractBlock.Settings.create()
                    .strength(4.5f, 3.0f)
                    .sounds(BlockSoundGroup.DEEPSLATE)
                    .requiresTool()
                    .luminance(state -> 2) // Slight glow
    );

    public static final Block ZIRCON_ORE = register(
            "zircon_ore", (settings) ->
                    new ExperienceDroppingBlock(UniformIntProvider.create(1,3), settings), AbstractBlock.Settings.create()
                    .strength(3.0f, 3.0f)
                    .requiresTool()
    );
    public static final Block DEEPSLATE_ZIRCON_ORE = register(
            "deepslate_zircon_ore", (settings) ->
                    new ExperienceDroppingBlock(UniformIntProvider.create(1,3), settings), AbstractBlock.Settings.create()
                    .strength(4.5f, 3.0f)
                    .sounds(BlockSoundGroup.DEEPSLATE)
                    .requiresTool()
    );


    /**
     * MISC
     */

    public static final Block STEEL_BLOCK = register(
            "steel_block",
            Block::new,
            AbstractBlock.Settings.create().sounds(BlockSoundGroup.IRON)
                    .requiresTool()
                    .strength(5.0F, 6.0F)
    );





    private static Block register(String path, Function<AbstractBlock.Settings, Block> factory, AbstractBlock.Settings settings) {
        final Identifier identifier = Identifier.of(Circuitmod.MOD_ID, path);
        final RegistryKey<Block> registryKey = RegistryKey.of(RegistryKeys.BLOCK, identifier);

        final Block block = Blocks.register(registryKey, factory, settings);
        Items.register(block);
        return block;
    }

    public static void initialize() {
        Circuitmod.LOGGER.info("ModBlocks initialized");
    }
} 