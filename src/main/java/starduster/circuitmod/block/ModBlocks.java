package starduster.circuitmod.block;

import java.util.function.Function;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;


public final class ModBlocks {
    // Register our QuarryBlock with a custom block class
    public static final Block QUARRY_BLOCK = register("quarry_block", QuarryBlock::new, Block.Settings.create().strength(4.0f).requiresTool());

    // Register bloomery block
    public static final Block BLOOMERY = register(
        "bloomery", 
        BloomeryBlock::new, 
        Block.Settings.create()
            .requiresTool()
            .strength(3.5f)
            .luminance(state -> 13) // Light when active, would need a blockstate for this
    );

    // Register bloomery block
    public static final Block ELECTRIC_FURNACE = register(
            "electric_furnace",
            ElectricFurnace::new,
            Block.Settings.create()
                    .requiresTool()
                    .strength(3.5f)
                    .luminance(state -> 13) // Light when active, would need a blockstate for this
    );
    
    // Register power cable block
    public static final Block POWER_CABLE = register(
        "power_cable",
        PowerCableBlock::new,
        Block.Settings.create()
            .strength(1.5f)
            .nonOpaque() // So we can see through the cable
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

    // Register solar panel block
    public static final Block SOLAR_PANEL = register(
            "solar_panel",
            SolarPanel::new,
            Block.Settings.create()
                    .requiresTool()
                    .strength(3.5f)
    );
    
    // Register battery block
    public static final Block BATTERY = register(
        "battery",
        BatteryBlock::new,
        Block.Settings.create()
            .strength(3.5f)
            .luminance(state -> 5) // Slight glow for battery
    );

    public static final Block ITEM_PIPE = register(
        "item_pipe",
        ItemPipeBlock::new,
        Block.Settings.create()
            .strength(2.0f, 2.0f)
            .nonOpaque()
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