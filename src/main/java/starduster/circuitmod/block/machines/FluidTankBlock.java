package starduster.circuitmod.block.machines;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.block.entity.FluidTankBlockEntity;
import starduster.circuitmod.block.entity.ModBlockEntities;
import net.minecraft.fluid.Fluids;


public class FluidTankBlock extends BlockWithEntity {
    public static final MapCodec<FluidTankBlock> CODEC = createCodec(FluidTankBlock::new);
    
    public FluidTankBlock(Settings settings) {
        super(settings);
    }

    @Override
    public MapCodec<FluidTankBlock> getCodec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new FluidTankBlockEntity(pos, state);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }

        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof FluidTankBlockEntity tank)) {
            return ActionResult.PASS;
        }

        // Bucket interactions with main hand
        ItemStack held = player.getMainHandStack();
        final int MB_PER_BUCKET = 1024;
        Direction side = hit.getSide();

        // Fill tank from water/lava bucket
        if (held.isOf(Items.WATER_BUCKET) || held.isOf(Items.LAVA_BUCKET)) {
            boolean isWater = held.isOf(Items.WATER_BUCKET);
            int inserted = tank.insertFluid(isWater ? Fluids.WATER : Fluids.LAVA, MB_PER_BUCKET, side);
            if (inserted == MB_PER_BUCKET) {
                if (!player.isCreative()) {
                    // If multiple buckets, consume one and give back empty bucket
                    if (held.getCount() > 1) {
                        held.decrement(1);
                        ItemStack empty = new ItemStack(Items.BUCKET);
                        if (!player.getInventory().insertStack(empty)) {
                            dropStack((ServerWorld) world, pos, empty);
                        }
                    } else {
                        player.setStackInHand(player.getActiveHand(), new ItemStack(Items.BUCKET));
                    }
                }
                return ActionResult.SUCCESS;
            }
        }

        // Drain tank into empty bucket
        if (held.isOf(Items.BUCKET)) {
            var storedType = tank.getStoredFluidType();
            if (storedType == Fluids.WATER || storedType == Fluids.LAVA) {
                int extracted = tank.extractFluid(storedType, MB_PER_BUCKET, side);
                if (extracted == MB_PER_BUCKET) {
                    if (!player.isCreative()) {
                        ItemStack filled = new ItemStack(storedType == Fluids.WATER ? Items.WATER_BUCKET : Items.LAVA_BUCKET);
                        if (held.getCount() > 1) {
                            held.decrement(1);
                            if (!player.getInventory().insertStack(filled)) {
                                dropStack((ServerWorld) world, pos, filled);
                            }
                        } else {
                            player.setStackInHand(player.getActiveHand(), filled);
                        }
                    }
                    return ActionResult.SUCCESS;
                }
            }
        }

        // Otherwise open GUI
        NamedScreenHandlerFactory screenHandlerFactory = (NamedScreenHandlerFactory) world.getBlockEntity(pos);
        if (screenHandlerFactory != null) {
            player.openHandledScreen(screenHandlerFactory);
        }
        return ActionResult.SUCCESS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : validateTicker(type, ModBlockEntities.FLUID_TANK_BLOCK_ENTITY,
                FluidTankBlockEntity::serverTick);
    }

    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        if (!moved) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof FluidTankBlockEntity tank) {
                for (int i = 0; i < tank.size(); i++) {
                    ItemStack stack = tank.getStack(i);
                    if (!stack.isEmpty()) {
                        dropStack(world, pos, stack);
                    }
                }
            }
        }
        super.onStateReplaced(state, world, pos, moved);
    }
} 