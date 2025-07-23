package starduster.circuitmod.worldgen.tree;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.TestableWorld;
import net.minecraft.world.gen.feature.TreeFeatureConfig;
import net.minecraft.world.gen.foliage.FoliagePlacer;
import net.minecraft.world.gen.foliage.FoliagePlacerType;

public class MegaFoliagePlacer extends FoliagePlacer {
    public static final MapCodec<MegaFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(instance ->
        fillFoliagePlacerFields(instance)
            .and(IntProvider.VALUE_CODEC.fieldOf("foliage_height").forGetter(placer -> placer.foliageHeight))
            .apply(instance, MegaFoliagePlacer::new));

    private final IntProvider foliageHeight;

    public MegaFoliagePlacer(IntProvider radius, IntProvider offset, IntProvider foliageHeight) {
        super(radius, offset);
        this.foliageHeight = foliageHeight;
    }

    @Override
    protected FoliagePlacerType<?> getType() {
        return ModFoliagePlacerTypes.MEGA_FOLIAGE_PLACER;
    }

    @Override
    protected void generate(TestableWorld world, BlockPlacer placer, Random random, TreeFeatureConfig config,
                          int trunkHeight, TreeNode treeNode, int foliageHeight, int radius, int offset) {
        
        // Generate a massive spherical canopy
        int canopyRadius = 25; // Much larger than normal trees
        int canopyHeight = this.foliageHeight.get(random);
        
        // Generate foliage in a sphere-like pattern
        for (int y = offset; y >= offset - canopyHeight; y--) {
            // Calculate radius for this layer (smaller at top and bottom)
            double layerProgress = (double) Math.abs(y - offset) / (double) canopyHeight;
            int layerRadius = (int) (canopyRadius * Math.sin(Math.PI * (1.0 - layerProgress)));
            
            this.generateSquare(world, placer, random, config, treeNode.getCenter(), layerRadius, y, false);
        }
    }

    @Override
    public int getRandomHeight(Random random, int trunkHeight, TreeFeatureConfig config) {
        return this.foliageHeight.get(random);
    }

    @Override
    protected boolean isInvalidForLeaves(Random random, int dx, int y, int dz, int radius, boolean giantTrunk) {
        // Create a more natural spherical shape
        double distance = Math.sqrt(dx * dx + dz * dz);
        double maxDistance = radius + random.nextFloat() * 2 - 1; // Add some randomness
        return distance > maxDistance;
    }

    @Override
    protected BlockState getFoliageState(Random random, BlockPos pos, TreeFeatureConfig config) {
        return config.foliageProvider.get(random, pos)
            .with(LeavesBlock.PERSISTENT, true);
    }
} 