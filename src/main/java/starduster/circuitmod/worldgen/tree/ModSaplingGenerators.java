package starduster.circuitmod.worldgen.tree;

import net.minecraft.block.SaplingGenerator;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.worldgen.ModConfiguredFeatures;

import java.util.Optional;

public class ModSaplingGenerators {
    public static final SaplingGenerator SHARINGA = new SaplingGenerator(Circuitmod.MOD_ID + ":sharinga",
            Optional.empty(), Optional.of(ModConfiguredFeatures.SHARINGA_TREE_KEY), Optional.empty());
}
