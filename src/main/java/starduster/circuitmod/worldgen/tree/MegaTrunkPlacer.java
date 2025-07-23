package starduster.circuitmod.worldgen.tree;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.TestableWorld;
import net.minecraft.world.gen.feature.TreeFeatureConfig;
import net.minecraft.world.gen.foliage.FoliagePlacer;
import net.minecraft.world.gen.trunk.TrunkPlacer;
import net.minecraft.world.gen.trunk.TrunkPlacerType;

import java.util.List;
import java.util.function.BiConsumer;

public class MegaTrunkPlacer extends TrunkPlacer {
    public static final MapCodec<MegaTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(instance ->
        fillTrunkPlacerFields(instance).apply(instance, MegaTrunkPlacer::new));

    public MegaTrunkPlacer(int baseHeight, int firstRandomHeight, int secondRandomHeight) {
        super(baseHeight, firstRandomHeight, secondRandomHeight);
    }

    @Override
    protected TrunkPlacerType<?> getType() {
        return ModTrunkPlacerTypes.MEGA_TRUNK_PLACER;
    }

    @Override
    public List<FoliagePlacer.TreeNode> generate(TestableWorld world, BiConsumer<BlockPos, BlockState> replacer, 
                                                Random random, int height, BlockPos startPos, TreeFeatureConfig config) {
        
        // Create a 20x20 trunk centered on startPos
        int trunkSize = 20;
        int offset = trunkSize / 2; // 10 blocks offset from center
        
        // Generate the trunk from startPos up to startPos + height
        for (int y = 0; y < height; y++) {
            for (int x = -offset; x < offset; x++) {
                for (int z = -offset; z < offset; z++) {
                    BlockPos logPos = startPos.add(x, y, z);
                    
                    // Only place logs if the position can be replaced
                    if (canReplace(world, logPos)) {
                        replacer.accept(logPos, config.trunkProvider.get(random, logPos));
                    }
                }
            }
        }
        
        // Return foliage nodes at the top of the trunk
        // Create multiple foliage nodes around the top for a massive canopy
        ImmutableList.Builder<FoliagePlacer.TreeNode> builder = ImmutableList.builder();
        
        // Main center node
        builder.add(new FoliagePlacer.TreeNode(startPos.up(height), 0, false));
        
        // Additional nodes around the edges for wider canopy
        int foliageOffset = 8;
        builder.add(new FoliagePlacer.TreeNode(startPos.add(foliageOffset, height, 0), 0, false));
        builder.add(new FoliagePlacer.TreeNode(startPos.add(-foliageOffset, height, 0), 0, false));
        builder.add(new FoliagePlacer.TreeNode(startPos.add(0, height, foliageOffset), 0, false));
        builder.add(new FoliagePlacer.TreeNode(startPos.add(0, height, -foliageOffset), 0, false));
        
        // Corner nodes
        builder.add(new FoliagePlacer.TreeNode(startPos.add(foliageOffset, height, foliageOffset), 0, false));
        builder.add(new FoliagePlacer.TreeNode(startPos.add(-foliageOffset, height, foliageOffset), 0, false));
        builder.add(new FoliagePlacer.TreeNode(startPos.add(foliageOffset, height, -foliageOffset), 0, false));
        builder.add(new FoliagePlacer.TreeNode(startPos.add(-foliageOffset, height, -foliageOffset), 0, false));
        
        return builder.build();
    }
} 