package starduster.circuitmod.worldgen;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.GenerationStep;
import starduster.circuitmod.Circuitmod;

public class ModOreGeneration {
    
    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> URANIUM_ORE_PLACED_KEY = 
        RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "uranium_ore"));
    
    public static void generateOres() {
        // Add uranium ore to all overworld biomes
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), 
            GenerationStep.Feature.UNDERGROUND_ORES, URANIUM_ORE_PLACED_KEY);
        
        Circuitmod.LOGGER.info("Uranium ore generation registered");
    }
} 