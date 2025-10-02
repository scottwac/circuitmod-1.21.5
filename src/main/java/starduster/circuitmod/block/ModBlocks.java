package starduster.circuitmod.block;

import java.util.function.Function;

import net.minecraft.block.*;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import net.minecraft.util.math.intprovider.UniformIntProvider;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.machines.*;
import starduster.circuitmod.block.machines.XpGenerator;
import starduster.circuitmod.block.networkblocks.*;


public final class ModBlocks {
    // Register our QuarryBlock with a custom block class
        public static final Block QUARRY_BLOCK = registerWithCustomItem(
            "quarry_block",
            QuarryBlock::new,
            Block.Settings.create().strength(3.0f, 5.0f).requiresTool(),
            // Make the block item enchantable so it can accept Fortune in an anvil/enchanting
            settings -> settings
                    .maxCount(1)
                    .component(
                    net.minecraft.component.DataComponentTypes.ENCHANTABLE,
                    new net.minecraft.component.type.EnchantableComponent(15)
            )
    );

    // Register drill block with an enchantable BlockItem so it can accept Fortune via anvil
    public static final Block DRILL_BLOCK = registerWithCustomItem(
            "drill_block",
            DrillBlock::new,
            Block.Settings.create()
                    .requiresTool()
                    .strength(3.0f, 5.0f),
            settings -> settings
                    .maxCount(1)
                    .component(
                            net.minecraft.component.DataComponentTypes.ENCHANTABLE,
                            new net.minecraft.component.type.EnchantableComponent(15)
                    )
    );

    // Register laser mining drill block
    public static final Block LASER_MINING_DRILL_BLOCK = register(
            "laser_mining_drill_block",
            LaserMiningDrillBlock::new,
            Block.Settings.create()
                    .requiresTool()
                    .strength(3.0f, 5.0f)
                    .nonOpaque()
    );

    // Register constructor block
    public static final Block CONSTRUCTOR_BLOCK = register(
            "constructor_block",
            ConstructorBlock::new,
            Block.Settings.create()
                    .requiresTool()
                    .strength(3.0f, 5.0f)
    );

