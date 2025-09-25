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
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.entity.ConstructorBlockEntity;
import starduster.circuitmod.block.entity.ModBlockEntities;
import starduster.circuitmod.power.EnergyNetworkManager;
import starduster.circuitmod.power.IPowerConnectable;

public class ConstructorBlock extends BlockWithEntity {
    public static final MapCodec<ConstructorBlock> CODEC = createCodec(ConstructorBlock::new);
    public static final BooleanProperty RUNNING = BooleanProperty.of("running");

    @Override
    public MapCodec<ConstructorBlock> getCodec() {
        return CODEC;
    }

    public ConstructorBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH));
        setDefaultState(getDefaultState().with(RUNNING, false));
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
        Circuitmod.LOGGER.info("Constructor placed with facing direction: " + facing);
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
        return new ConstructorBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            // Access the block entity
            BlockEntity blockEntity = world.getBlockEntity(pos);
            
            if (blockEntity instanceof ConstructorBlockEntity) {
                // Open the screen handler through the named screen handler factory
                player.openHandledScreen((ConstructorBlockEntity) blockEntity);
            }
        }
        return ActionResult.SUCCESS;
    }

    // Set up ticker for the block entity
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if(world.isClient()) {
            return null;
        }
        return validateTicker(type, ModBlockEntities.CONSTRUCTOR_BLOCK_ENTITY,
                ConstructorBlockEntity::tick);
    }

    // Add method to connect to network when placed
    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        
        if (!world.isClient()) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof IPowerConnectable) {
                // Use the standardized network connection method
                EnergyNetworkManager.onBlockPlaced(world, pos, (IPowerConnectable) blockEntity);
                Circuitmod.LOGGER.info("[CONSTRUCTOR] Block placed at {}, attempting network connection", pos);
            }
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
        // Schedule a block tick to handle network connections (avoid modifying world during neighbor update)
        if (world instanceof World realWorld && !realWorld.isClient()) {
            realWorld.scheduleBlockTick(pos, this, 1);
        }
        
        return state;
    }
    
    @Override
    public void scheduledTick(BlockState state, net.minecraft.server.world.ServerWorld world, BlockPos pos, net.minecraft.util.math.random.Random random) {
        super.scheduledTick(state, world, pos, random);
        
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof IPowerConnectable) {
            // Try to connect to networks when neighbors change
            EnergyNetworkManager.findAndJoinNetwork(world, pos, (IPowerConnectable) blockEntity);
            Circuitmod.LOGGER.info("[CONSTRUCTOR] Scheduled tick at {}, checking network connections", pos);
        }
    }

    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        if (!moved) {
            // Only handle network removal if the block is actually being removed, not moved
            EnergyNetworkManager.onBlockRemoved(world, pos);
        }
        
        super.onStateReplaced(state, world, pos, moved);
    }
} 