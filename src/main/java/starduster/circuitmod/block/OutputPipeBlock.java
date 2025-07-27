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
import starduster.circuitmod.block.entity.OutputPipeBlockEntity;

public class OutputPipeBlock extends BasePipeBlock {
    public static final MapCodec<OutputPipeBlock> CODEC = createCodec(OutputPipeBlock::new);

    @Override
    public MapCodec<OutputPipeBlock> getCodec() {
        return CODEC;
    }

    public OutputPipeBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new OutputPipeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient) {
            return null;
        }
        
        return validateTicker(type, ModBlockEntities.OUTPUT_PIPE, (tickWorld, pos, tickState, blockEntity) -> {
            OutputPipeBlockEntity.tick(tickWorld, pos, tickState, blockEntity);
        });
    }

    @Override
    protected void handleScheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random, BlockEntity blockEntity) {
        // Force an immediate tick of the output pipe
        if (blockEntity instanceof OutputPipeBlockEntity outputPipe) {
            OutputPipeBlockEntity.tick(world, pos, state, outputPipe);
        }
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        // Output pipes don't have a GUI, but we can provide feedback
        if (!world.isClient) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof OutputPipeBlockEntity outputPipe) {
                boolean isPowered = world.getReceivedRedstonePower(pos) > 0;
                // Could add feedback here if needed
            }
        }
        return ActionResult.SUCCESS;
    }
} 