package starduster.circuitmod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.block.entity.ModBlockEntities;
import starduster.circuitmod.block.entity.UpPipeBlockEntity;

public class UpPipeBlock extends BasePipeBlock {
    public static final MapCodec<UpPipeBlock> CODEC = createCodec(UpPipeBlock::new);

    @Override
    public MapCodec<UpPipeBlock> getCodec() {
        return CODEC;
    }

    public UpPipeBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new UpPipeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient) {
            return null;
        }
        
        return validateTicker(type, ModBlockEntities.UP_PIPE, (tickWorld, pos, tickState, blockEntity) -> {
            UpPipeBlockEntity.tick(tickWorld, pos, tickState, (UpPipeBlockEntity) blockEntity);
        });
    }
} 