package starduster.circuitmod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.AbstractTorchBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * An extinguished torch block that produces no light or particles.
 * Used when torches are placed in the Luna dimension where they cannot burn.
 */
public class ExtinguishedTorchBlock extends AbstractTorchBlock {
    public static final MapCodec<ExtinguishedTorchBlock> CODEC = createCodec(ExtinguishedTorchBlock::new);

    public ExtinguishedTorchBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends AbstractTorchBlock> getCodec() {
        return CODEC;
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        // No particles - the torch is extinguished
    }
}
