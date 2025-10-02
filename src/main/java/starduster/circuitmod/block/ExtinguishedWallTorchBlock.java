package starduster.circuitmod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * An extinguished wall torch block that produces no light or particles.
 * Used when wall torches are placed in the Luna dimension where they cannot burn.
 */
public class ExtinguishedWallTorchBlock extends WallTorchBlock {
    public static final MapCodec<ExtinguishedWallTorchBlock> CODEC = createCodec(ExtinguishedWallTorchBlock::new);

    public ExtinguishedWallTorchBlock(Settings settings) {
        super(ParticleTypes.FLAME, settings); // Particle type is ignored since we override randomDisplayTick
    }

    @Override
    public MapCodec<WallTorchBlock> getCodec() {
        return WallTorchBlock.CODEC;
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        // No particles - the torch is extinguished
    }
}
