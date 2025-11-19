package starduster.circuitmod.block.machines;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.entity.HologramTableBlockEntity;
import starduster.circuitmod.block.entity.ModBlockEntities;

public class HologramTableBlock extends BlockWithEntity {
    public static final MapCodec<HologramTableBlock> CODEC = createCodec(HologramTableBlock::new);

    @Override
    public MapCodec<HologramTableBlock> getCodec() {
        return CODEC;
    }

    public HologramTableBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH));
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        // Always face north for consistent hologram rendering
        return this.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(HorizontalFacingBlock.FACING);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new HologramTableBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient()) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof HologramTableBlockEntity hologramEntity) {
                player.openHandledScreen(hologramEntity);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient()) {
            Circuitmod.LOGGER.info("[HOLOGRAM-TABLE] Placed hologram table at {}", pos);
            // Schedule a tick to check for chaining after placement
            world.scheduleBlockTick(pos, this, 1);
        }
    }

    @Override
    protected BlockState getStateForNeighborUpdate(
        BlockState state,
        net.minecraft.world.WorldView world,
        net.minecraft.world.tick.ScheduledTickView tickView,
        BlockPos pos,
        Direction direction,
        BlockPos neighborPos,
        BlockState neighborState,
        net.minecraft.util.math.random.Random random
    ) {
        // Schedule a block tick to check for chaining when neighbors change
        if (world instanceof World realWorld && !realWorld.isClient()) {
            // Check if neighbor is a hologram table
            if (neighborState.getBlock() instanceof HologramTableBlock) {
                Circuitmod.LOGGER.info("[HOLOGRAM-TABLE] Neighbor hologram table detected at {} (direction {}), scheduling tick", neighborPos, direction);
                realWorld.scheduleBlockTick(pos, this, 1);
                // Also schedule tick for the neighbor so it can update its chaining
                realWorld.scheduleBlockTick(neighborPos, neighborState.getBlock(), 1);
            }
        }
        
        return state;
    }
    
    @Override
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, net.minecraft.util.math.random.Random random) {
        super.scheduledTick(state, world, pos, random);
        
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof HologramTableBlockEntity hologramEntity) {
            Circuitmod.LOGGER.info("[HOLOGRAM-TABLE] Scheduled tick at {}, checking for chaining", pos);
            hologramEntity.checkForChaining(world, pos);
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient() ? null :
            validateTicker(type, ModBlockEntities.HOLOGRAM_TABLE_BLOCK_ENTITY, HologramTableBlockEntity::tick);
    }
}

