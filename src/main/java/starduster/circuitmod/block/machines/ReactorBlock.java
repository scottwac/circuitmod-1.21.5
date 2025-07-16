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
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.entity.ReactorBlockBlockEntity;
import starduster.circuitmod.power.EnergyNetwork;
import starduster.circuitmod.power.IPowerConnectable;

public class ReactorBlock extends BlockWithEntity {
    public static final MapCodec<ReactorBlock> CODEC = createCodec(ReactorBlock::new);
    public static final IntProperty RODS = IntProperty.of("rods", 0, 9);

    @Override
    public MapCodec<ReactorBlock> getCodec() {
        return CODEC;
    }

    public ReactorBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH));
        setDefaultState(getDefaultState().with(RODS, 0));
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
        Circuitmod.LOGGER.info("Reactor placed with facing direction: " + facing);
        return this.getDefaultState().with(HorizontalFacingBlock.FACING, facing);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(HorizontalFacingBlock.FACING);
        builder.add(RODS);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ReactorBlockBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }

        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof ReactorBlockBlockEntity reactorEntity) {
            // For now, just open the inventory
            player.openHandledScreen(reactorEntity);
            return ActionResult.SUCCESS;
        }

        return ActionResult.FAIL;
    }

    // Add ticker for the block entity
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : 
            validateTicker(type, starduster.circuitmod.block.entity.ModBlockEntities.REACTOR_BLOCK_BLOCK_ENTITY, 
                ReactorBlockBlockEntity::tick);
    }

    // Add method to connect to network when placed
    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        
        if (!world.isClient) {
            // Check for adjacent power cables and connect to their network
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof ReactorBlockBlockEntity reactor) {
                // First, try to find an existing network to join
                boolean foundNetwork = false;
                
                // Look for adjacent networks
                for (Direction dir : Direction.values()) {
                    BlockPos neighborPos = pos.offset(dir);
                    BlockEntity be = world.getBlockEntity(neighborPos);
                    
                    if (be instanceof IPowerConnectable) {
                        IPowerConnectable connectable = (IPowerConnectable) be;
                        EnergyNetwork network = connectable.getNetwork();
                        
                        if (network != null) {
                            // Found a network, join it
                            network.addBlock(pos, reactor);
                            foundNetwork = true;
                            Circuitmod.LOGGER.info("Reactor at " + pos + " joined existing network " + network.getNetworkId());
                            break;
                        }
                    }
                }
                
                // If no existing network was found, create a new one with any adjacent IPowerConnectable blocks
                if (!foundNetwork) {
                    EnergyNetwork newNetwork = new EnergyNetwork();
                    newNetwork.addBlock(pos, reactor);
                    
                    for (Direction dir : Direction.values()) {
                        BlockPos neighborPos = pos.offset(dir);
                        BlockEntity be = world.getBlockEntity(neighborPos);
                        
                        if (be instanceof IPowerConnectable && ((IPowerConnectable) be).getNetwork() == null) {
                            IPowerConnectable connectable = (IPowerConnectable) be;
                            if (connectable.canConnectPower(dir.getOpposite()) && reactor.canConnectPower(dir)) {
                                newNetwork.addBlock(neighborPos, connectable);
                            }
                        }
                    }
                    
                    Circuitmod.LOGGER.info("Reactor at " + pos + " created new network " + newNetwork.getNetworkId());
                }
            }
        }
    }

    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        if (!moved) {
            // Handle network updates when this block is removed
            BlockEntity entity = world.getBlockEntity(pos);
            if (entity instanceof ReactorBlockBlockEntity reactor) {
                EnergyNetwork network = reactor.getNetwork();
                if (network != null) {
                    // Remove this block from the network
                    network.removeBlock(pos);
                    Circuitmod.LOGGER.info("Reactor at " + pos + " removed from network " + network.getNetworkId());
                }
            }
        }
        
        super.onStateReplaced(state, world, pos, moved);
    }
} 