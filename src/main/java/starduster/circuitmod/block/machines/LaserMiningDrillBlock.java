package starduster.circuitmod.block.machines;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.entity.LaserMiningDrillBlockEntity;
import starduster.circuitmod.block.entity.ModBlockEntities;

public class LaserMiningDrillBlock extends BlockWithEntity {
    // Use the FACING property from HorizontalFacingBlock
    public static final MapCodec<LaserMiningDrillBlock> CODEC = createCodec(LaserMiningDrillBlock::new);
    public static final BooleanProperty RUNNING = BooleanProperty.of("running");

    @Override
    public MapCodec<LaserMiningDrillBlock> getCodec() {
        return CODEC;
    }

    public LaserMiningDrillBlock(Settings settings) {
        super(settings);
        // Set the default facing direction to north
        this.setDefaultState(this.stateManager.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH));
        setDefaultState(getDefaultState().with(RUNNING, false));
    }

    // Create the block entity
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new LaserMiningDrillBlockEntity(pos, state);
    }

    // Set the facing direction when placed
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
        Circuitmod.LOGGER.info("Laser Mining Drill placed with facing direction: " + facing);
        return this.getDefaultState().with(HorizontalFacingBlock.FACING, facing);
    }

    // Make sure to append the facing property to the block state
    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(HorizontalFacingBlock.FACING);
        builder.add(RUNNING);
    }

    // Update onUse to open the GUI
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            // Access the block entity
            BlockEntity blockEntity = world.getBlockEntity(pos);
            
            if (blockEntity instanceof LaserMiningDrillBlockEntity) {
                Direction facing = state.get(HorizontalFacingBlock.FACING);
                Circuitmod.LOGGER.info("Laser Mining Drill at " + pos + " has facing direction: " + facing);
                
                // Open the screen handler through the named screen handler factory
                player.openHandledScreen((LaserMiningDrillBlockEntity) blockEntity);
            }
        }
        return ActionResult.SUCCESS;
    }

    // Allow the block to be rendered normally (not invisible like typical BlockEntities)
    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    // Set up ticker for the block entity
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if(world.isClient()) {
            return null;
        }
        return validateTicker(type, ModBlockEntities.LASER_MINING_DRILL_BLOCK_ENTITY,
                (world1, pos, state1, blockEntity) -> blockEntity.tick(world1, pos, state1, blockEntity));
    }
} 