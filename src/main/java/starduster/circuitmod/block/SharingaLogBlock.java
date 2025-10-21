package starduster.circuitmod.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.PillarBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class SharingaLogBlock extends PillarBlock {
    public static final BooleanProperty NATURAL = BooleanProperty.of("natural");

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(NATURAL);
    }

    public SharingaLogBlock(Settings settings) {
        super(settings);
        setDefaultState(this.stateManager.getDefaultState().with(NATURAL, false));
    }
}
