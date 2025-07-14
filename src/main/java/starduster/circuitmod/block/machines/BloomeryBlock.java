package starduster.circuitmod.block.machines;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.block.entity.BloomeryBlockEntity;
import starduster.circuitmod.block.entity.ModBlockEntities;

public class BloomeryBlock extends BlockWithEntity {
    public static final MapCodec<BloomeryBlock> CODEC = BloomeryBlock.createCodec(BloomeryBlock::new);
    public static final BooleanProperty LIT = Properties.LIT;
    
    public BloomeryBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState()
            .with(Properties.HORIZONTAL_FACING, Direction.NORTH)
            .with(LIT, false));
    }
    
    @Override
    public MapCodec<BloomeryBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(Properties.HORIZONTAL_FACING, LIT);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return super.getPlacementState(ctx)
            .with(Properties.HORIZONTAL_FACING, ctx.getHorizontalPlayerFacing().getOpposite())
            .with(LIT, false);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
    
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new BloomeryBlockEntity(pos, state);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof BloomeryBlockEntity) {
                player.openHandledScreen((BloomeryBlockEntity) blockEntity);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if(world.isClient()) {
            return null;
        }

        return validateTicker(type, ModBlockEntities.BLOOMERY_BLOCK_ENTITY,
                (world1, pos, state1, blockEntity) -> blockEntity.tick(world1, pos, state1));
    }
    
    // Add furnace-like particles and sounds when active
    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (state.get(LIT)) {
            double x = (double)pos.getX() + 0.5;
            double y = (double)pos.getY() + 1.0;
            double z = (double)pos.getZ() + 0.5;
            
            if (random.nextDouble() < 0.1) {
                world.playSound(null, pos, SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE, 
                    SoundCategory.BLOCKS, 1.0f, 1.0f);
            }
            
            Direction direction = state.get(Properties.HORIZONTAL_FACING);
            Direction.Axis axis = direction.getAxis();
            double offset = random.nextDouble() * 0.6 - 0.3;
            double xOffset = axis == Direction.Axis.X ? 0.52 * direction.getOffsetX() : offset;
            double yOffset = random.nextDouble() * 6.0 / 16.0;
            double zOffset = axis == Direction.Axis.Z ? 0.52 * direction.getOffsetZ() : offset;
           // world.addParticle(ParticleTypes.SMOKE, x + xOffset, y, z + zOffset, 0.0, 0.0, 0.0);
           // world.addParticle(ParticleTypes.FLAME, x + xOffset, y, z + zOffset, 0.0, 0.0, 0.0);
        }
    }
} 