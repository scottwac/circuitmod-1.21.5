package starduster.circuitmod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
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
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import net.minecraft.world.block.OrientationHelper;
import net.minecraft.world.tick.ScheduledTickView;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.entity.ModBlockEntities;
import starduster.circuitmod.block.entity.PowerCableBlockEntity;
import starduster.circuitmod.power.IPowerConnectable;

import java.util.Map;

public class PowerCableBlock extends BlockWithEntity {
    // Required codec for block registration in 1.21+
    public static final MapCodec<PowerCableBlock> CODEC = createCodec(PowerCableBlock::new);
    
    // Properties for each connection direction
    public static final BooleanProperty NORTH = Properties.NORTH;
    public static final BooleanProperty EAST = Properties.EAST;
    public static final BooleanProperty SOUTH = Properties.SOUTH;
    public static final BooleanProperty WEST = Properties.WEST;
    public static final BooleanProperty UP = Properties.UP;
    public static final BooleanProperty DOWN = Properties.DOWN;
    
    // Base shape for the center of the cable
    private static final VoxelShape CORE_SHAPE = Block.createCuboidShape(6.0, 6.0, 6.0, 10.0, 10.0, 10.0);
    
    // Shapes for each connection
    private static final VoxelShape NORTH_SHAPE = Block.createCuboidShape(6.0, 6.0, 0.0, 10.0, 10.0, 6.0);
    private static final VoxelShape EAST_SHAPE = Block.createCuboidShape(10.0, 6.0, 6.0, 16.0, 10.0, 10.0);
    private static final VoxelShape SOUTH_SHAPE = Block.createCuboidShape(6.0, 6.0, 10.0, 10.0, 10.0, 16.0);
    private static final VoxelShape WEST_SHAPE = Block.createCuboidShape(0.0, 6.0, 6.0, 6.0, 10.0, 10.0);
    private static final VoxelShape UP_SHAPE = Block.createCuboidShape(6.0, 10.0, 6.0, 10.0, 16.0, 10.0);
    private static final VoxelShape DOWN_SHAPE = Block.createCuboidShape(6.0, 0.0, 6.0, 10.0, 6.0, 10.0);
    
    // Map of directions to properties
    private static final Map<Direction, BooleanProperty> DIRECTION_PROPERTIES = Map.of(
        Direction.NORTH, NORTH,
        Direction.EAST, EAST,
        Direction.SOUTH, SOUTH,
        Direction.WEST, WEST,
        Direction.UP, UP,
        Direction.DOWN, DOWN
    );
    
    @Override
    public MapCodec<PowerCableBlock> getCodec() {
        return CODEC;
    }
    
    public PowerCableBlock(Settings settings) {
        super(settings);
        // Set default property values
        setDefaultState(getStateManager().getDefaultState()
            .with(NORTH, false)
            .with(EAST, false)
            .with(SOUTH, false)
            .with(WEST, false)
            .with(UP, false)
            .with(DOWN, false)
        );
    }
    
    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }
    
    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        VoxelShape shape = CORE_SHAPE;
        
        // Add shapes for each connection
        if (state.get(NORTH)) {
            shape = VoxelShapes.union(shape, NORTH_SHAPE);
        }
        if (state.get(EAST)) {
            shape = VoxelShapes.union(shape, EAST_SHAPE);
        }
        if (state.get(SOUTH)) {
            shape = VoxelShapes.union(shape, SOUTH_SHAPE);
        }
        if (state.get(WEST)) {
            shape = VoxelShapes.union(shape, WEST_SHAPE);
        }
        if (state.get(UP)) {
            shape = VoxelShapes.union(shape, UP_SHAPE);
        }
        if (state.get(DOWN)) {
            shape = VoxelShapes.union(shape, DOWN_SHAPE);
        }
        
        return shape;
    }
    
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockState state = getDefaultState();
        World world = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos();
        
        // Check each direction for connectable blocks
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.offset(direction);
            BlockState neighborState = world.getBlockState(neighborPos);
            
            // Connect if the neighbor is a cable or a machine that can connect to power
            boolean canConnect = false;
            
            // Case 1: Neighbor is another cable
            if (neighborState.getBlock() instanceof PowerCableBlock) {
                canConnect = true;
            } 
            // Case 2: Neighbor is a block entity that can connect to power
            else if (world.getBlockEntity(neighborPos) instanceof IPowerConnectable) {
                IPowerConnectable connectable = (IPowerConnectable) world.getBlockEntity(neighborPos);
                canConnect = connectable.canConnectPower(direction.getOpposite());
            }
            
            if (canConnect) {
                state = state.with(DIRECTION_PROPERTIES.get(direction), true);
            }
        }
        
        return state;
    }
    
    @Override
    protected BlockState getStateForNeighborUpdate(
        BlockState state,
        WorldView world,
        ScheduledTickView tickView,
        BlockPos pos,
        Direction direction,
        BlockPos neighborPos,
        BlockState neighborState,
        Random random
    ) {
        // Update connection state when a neighbor changes
        boolean canConnect = false;
        
        // Case 1: Neighbor is another cable
        if (neighborState.getBlock() instanceof PowerCableBlock) {
            canConnect = true;
        } 
        // Case 2: Neighbor is a block entity that can connect to power
        else if (world.getBlockEntity(neighborPos) instanceof IPowerConnectable) {
            IPowerConnectable connectable = (IPowerConnectable) world.getBlockEntity(neighborPos);
            canConnect = connectable.canConnectPower(direction.getOpposite());
        }
        
        return state.with(DIRECTION_PROPERTIES.get(direction), canConnect);
    }
    
    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        
        if (!world.isClient) {
            // Debug what's around us
            Circuitmod.LOGGER.info("PowerCable placed at " + pos + ". Checking for connectable neighbors...");
            
            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = pos.offset(dir);
                BlockEntity be = world.getBlockEntity(neighborPos);
                
                if (be instanceof IPowerConnectable) {
                    Circuitmod.LOGGER.info("Found connectable neighbor at " + neighborPos + ": " + be.getClass().getSimpleName());
                }
            }
            
            // Update network connections
            BlockEntity entity = world.getBlockEntity(pos);
            if (entity instanceof PowerCableBlockEntity) {
                ((PowerCableBlockEntity) entity).updateNetworkConnections();
            }
        }
    }
    
    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        if (!moved) {
            // Handle network splitting
            BlockEntity entity = world.getBlockEntity(pos);
            if (entity instanceof PowerCableBlockEntity) {
                PowerCableBlockEntity cable = (PowerCableBlockEntity) entity;
                cable.onRemoved();
            }
        }
        
        super.onStateReplaced(state, world, pos, moved);
    }
    
    // Allow the block to be rendered normally
    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
    
    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PowerCableBlockEntity(pos, state);
    }
    
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : 
            validateTicker(type, ModBlockEntities.POWER_CABLE_BLOCK_ENTITY, PowerCableBlockEntity::tick);
    }
} 