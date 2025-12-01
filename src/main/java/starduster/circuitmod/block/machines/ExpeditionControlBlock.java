package starduster.circuitmod.block.machines;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.block.entity.ExpeditionControlBlockEntity;

public class ExpeditionControlBlock extends BlockWithEntity {
    public static final MapCodec<ExpeditionControlBlock> CODEC = createCodec(ExpeditionControlBlock::new);

    @Override
    public MapCodec<ExpeditionControlBlock> getCodec() {
        return CODEC;
    }

    public ExpeditionControlBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH));
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
        return this.getDefaultState().with(HorizontalFacingBlock.FACING, facing);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(HorizontalFacingBlock.FACING);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ExpeditionControlBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, net.minecraft.util.hit.BlockHitResult hit) {
        if (!world.isClient()) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof ExpeditionControlBlockEntity controlBlockEntity) {
                player.openHandledScreen(controlBlockEntity);
            }
        }
        return ActionResult.SUCCESS;
    }
}

