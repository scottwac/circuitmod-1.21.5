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
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.entity.ModBlockEntities;
import starduster.circuitmod.block.entity.SolarPanelBlockEntity;
import starduster.circuitmod.power.EnergyNetwork;
import starduster.circuitmod.power.IPowerConnectable;
import starduster.circuitmod.power.EnergyNetworkManager;

public class SolarPanel extends BlockWithEntity {
    public static final MapCodec<SolarPanel> CODEC = createCodec(SolarPanel::new);

    @Override
    public MapCodec<SolarPanel> getCodec() {
        return CODEC;
    }

    public SolarPanel(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH));
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
        Circuitmod.LOGGER.info("Solar Panel placed with facing direction: " + facing);
        return this.getDefaultState().with(HorizontalFacingBlock.FACING, facing);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(HorizontalFacingBlock.FACING);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SolarPanelBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient() && world.getBlockEntity(pos) instanceof SolarPanelBlockEntity solarPanel) {
            // Show energy production stats to the player
            int currentProduction = solarPanel.getCurrentEnergyProduction();
            float lightLevel = solarPanel.getLastLightLevel();
            
            player.sendMessage(net.minecraft.text.Text.literal(
                String.format("Solar Panel - Energy: %d/tick, Light: %.1f%%", 
                    currentProduction, lightLevel * 100)), false);
        }
        return ActionResult.SUCCESS;
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
                Circuitmod.LOGGER.info("[SOLAR-PANEL] Block placed at {}, attempting network connection", pos);
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
            Circuitmod.LOGGER.info("[SOLAR-PANEL] Scheduled tick at {}, checking network connections", pos);
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

    // Add the ticker to enable the block entity to tick
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return validateTicker(type, ModBlockEntities.SOLAR_PANEL_BLOCK_ENTITY, 
            world.isClient() ? null : SolarPanelBlockEntity::tick);
    }
} 