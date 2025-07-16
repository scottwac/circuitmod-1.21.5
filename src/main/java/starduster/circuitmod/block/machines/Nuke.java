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
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.entity.ModBlockEntities;
import starduster.circuitmod.block.entity.NukeBlockEntity;

import org.jetbrains.annotations.Nullable;

public class Nuke extends BlockWithEntity {
    public static final MapCodec<Nuke> CODEC = createCodec(Nuke::new);
    public static final BooleanProperty PRIMED = BooleanProperty.of("primed");

    @Override
    public MapCodec<Nuke> getCodec() {
        return CODEC;
    }

    public Nuke(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(PRIMED, false));
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new NukeBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(PRIMED);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof NukeBlockEntity nuke) {
                if (!nuke.isPrimed() && !nuke.isDetonating()) {
                    nuke.prime();
                    // Update block state to show it's primed
                    world.setBlockState(pos, state.with(PRIMED, true));
                    
                }
            }
        }
        return ActionResult.SUCCESS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : 
            validateTicker(type, ModBlockEntities.NUKE_BLOCK_ENTITY, NukeBlockEntity::tick);
    }
} 