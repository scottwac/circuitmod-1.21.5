package starduster.circuitmod.block.machines;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
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
import starduster.circuitmod.block.entity.GeneratorBlockEntity;
import starduster.circuitmod.block.entity.ModBlockEntities;

public class Generator extends BlockWithEntity {
    public static final MapCodec<Generator> CODEC = createCodec(Generator::new);
    public static final BooleanProperty RUNNING = BooleanProperty.of("running");

    @Override
    public MapCodec<Generator> getCodec() {
        return CODEC;
    }

    public Generator(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH));
        setDefaultState(getDefaultState().with(RUNNING, false));
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
        Circuitmod.LOGGER.info("Generator placed with facing direction: " + facing);
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
        return new GeneratorBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            NamedScreenHandlerFactory screenHandlerFactory = state.createScreenHandlerFactory(world, pos);
            if (screenHandlerFactory != null) {
                player.openHandledScreen(screenHandlerFactory);
            }
        }
        return ActionResult.SUCCESS;
    }

    // Add method to connect to network when placed
    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        // The block entity will handle power network connection
    }

    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        if (!moved) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof GeneratorBlockEntity generator) {
                // Drop inventory items
                for (int i = 0; i < generator.size(); i++) {
                    ItemStack stack = generator.getStack(i);
                    if (!stack.isEmpty()) {
                        Block.dropStack(world, pos, stack);
                    }
                }
            }
        }
        super.onStateReplaced(state, world, pos, moved);
    }
    
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return validateTicker(type, ModBlockEntities.GENERATOR_BLOCK_ENTITY, GeneratorBlockEntity::tick);
    }
} 