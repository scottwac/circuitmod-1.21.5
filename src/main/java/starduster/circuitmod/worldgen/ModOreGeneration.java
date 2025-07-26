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
    
    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> BAUXITE_ORE_PLACED_KEY = 
        RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "bauxite_ore"));
    
    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LEAD_ORE_UPPER_PLACED_KEY = 
        RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "lead_ore_upper"));
    
    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LEAD_ORE_LOWER_PLACED_KEY = 
        RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "lead_ore_lower"));
    
    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> ZIRCON_ORE_PLACED_KEY = 
        RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "zircon_ore"));
    
    public static void generateOres() {
        // Add uranium ore to all overworld biomes
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), 
            GenerationStep.Feature.UNDERGROUND_ORES, URANIUM_ORE_PLACED_KEY);
        
        // Add bauxite ore to all overworld biomes
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), 
            GenerationStep.Feature.UNDERGROUND_ORES, BAUXITE_ORE_PLACED_KEY);
        
        // Add lead ore (upper layer) to all overworld biomes
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), 
            GenerationStep.Feature.UNDERGROUND_ORES, LEAD_ORE_UPPER_PLACED_KEY);
        
        // Add lead ore (lower layer) to all overworld biomes
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), 
            GenerationStep.Feature.UNDERGROUND_ORES, LEAD_ORE_LOWER_PLACED_KEY);
        
        // Add zircon ore to all overworld biomes
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), 
            GenerationStep.Feature.UNDERGROUND_ORES, ZIRCON_ORE_PLACED_KEY);
        
        Circuitmod.LOGGER.info("All mod ores generation registered for overworld biomes");
    }
} 