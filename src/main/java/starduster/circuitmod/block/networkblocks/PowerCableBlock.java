package starduster.circuitmod.block.networkblocks;

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
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.entity.ModBlockEntities;
import starduster.circuitmod.block.entity.PowerCableBlockEntity;
import starduster.circuitmod.power.IPowerConnectable;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;

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
    private static final VoxelShape CORE_SHAPE = Block.createCuboidShape(5.0, 5.0, 5.0, 11.0, 11.0, 11.0);
    
    // Shapes for each connection
    private static final VoxelShape NORTH_SHAPE = Block.createCuboidShape(5.0, 5.0, 0.0, 11.0, 11.0, 5.0);
    private static final VoxelShape EAST_SHAPE = Block.createCuboidShape(11.0, 5.0, 5.0, 16.0, 11.0, 11.0);
    private static final VoxelShape SOUTH_SHAPE = Block.createCuboidShape(5.0, 5.0, 11.0, 11.0, 11.0, 16.0);
    private static final VoxelShape WEST_SHAPE = Block.createCuboidShape(0.0, 5.0, 5.0, 5.0, 11.0, 11.0);
    private static final VoxelShape UP_SHAPE = Block.createCuboidShape(5.0, 11.0, 5.0, 11.0, 16.0, 11.0);
    private static final VoxelShape DOWN_SHAPE = Block.createCuboidShape(5.0, 0.0, 5.0, 11.0, 5.0, 11.0);
    
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
            
            // If this is a real world and not just a view, check for network connections
            if (world instanceof World && canConnect) {
                BlockEntity be = ((World) world).getBlockEntity(pos);
                if (be instanceof PowerCableBlockEntity cable && cable.getNetwork() != null) {
                    World realWorld = (World) world;
                    
                    // Schedule a tick to check for network connections to avoid modifying the world during neighbor updates
                    realWorld.scheduleBlockTick(pos, this, 1);
                }
            }
        }
        
        return state.with(DIRECTION_PROPERTIES.get(direction), canConnect);
    }
    
    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        
        if (!world.isClient && world instanceof ServerWorld serverWorld) {
            // Force load the chunk containing this cable and all adjacent chunks
            forceLoadCableChunks(serverWorld, pos, true);
            
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
                PowerCableBlockEntity cable = (PowerCableBlockEntity) entity;
                cable.updateNetworkConnections();
                
                // Initialize chunk loading for the newly placed cable
                if (world instanceof ServerWorld) {
                    // Schedule chunk loading for next tick to ensure everything is properly initialized
                    world.getServer().execute(() -> {
                        if (world != null && !world.isClient()) {
                            cable.updateChunkLoading();
                        }
                    });
                }
            }
        }
    }
    
    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        if (!moved) {
            // Unload the force-loaded chunks when cable is removed
            forceLoadCableChunks(world, pos, false);
            
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
    
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof PowerCableBlockEntity cable) {
                // Display cable and network info when right-clicked
                player.sendMessage(Text.literal("§7Power Cable Status:"), false);
                
                if (cable.getNetwork() != null) {
                    player.sendMessage(Text.literal("§7Network ID: §9" + cable.getNetwork().getNetworkId()), false);
                    player.sendMessage(Text.literal("§7Connected to network with §9" + cable.getNetwork().getSize() + "§7 blocks"), false);
                    player.sendMessage(Text.literal("§7Network energy: §9" + cable.getNetwork().getStoredEnergy() + "§7/§9" 
                        + cable.getNetwork().getMaxStorage()), false);
                    player.sendMessage(Text.literal("§7Last tick: §a+" + cable.getNetwork().getLastTickEnergyProduced() 
                        + "§7 produced, §c-" + cable.getNetwork().getLastTickEnergyConsumed() + "§7 consumed"), false);
                    
                    // Show battery info if there are batteries on the network
                    if (cable.getNetwork().getLastTickEnergyStoredInBatteries() > 0 || 
                        cable.getNetwork().getLastTickEnergyDrawnFromBatteries() > 0) {
                        player.sendMessage(Text.literal("§7Battery activity: §a+" + cable.getNetwork().getLastTickEnergyStoredInBatteries() 
                            + "§7 stored, §c-" + cable.getNetwork().getLastTickEnergyDrawnFromBatteries() + "§7 drawn"), false);
                    }
                    
                    // Show chunk loading info
                    player.sendMessage(Text.literal("§7Chunk loading: §aActive (§9" + cable.getLoadedChunkCount() + " chunks§7)"), false);
                } else {
                    player.sendMessage(Text.literal("§cNot connected to any network!"), false);
                    player.sendMessage(Text.literal("§7Chunk loading: §cInactive"), false);
                }
            }
        }
        return ActionResult.SUCCESS;
    }
    
    @Override
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        super.scheduledTick(state, world, pos, random);
        
        // Get the block entity
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof PowerCableBlockEntity cable) {
            // Check for neighbor connections
            if (cable.getNetwork() != null) {
                for (Direction dir : Direction.values()) {
                    if (state.get(DIRECTION_PROPERTIES.get(dir))) {
                        BlockPos neighborPos = pos.offset(dir);
                        BlockEntity neighborBe = world.getBlockEntity(neighborPos);
                        
                        if (neighborBe instanceof IPowerConnectable) {
                            IPowerConnectable connectable = (IPowerConnectable) neighborBe;
                            
                            // If the neighbor doesn't have a network yet, add it to ours
                            if (connectable.getNetwork() == null && connectable.canConnectPower(dir.getOpposite())) {
                                Circuitmod.LOGGER.info("Scheduled tick: Adding neighbor at " + neighborPos + " to network " + cable.getNetwork().getNetworkId());
                                cable.getNetwork().addBlock(neighborPos, connectable);
                            }
                            // If it has a different network, merge them
                            else if (connectable.getNetwork() != null && 
                                    connectable.getNetwork() != cable.getNetwork() && 
                                    connectable.canConnectPower(dir.getOpposite())) {
                                
                                // Check if the neighbor's network is already merged (prevent infinite loops)
                                String neighborNetworkId = connectable.getNetwork().getNetworkId();
                                if (neighborNetworkId.startsWith("MERGED-")) {
                                    Circuitmod.LOGGER.info("Scheduled tick: Found neighbor with already merged network at " + neighborPos + 
                                                          " (Network ID: " + neighborNetworkId + "), skipping merge");
                                    continue;
                                }
                                
                                Circuitmod.LOGGER.info("Scheduled tick: Merging networks");
                                cable.getNetwork().mergeWith(connectable.getNetwork());
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Force loads or unloads the chunk containing the cable and all 8 adjacent chunks.
     * This ensures power networks remain active even when players are far away.
     */
    private void forceLoadCableChunks(ServerWorld world, BlockPos cablePos, boolean forceLoad) {
        ChunkPos cableChunk = new ChunkPos(cablePos);
        
        // Force load the cable's chunk and all 8 adjacent chunks (3x3 grid)
        for (int xOffset = -1; xOffset <= 1; xOffset++) {
            for (int zOffset = -1; zOffset <= 1; zOffset++) {
                int chunkX = cableChunk.x + xOffset;
                int chunkZ = cableChunk.z + zOffset;
                
                boolean success = world.setChunkForced(chunkX, chunkZ, forceLoad);
                
                if (forceLoad && success) {
                    Circuitmod.LOGGER.info("PowerCable at {} force-loaded chunk [{}, {}]", 
                        cablePos, chunkX, chunkZ);
                } else if (!forceLoad && success) {
                    Circuitmod.LOGGER.info("PowerCable at {} unloaded force-loaded chunk [{}, {}]", 
                        cablePos, chunkX, chunkZ);
                }
            }
        }
        
        if (forceLoad) {
            Circuitmod.LOGGER.info("PowerCable at {} force-loaded 9 chunks (3x3 grid) to keep power network active", cablePos);
        } else {
            Circuitmod.LOGGER.info("PowerCable at {} removed force-loading for 9 chunks", cablePos);
        }
    }
} 