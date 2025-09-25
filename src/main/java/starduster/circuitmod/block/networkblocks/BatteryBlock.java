package starduster.circuitmod.block.networkblocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.block.entity.BatteryBlockEntity;
import starduster.circuitmod.block.entity.ModBlockEntities;
import starduster.circuitmod.power.IPowerConnectable;
import starduster.circuitmod.power.EnergyNetworkManager;
import starduster.circuitmod.Circuitmod;
import net.minecraft.server.world.ServerWorld;

public class BatteryBlock extends BlockWithEntity {
    public static final MapCodec<BatteryBlock> CODEC = createCodec(BatteryBlock::new);

    @Override
    public MapCodec<BatteryBlock> getCodec() {
        return CODEC;
    }

    public BatteryBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH)); //Sets default rotation state
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(Properties.HORIZONTAL_FACING);
    } //Adds state tag

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return super.getPlacementState(ctx).with(Properties.HORIZONTAL_FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    } //Gets and assigns the state

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new BatteryBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof BatteryBlockEntity battery) {
                // Open the battery screen
                player.openHandledScreen(battery);
            }
        }
        return ActionResult.SUCCESS;
    }

    // Add method to connect to network when placed
    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        
        if (!world.isClient && world instanceof ServerWorld serverWorld) {
            // Force load the chunk containing this battery and all adjacent chunks
            forceLoadBatteryChunks(serverWorld, pos, true);
            
            // Use the standardized network connection method
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof IPowerConnectable) {
                EnergyNetworkManager.onBlockPlaced(world, pos, (IPowerConnectable) blockEntity);
                Circuitmod.LOGGER.info("[BATTERY] Block placed at {}, attempting network connection", pos);
            }
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return validateTicker(type, ModBlockEntities.BATTERY_BLOCK_ENTITY, BatteryBlockEntity::tick);
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
            Circuitmod.LOGGER.info("[BATTERY] Scheduled tick at {}, checking network connections", pos);
        }
    }

    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        if (!moved) {
            // Unload the force-loaded chunks when battery is removed
            forceLoadBatteryChunks(world, pos, false);
            
            // Only handle network removal if the block is actually being removed, not moved
            EnergyNetworkManager.onBlockRemoved(world, pos);
        }
        
        super.onStateReplaced(state, world, pos, moved);
    }

    /**
     * Force loads or unloads the chunk containing the battery and all 8 adjacent chunks.
     * This ensures power networks remain active even when players are far away.
     */
    private void forceLoadBatteryChunks(ServerWorld world, BlockPos batteryPos, boolean forceLoad) {
        ChunkPos batteryChunk = new ChunkPos(batteryPos);
        
        // Force load the battery's chunk and all 8 adjacent chunks (3x3 grid)
        for (int xOffset = -1; xOffset <= 1; xOffset++) {
            for (int zOffset = -1; zOffset <= 1; zOffset++) {
                int chunkX = batteryChunk.x + xOffset;
                int chunkZ = batteryChunk.z + zOffset;
                
                boolean success = world.setChunkForced(chunkX, chunkZ, forceLoad);
                
                if (forceLoad && success) {
                    Circuitmod.LOGGER.info("Battery at {} force-loaded chunk [{}, {}]", 
                        batteryPos, chunkX, chunkZ);
                } else if (!forceLoad && success) {
                    Circuitmod.LOGGER.info("Battery at {} unloaded force-loaded chunk [{}, {}]", 
                        batteryPos, chunkX, chunkZ);
                }
            }
        }
        
        if (forceLoad) {
            Circuitmod.LOGGER.info("Battery at {} force-loaded 9 chunks (3x3 grid) to keep power network active", batteryPos);
        } else {
            Circuitmod.LOGGER.info("Battery at {} removed force-loading for 9 chunks", batteryPos);
        }
    }
} 