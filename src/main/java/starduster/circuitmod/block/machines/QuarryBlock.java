package starduster.circuitmod.block.machines;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.HorizontalFacingBlock;
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
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.entity.ModBlockEntities;
import starduster.circuitmod.block.entity.QuarryBlockEntity;

import org.jetbrains.annotations.Nullable;

public class QuarryBlock extends BlockWithEntity {
    // Use the FACING property from HorizontalFacingBlock
    public static final MapCodec<QuarryBlock> CODEC = createCodec(QuarryBlock::new);
    public static final BooleanProperty RUNNING = BooleanProperty.of("running");


    @Override
    public MapCodec<QuarryBlock> getCodec() {
        return CODEC;
    }

    public QuarryBlock(Settings settings) {
        super(settings);
        // Set the default facing direction to north
        this.setDefaultState(this.stateManager.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH));
        setDefaultState(getDefaultState().with(RUNNING, false));
    }


    // Create the block entity
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new QuarryBlockEntity(pos, state);
    }

    // Set the facing direction when placed
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
        Circuitmod.LOGGER.info("Quarry placed with facing direction: " + facing);
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
            
            if (blockEntity instanceof QuarryBlockEntity) {
                Direction facing = state.get(HorizontalFacingBlock.FACING);
                Circuitmod.LOGGER.info("Quarry at " + pos + " has facing direction: " + facing);
                
                // Open the screen handler through the named screen handler factory
                player.openHandledScreen((QuarryBlockEntity) blockEntity);
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
        return world.isClient ? null : 
            validateTicker(type, ModBlockEntities.QUARRY_BLOCK_ENTITY, QuarryBlockEntity::tick);
    }
} 