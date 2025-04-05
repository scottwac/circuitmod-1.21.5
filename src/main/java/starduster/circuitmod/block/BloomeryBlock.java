package starduster.circuitmod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.block.entity.BloomeryBlockEntity;
import starduster.circuitmod.block.entity.ModBlockEntities;

public class BloomeryBlock extends BlockWithEntity {
    public static final MapCodec<BloomeryBlock> CODEC = createCodec(BloomeryBlock::new);
    
    public BloomeryBlock(Settings settings) {
        super(settings);
    }
    
    @Override
    public MapCodec<BloomeryBlock> getCodec() {
        return CODEC;
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
        return world.isClient ? null :
            validateTicker(type, ModBlockEntities.BLOOMERY_BLOCK_ENTITY, BloomeryBlockEntity::tick);
    }
    
    // Add furnace-like particles and sounds when active
    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (world.isClient) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof BloomeryBlockEntity bloomery && bloomery.isBurning()) {
                double x = (double)pos.getX() + 0.5;
                double y = (double)pos.getY() + 0.4;
                double z = (double)pos.getZ() + 0.5;
                
                if (random.nextDouble() < 0.1) {
                    world.playSound(null, pos, SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE, 
                        SoundCategory.BLOCKS, 1.0f, 1.0f);
                }
                
                
            }
        }
    }
} 