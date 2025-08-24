package starduster.circuitmod.util;

import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;

public class ModBlockTags {
    public static final TagKey<Block> EXAMPLE_ORES = TagKey.of(RegistryKeys.BLOCK, Identifier.of("circuitmod", "heightmaps_ignore"));

    public static void initialize() {
        Circuitmod.LOGGER.info("Registering mod block tags");
    }
}
