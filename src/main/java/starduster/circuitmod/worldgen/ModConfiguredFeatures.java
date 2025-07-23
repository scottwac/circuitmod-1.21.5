package starduster.circuitmod.worldgen;

import net.minecraft.block.Blocks;
import net.minecraft.registry.Registerable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.TreeFeatureConfig;
import net.minecraft.world.gen.feature.size.TwoLayersFeatureSize;
import net.minecraft.world.gen.stateprovider.BlockStateProvider;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.worldgen.tree.MegaFoliagePlacer;
import starduster.circuitmod.worldgen.tree.MegaTrunkPlacer;

public class ModConfiguredFeatures {
    
    public static final RegistryKey<ConfiguredFeature<?, ?>> MEGA_TREE_KEY = registerKey("mega_tree");
    
    public static void bootstrap(Registerable<ConfiguredFeature<?, ?>> context) {
        register(context, MEGA_TREE_KEY, Feature.TREE, new TreeFeatureConfig.Builder(
            // Use jungle logs for the trunk
            BlockStateProvider.of(Blocks.JUNGLE_LOG),
            // Custom mega trunk placer: 50 blocks tall base height, +0-10 random, +0-5 second random
            new MegaTrunkPlacer(50, 10, 5),
            
            // Use jungle leaves for foliage, set persistent to true
            BlockStateProvider.of(Blocks.JUNGLE_LEAVES.getDefaultState().with(net.minecraft.state.property.Properties.PERSISTENT, true)),
            // Custom mega foliage placer with large radius and height
            new MegaFoliagePlacer(ConstantIntProvider.create(15), ConstantIntProvider.create(2), ConstantIntProvider.create(20)),
            
            // Large minimum size requirement
            new TwoLayersFeatureSize(3, 0, 5)
        ).dirtProvider(BlockStateProvider.of(Blocks.DIRT)).build());
    }
    
    public static RegistryKey<ConfiguredFeature<?, ?>> registerKey(String name) {
        return RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, Identifier.of(Circuitmod.MOD_ID, name));
    }
    
    private static <FC extends net.minecraft.world.gen.feature.FeatureConfig, F extends Feature<FC>> void register(
            Registerable<ConfiguredFeature<?, ?>> context,
            RegistryKey<ConfiguredFeature<?, ?>> key, 
            F feature, 
            FC configuration) {
        context.register(key, new ConfiguredFeature<>(feature, configuration));
    }
} 