package starduster.circuitmod.block.machines;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.entity.MissileControlBlockEntity;
import starduster.circuitmod.block.entity.ModBlockEntities;
import starduster.circuitmod.entity.MissileEntity;
import starduster.circuitmod.item.ModItems;

public class MissileControlBlock extends BlockWithEntity {
    public static final MapCodec<MissileControlBlock> CODEC = createCodec(MissileControlBlock::new);

    @Override
    public MapCodec<MissileControlBlock> getCodec() {
        return CODEC;
    }

    public MissileControlBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH));
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
        return this.getDefaultState().with(HorizontalFacingBlock.FACING, facing);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(HorizontalFacingBlock.FACING);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new MissileControlBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        ItemStack heldStack = player.getStackInHand(Hand.MAIN_HAND);
        
        // Check if player is holding a missile item
        if (heldStack.getItem() == ModItems.MISSILE) {
            if (!world.isClient()) {
                BlockEntity blockEntity = world.getBlockEntity(pos);
                if (blockEntity instanceof MissileControlBlockEntity controlBlock) {
                    // Check if there's already a missile attached
                    if (controlBlock.hasMissile()) {
                        Circuitmod.LOGGER.info("[MISSILE-CONTROL] Block at {} already has a missile attached", pos);
                        return ActionResult.FAIL;
                    }
                    
                    // Spawn missile above the control block
                    Vec3d spawnPos = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
                    MissileEntity missile = new MissileEntity(world, spawnPos);
                    
                    // Set target position from control block
                    Vec3d targetPos = new Vec3d(
                        controlBlock.getTargetX() + 0.5,
                        controlBlock.getTargetY(),
                        controlBlock.getTargetZ() + 0.5
                    );
                    missile.setTargetPosition(targetPos);
                    
                    // Attach missile to control block (missile will stay fixed)
                    world.spawnEntity(missile);
                    controlBlock.attachMissile(missile);
                    
                    // Consume the item
                    if (!player.isCreative()) {
                        heldStack.decrement(1);
                    }
                    
                    Circuitmod.LOGGER.info("[MISSILE-CONTROL] Placed missile on control block at {}", pos);
                }
            }
            return ActionResult.SUCCESS;
        }
        
        // Otherwise, open the GUI
        if (!world.isClient()) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof MissileControlBlockEntity controlBlockEntity) {
                player.openHandledScreen(controlBlockEntity);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return validateTicker(type, ModBlockEntities.MISSILE_CONTROL_BLOCK_ENTITY,
            (world1, pos, state1, blockEntity) -> MissileControlBlockEntity.tick(world1, pos, state1, blockEntity));
    }
}

