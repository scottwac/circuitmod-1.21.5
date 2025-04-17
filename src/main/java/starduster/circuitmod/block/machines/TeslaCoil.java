package starduster.circuitmod.block.machines;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.entity.TeslaCoilBlockEntity;

public class TeslaCoil extends BlockWithEntity {
    public static final MapCodec<TeslaCoil> CODEC = createCodec(TeslaCoil::new);
    public static final BooleanProperty RUNNING = BooleanProperty.of("running");
//    private static final VoxelShape SHAPE = Block.createCuboidShape(0.0,0.0,0.0,16.0,1.0,16.0);
    private static final VoxelShape SHAPE = VoxelShapes.union(Block.createCuboidShape(0.0,0.0,0.0,16,16,16), Block.createCuboidShape(5.0,16.0,5.0,11.0,28.0,11.0), Block.createCuboidShape(1,28,1,15,32,15));

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public MapCodec<TeslaCoil> getCodec() {
        return CODEC;
    }

    public TeslaCoil(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH));
        setDefaultState(getDefaultState().with(RUNNING, false));
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
        Circuitmod.LOGGER.info("Tesla Coil placed with facing direction: " + facing);
        return this.getDefaultState().with(HorizontalFacingBlock.FACING, facing);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(HorizontalFacingBlock.FACING);
        builder.add(RUNNING);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TeslaCoilBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {

        return ActionResult.SUCCESS;
    }

    // Add method to connect to network when placed
    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);

    }

    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        
        super.onStateReplaced(state, world, pos, moved);
    }
} 