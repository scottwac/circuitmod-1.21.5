package starduster.circuitmod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
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
import starduster.circuitmod.block.entity.SolarPanelBlockEntity;

public class Nuke extends Block {
    public static final MapCodec<Nuke> CODEC = createCodec(Nuke::new);

    /**
     * I made this a regular Block, instead of a BlockWithEntity.
     * Every line relevant to making it a BlockWithEntity has just
     * been commented out, so you can make it one if needed.
     * The Block Entities classes have not been created yet.
     */

    @Override
    public MapCodec<Nuke> getCodec() {
        return CODEC;
    }

    public Nuke(Settings settings) {
        super(settings);

    }

//    @Nullable
//    @Override
//    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
//        return new SolarPanelBlockEntity(pos, state);
//    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

//    @Override
//    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
//
//        return ActionResult.SUCCESS;
//    }

    // Add method to connect to network when placed
//    @Override
//    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
//        super.onPlaced(world, pos, state, placer, itemStack);
//
//    }

//    @Override
//    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
//
//        super.onStateReplaced(state, world, pos, moved);
//    }
} 