    // Register fluid tank block
    public static final Block FLUID_TANK = register(
            "fluid_tank",
            FluidTankBlock::new,
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

    // Register XP generator block
    public static final Block XP_GENERATOR = register(
            "xp_generator",
            XpGenerator::new,
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

    public static final Block OUTPUT_PIPE = register(
        "output_pipe",
        OutputPipeBlock::new,
        Block.Settings.create()
            .strength(0.3f, 0.3f)
            .nonOpaque()
            .sounds(BlockSoundGroup.GLASS)
    );

    public static final Block SORTING_PIPE = register(
        "sorting_pipe",
        SortingPipeBlock::new,
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
                    .mapColor(MapColor.DEEPSLATE_GRAY)
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
                    .mapColor(MapColor.DEEPSLATE_GRAY)
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
                    .mapColor(MapColor.DEEPSLATE_GRAY)
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
                    .mapColor(MapColor.DEEPSLATE_GRAY)
    );


    /**
     * MISC
     */

    public static final Block STEEL_BLOCK = register(
            "steel_block",
            Block::new,
            AbstractBlock.Settings.create().sounds(BlockSoundGroup.IRON)
                    .requiresTool()
                    .strength(3.0F, 6.0F)
    );

    public static final Block LAUNCH_PAD = register(
            "launch_pad",
            LaunchPadBlock::new,
            AbstractBlock.Settings.create()
                    .strength(2.0f, 3.0f)
                    .sounds(BlockSoundGroup.COPPER)
                    .requiresTool()
    );


    /**
     * LUANR BLOCKS
     */

    public static final Block LUNAR_REGOLITH = register(
            "lunar_regolith",
            Block::new,
            Block.Settings.create().sounds(BlockSoundGroup.SAND)
                    .strength(0.5F, 0.5F)
                    .mapColor(MapColor.LIGHT_BLUE_GRAY)
    );
    public static final Block LUNAR_DEEPBASALT = register(
            "lunar_deepbasalt",
            Block::new,
            Block.Settings.create().sounds(BlockSoundGroup.NETHERRACK)
                    .strength(3.0F, 6.0F)
                    .mapColor(MapColor.DEEPSLATE_GRAY)
    );
    public static final Block LUNAR_MANTLEROCK = register(
            "lunar_mantlerock",
            Block::new,
            Block.Settings.create().sounds(BlockSoundGroup.NETHERRACK)
                    .strength(4.0F, 7.0F)
                    .mapColor(MapColor.DEEPSLATE_GRAY)
    );
    public static final Block LUNAR_BASALT = register(
            "lunar_basalt",
            Block::new,
            Block.Settings.create().sounds(BlockSoundGroup.BASALT)
                    .strength(1.25F, 4.2F)
                    .mapColor(MapColor.DEEPSLATE_GRAY)
    );
    public static final Block LUNAR_ANORTHOSITE = register(
            "lunar_anorthosite",
            Block::new,
            Block.Settings.create().sounds(BlockSoundGroup.TUFF)
                    .strength(1.5F, 6.0F)
                    .mapColor(MapColor.IRON_GRAY)
    );

    public static final Block LUNAR_BRECCIA = register(
            "lunar_breccia",
            Block::new,
            Block.Settings.create().sounds(BlockSoundGroup.TUFF)
                    .strength(3.5F, 6.0F)
                    .mapColor(MapColor.TERRACOTTA_CYAN)
    );
    public static final Block LUNAR_IMPACT_GLASS = register(
            "lunar_impact_glass",
            Block::new,
            Block.Settings.create().sounds(BlockSoundGroup.TUFF)
                    .strength(3.5F, 4.0F)
                    .mapColor(MapColor.GRAY)
    );
    public static final Block LUNAR_EJECTA = register(
            "lunar_ejecta",
            Block::new,
            Block.Settings.create().sounds(BlockSoundGroup.SAND)
                    .strength(0.5F, 0.5F)
                    .mapColor(MapColor.WHITE_GRAY)
    );
    public static final Block LUNAR_ICE = register(
            "lunar_ice",
            Block::new,
            Block.Settings.create().sounds(BlockSoundGroup.GLASS)
                    .strength(2.5F, 2.8F)
                    .mapColor(MapColor.PALE_PURPLE)
    );

    /**
     * LUANR ORES
     */

    public static final Block ANORTHOSITE_IRON_ORE = register(
            "anorthosite_iron_ore", (settings) ->
                    new ExperienceDroppingBlock(ConstantIntProvider.create(0), settings), AbstractBlock.Settings.create()
                    .strength(3.0f, 3.0f)
                    .sounds(BlockSoundGroup.TUFF)
                    .mapColor(MapColor.IRON_GRAY)
                    .requiresTool()
    );
    public static final Block ANORTHOSITE_COPPER_ORE = register(
            "anorthosite_copper_ore", (settings) ->
                    new ExperienceDroppingBlock(ConstantIntProvider.create(0), settings), AbstractBlock.Settings.create()
                    .strength(3.0f, 3.0f)
                    .sounds(BlockSoundGroup.TUFF)
                    .mapColor(MapColor.IRON_GRAY)
                    .requiresTool()
    );
    public static final Block ANORTHOSITE_GOLD_ORE = register(
            "anorthosite_gold_ore", (settings) ->
                    new ExperienceDroppingBlock(ConstantIntProvider.create(0), settings), AbstractBlock.Settings.create()
                    .strength(3.0f, 3.0f)
                    .sounds(BlockSoundGroup.TUFF)
                    .mapColor(MapColor.IRON_GRAY)
                    .requiresTool()
    );
    public static final Block ANORTHOSITE_DIAMOND_ORE = register(
            "anorthosite_diamond_ore", (settings) ->
                    new ExperienceDroppingBlock(UniformIntProvider.create(3,7), settings), AbstractBlock.Settings.create()
                    .strength(3.0f, 3.0f)
                    .sounds(BlockSoundGroup.TUFF)
                    .mapColor(MapColor.IRON_GRAY)
                    .requiresTool()
    );
    public static final Block ANORTHOSITE_ZIRCON_ORE = register(
            "anorthosite_zircon_ore", (settings) ->
                    new ExperienceDroppingBlock(UniformIntProvider.create(1,3), settings), AbstractBlock.Settings.create()
                    .strength(3.0f, 3.0f)
                    .sounds(BlockSoundGroup.TUFF)
                    .mapColor(MapColor.IRON_GRAY)
                    .requiresTool()
    );
    public static final Block ANORTHOSITE_TITANIUM_ORE = register(
            "anorthosite_titanium_ore", (settings) ->
                    new ExperienceDroppingBlock(UniformIntProvider.create(7,9), settings), AbstractBlock.Settings.create()
                    .strength(3.0f, 3.0f)
                    .sounds(BlockSoundGroup.TUFF)
                    .mapColor(MapColor.IRON_GRAY)
                    .requiresTool()
    );


    public static final Block DEEPBASALT_IRON_ORE = register(
            "deepbasalt_iron_ore", (settings) ->
                    new ExperienceDroppingBlock(ConstantIntProvider.create(0), settings), AbstractBlock.Settings.create()
                    .strength(4.0f, 3.0f)
                    .sounds(BlockSoundGroup.NETHERRACK)
                    .mapColor(MapColor.DEEPSLATE_GRAY)
                    .requiresTool()
    );
    public static final Block DEEPBASALT_COPPER_ORE = register(
            "deepbasalt_copper_ore", (settings) ->
                    new ExperienceDroppingBlock(ConstantIntProvider.create(0), settings), AbstractBlock.Settings.create()
                    .strength(4.0f, 3.0f)
                    .sounds(BlockSoundGroup.NETHERRACK)
                    .mapColor(MapColor.DEEPSLATE_GRAY)
                    .requiresTool()
    );
    public static final Block DEEPBASALT_GOLD_ORE = register(
            "deepbasalt_gold_ore", (settings) ->
                    new ExperienceDroppingBlock(ConstantIntProvider.create(0), settings), AbstractBlock.Settings.create()
                    .strength(4.0f, 3.0f)
                    .sounds(BlockSoundGroup.NETHERRACK)
                    .mapColor(MapColor.DEEPSLATE_GRAY)
                    .requiresTool()
    );
    public static final Block DEEPBASALT_DIAMOND_ORE = register(
            "deepbasalt_diamond_ore", (settings) ->
                    new ExperienceDroppingBlock(UniformIntProvider.create(3,7), settings), AbstractBlock.Settings.create()
                    .strength(4.0f, 3.0f)
                    .sounds(BlockSoundGroup.NETHERRACK)
                    .mapColor(MapColor.DEEPSLATE_GRAY)
                    .requiresTool()
    );
    public static final Block DEEPBASALT_ZIRCON_ORE = register(
            "deepbasalt_zircon_ore", (settings) ->
                    new ExperienceDroppingBlock(UniformIntProvider.create(1,3), settings), AbstractBlock.Settings.create()
                    .strength(4.0f, 3.0f)
                    .sounds(BlockSoundGroup.NETHERRACK)
                    .mapColor(MapColor.DEEPSLATE_GRAY)
                    .requiresTool()
    );
    public static final Block DEEPBASALT_URANIUM_ORE = register(
            "deepbasalt_uranium_ore", (settings) ->
                    new ExperienceDroppingBlock(UniformIntProvider.create(3,5), settings), AbstractBlock.Settings.create()
                    .strength(4.0f, 3.0f)
                    .sounds(BlockSoundGroup.NETHERRACK)
                    .mapColor(MapColor.DEEPSLATE_GRAY)
                    .requiresTool()
    );
    public static final Block DEEPBASALT_TITANIUM_ORE = register(
            "deepbasalt_titanium_ore", (settings) ->
                    new ExperienceDroppingBlock(UniformIntProvider.create(7,9), settings), AbstractBlock.Settings.create()
                    .strength(4.0f, 3.0f)
                    .sounds(BlockSoundGroup.NETHERRACK)
                    .mapColor(MapColor.DEEPSLATE_GRAY)
                    .requiresTool()
    );


    public static final Block MANTLEROCK_DIAMOND_ORE = register(
            "mantlerock_diamond_ore", (settings) ->
                    new ExperienceDroppingBlock(UniformIntProvider.create(3,7), settings), AbstractBlock.Settings.create()
                    .strength(5.0f, 5.0f)
                    .sounds(BlockSoundGroup.NETHERRACK)
                    .mapColor(MapColor.DEEPSLATE_GRAY)
                    .requiresTool()
    );
    public static final Block MANTLEROCK_URANIUM_ORE = register(
            "mantlerock_uranium_ore", (settings) ->
                    new ExperienceDroppingBlock(UniformIntProvider.create(3,5), settings), AbstractBlock.Settings.create()
                    .strength(5.0f, 5.0f)
                    .sounds(BlockSoundGroup.NETHERRACK)
                    .mapColor(MapColor.DEEPSLATE_GRAY)
                    .requiresTool()
    );
    public static final Block MANTLEROCK_TITANIUM_ORE = register(
            "mantlerock_titanium_ore", (settings) ->
                    new ExperienceDroppingBlock(UniformIntProvider.create(7,9), settings), AbstractBlock.Settings.create()
                    .strength(5.0f, 5.0f)
                    .sounds(BlockSoundGroup.NETHERRACK)
                    .mapColor(MapColor.DEEPSLATE_GRAY)
                    .requiresTool()
    );

    /**
     * EXTINGUISHED TORCHES (for Luna dimension)
     */

    public static final Block EXTINGUISHED_TORCH = registerWithoutItem(
            "extinguished_torch",
            ExtinguishedTorchBlock::new,
            Block.Settings.create()
                    .noCollision()
                    .breakInstantly()
                    .sounds(BlockSoundGroup.WOOD)
                    .pistonBehavior(net.minecraft.block.piston.PistonBehavior.DESTROY)
    );

    public static final Block EXTINGUISHED_WALL_TORCH = registerWithoutItem(
            "extinguished_wall_torch",
            ExtinguishedWallTorchBlock::new,
            Block.Settings.create()
                    .noCollision()
                    .breakInstantly()
                    .sounds(BlockSoundGroup.WOOD)
                    .pistonBehavior(net.minecraft.block.piston.PistonBehavior.DESTROY)
    );


    private static Block register(String path, Function<AbstractBlock.Settings, Block> factory, AbstractBlock.Settings settings) {
        final Identifier identifier = Identifier.of(Circuitmod.MOD_ID, path);
        final RegistryKey<Block> registryKey = RegistryKey.of(RegistryKeys.BLOCK, identifier);

        final Block block = Blocks.register(registryKey, factory, settings);
        Items.register(block);
        return block;
    }

    // Register a block but create its BlockItem with customized Item.Settings
    private static Block registerWithCustomItem(
            String path,
            Function<AbstractBlock.Settings, Block> factory,
            AbstractBlock.Settings blockSettings,
            java.util.function.Function<Item.Settings, Item.Settings> itemSettingsCustomizer
    ) {
        final Identifier identifier = Identifier.of(Circuitmod.MOD_ID, path);
        final RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, identifier);
        final Block block = Blocks.register(blockKey, factory, blockSettings);

        final RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, identifier);
        Items.register(
                itemKey,
                (s) -> new net.minecraft.item.BlockItem(block, itemSettingsCustomizer.apply(s)),
                new Item.Settings()
        );
        return block;
    }

    // Register a block without creating a BlockItem (for wall variants that share items)
    private static Block registerWithoutItem(String path, Function<AbstractBlock.Settings, Block> factory, AbstractBlock.Settings settings) {
        final Identifier identifier = Identifier.of(Circuitmod.MOD_ID, path);
        final RegistryKey<Block> registryKey = RegistryKey.of(RegistryKeys.BLOCK, identifier);
        return Blocks.register(registryKey, factory, settings);
    }

    public static void initialize() {
        Circuitmod.LOGGER.info("ModBlocks initialized");
    }
} 