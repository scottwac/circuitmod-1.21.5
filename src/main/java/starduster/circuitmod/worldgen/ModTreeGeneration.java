package starduster.circuitmod.worldgen;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.world.gen.GenerationStep;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.worldgen.ModBiomes;

public class ModTreeGeneration {
    
    public static void generateTrees() {
        // Add mega trees to the techno jungle biome
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(ModBiomes.TECHNO_JUNGLE), 
            GenerationStep.Feature.VEGETAL_DECORATION, ModPlacedFeatures.MEGA_TREE_PLACED_KEY);
        
        Circuitmod.LOGGER.info("Mega tree generation registered for techno jungle biome");
    }
} 