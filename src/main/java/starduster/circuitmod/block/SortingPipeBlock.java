package starduster.circuitmod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.block.entity.ModBlockEntities;
import starduster.circuitmod.block.entity.SortingPipeBlockEntity;

public class SortingPipeBlock extends BasePipeBlock {
    public static final MapCodec<SortingPipeBlock> CODEC = createCodec(SortingPipeBlock::new);

    @Override
    public MapCodec<SortingPipeBlock> getCodec() {
        return CODEC;
    }

    public SortingPipeBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SortingPipeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient) {
            return null;
        }
        
        return validateTicker(type, ModBlockEntities.SORTING_PIPE, (tickWorld, pos, tickState, blockEntity) -> {
            SortingPipeBlockEntity.tick(tickWorld, pos, tickState, (SortingPipeBlockEntity) blockEntity);
        });
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof SortingPipeBlockEntity sortingPipe) {
                // Open the sorting pipe GUI
                player.openHandledScreen(sortingPipe);
                return ActionResult.CONSUME;
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    protected void handleScheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random, BlockEntity blockEntity) {
        // Force an immediate tick of the sorting pipe
        if (blockEntity instanceof SortingPipeBlockEntity sortingPipe) {
            SortingPipeBlockEntity.tick(world, pos, state, sortingPipe);
        }
    }
} 