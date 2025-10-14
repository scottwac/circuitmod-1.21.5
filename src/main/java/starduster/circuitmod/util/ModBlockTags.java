package starduster.circuitmod.util;

import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;

public class ModBlockTags {
    public static final TagKey<Block> EXAMPLE_ORES = TagKey.of(RegistryKeys.BLOCK, Identifier.of("circuitmod", "heightmaps_ignore"));
    public static final TagKey<Block> SHARINGA_LOGS = TagKey.of(RegistryKeys.BLOCK, Identifier.of("circuitmod", "sharinga_logs"));

    public static final TagKey<Block> ANORTHOSITE_ORE_REPLACEABLES = TagKey.of(RegistryKeys.BLOCK, Identifier.of("circuitmod", "anorthosite_ore_replaceables"));
    public static final TagKey<Block> DEEPBASALT_ORE_REPLACEABLES = TagKey.of(RegistryKeys.BLOCK, Identifier.of("circuitmod", "deepbasalt_ore_replaceables"));
    public static final TagKey<Block> MANTLEROCK_ORE_REPLACEABLES = TagKey.of(RegistryKeys.BLOCK, Identifier.of("circuitmod", "mantlerock_ore_replaceables"));

    public static void initialize() {
        Circuitmod.LOGGER.info("Registering mod block tags");
    }
}
