package starduster.circuitmod.worldgen;

import net.minecraft.registry.Registerable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.placementmodifier.*;
import starduster.circuitmod.Circuitmod;

import java.util.List;

public class ModPlacedFeatures {
    
    public static final RegistryKey<PlacedFeature> MEGA_TREE_PLACED_KEY = registerKey("mega_tree_placed");

    public static final RegistryKey<PlacedFeature> SHARINGA_TREE_PLACED_KEY = registerKey("sharinga_tree");
    public static final RegistryKey<PlacedFeature> PLAINS_SHARINGA_TREE_PLACED_KEY = registerKey("plains_sharinga_tree");
    public static final RegistryKey<PlacedFeature> SPARSE_JUNGLE_SHARINGA_TREE_PLACED_KEY = registerKey("sparse_jungle_sharinga_tree");

    public static void bootstrap(Registerable<PlacedFeature> context) {
        var configuredFeatures = context.getRegistryLookup(RegistryKeys.CONFIGURED_FEATURE);
        
        register(context, MEGA_TREE_PLACED_KEY, configuredFeatures.getOrThrow(ModConfiguredFeatures.MEGA_TREE_KEY),
            // Very rare - only 1 attempt per 200 chunks on average
            RarityFilterPlacementModifier.of(200),
            // Random placement within chunk
            SquarePlacementModifier.of(),
            // Only place on surface
            HeightmapPlacementModifier.of(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING),
            // Biome check
            BiomePlacementModifier.of()
        );
    }
    
    public static RegistryKey<PlacedFeature> registerKey(String name) {
        return RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, name));
    }
    
    private static void register(Registerable<PlacedFeature> context, RegistryKey<PlacedFeature> key, 
                               RegistryEntry<ConfiguredFeature<?, ?>> configuration,
                               PlacementModifier... modifiers) {
        register(context, key, configuration, List.of(modifiers));
    }
    
    private static void register(Registerable<PlacedFeature> context, RegistryKey<PlacedFeature> key, 
                               RegistryEntry<ConfiguredFeature<?, ?>> configuration,
                               List<PlacementModifier> modifiers) {
        context.register(key, new PlacedFeature(configuration, List.copyOf(modifiers)));
    }
} 