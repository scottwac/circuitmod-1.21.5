package starduster.circuitmod.worldgen;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.GenerationStep;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.worldgen.ModBiomes;

public class ModTreeGeneration {
    
    public static void generateTrees() {

        BiomeModifications.addFeature(BiomeSelectors.includeByKey(BiomeKeys.JUNGLE),
             GenerationStep.Feature.VEGETAL_DECORATION, ModPlacedFeatures.SHARINGA_TREE_PLACED_KEY);

        BiomeModifications.addFeature(BiomeSelectors.includeByKey(BiomeKeys.PLAINS, BiomeKeys.FOREST),
                GenerationStep.Feature.VEGETAL_DECORATION, ModPlacedFeatures.PLAINS_SHARINGA_TREE_PLACED_KEY);

        BiomeModifications.addFeature(BiomeSelectors.includeByKey(BiomeKeys.BAMBOO_JUNGLE, BiomeKeys.SPARSE_JUNGLE),
                GenerationStep.Feature.VEGETAL_DECORATION, ModPlacedFeatures.SPARSE_JUNGLE_SHARINGA_TREE_PLACED_KEY);
        
        Circuitmod.LOGGER.info("All mod tree generation registered for overworld biomes");
    }
} 