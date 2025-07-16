package starduster.circuitmod.block.machines;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.entity.ModBlockEntities;
import starduster.circuitmod.block.entity.QuarryBlockEntity;

import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.sound.ModSounds;

public class QuarryBlock extends BlockWithEntity {
    // Use the FACING property from HorizontalFacingBlock
    public static final MapCodec<QuarryBlock> CODEC = createCodec(QuarryBlock::new);
    public static final BooleanProperty RUNNING = BooleanProperty.of("running");


    @Override
    public MapCodec<QuarryBlock> getCodec() {
        return CODEC;
    }

    public QuarryBlock(Settings settings) {
        super(settings);
        // Set the default facing direction to north
        this.setDefaultState(this.stateManager.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH));
        setDefaultState(getDefaultState().with(RUNNING, false));
    }


    // Create the block entity
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new QuarryBlockEntity(pos, state);
    }

    // Set the facing direction when placed
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
       // Circuitmod.LOGGER.info("Quarry placed with facing direction: " + facing);
        return this.getDefaultState().with(HorizontalFacingBlock.FACING, facing);
    }

    // Make sure to append the facing property to the block state
    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(HorizontalFacingBlock.FACING);
        builder.add(RUNNING);
    }

    // Update onUse to open the GUI
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            // Access the block entity
            BlockEntity blockEntity = world.getBlockEntity(pos);
            
            if (blockEntity instanceof QuarryBlockEntity) {
                Direction facing = state.get(HorizontalFacingBlock.FACING);
              //  Circuitmod.LOGGER.info("Quarry at " + pos + " has facing direction: " + facing);
                
                // Open the screen handler through the named screen handler factory
                player.openHandledScreen((QuarryBlockEntity) blockEntity);
            }
        }
        return ActionResult.SUCCESS;
    }

    // Allow the block to be rendered normally (not invisible like typical BlockEntities)
    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    // Set up ticker for the block entity
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
//        return world.isClient ? null :
//            validateTicker(type, ModBlockEntities.QUARRY_BLOCK_ENTITY, QuarryBlockEntity::tick);
        if(world.isClient()) {
            return null;
        }
        return validateTicker(type, ModBlockEntities.QUARRY_BLOCK_ENTITY,
                (world1, pos, state1, blockEntity) -> blockEntity.tick(world1, pos, state1, blockEntity));
    }

    //private int soundClock = 0;
//    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
//        double x = (double)pos.getX() + (double)0.5F;
//        double y = (double)pos.getY() + (double)0.5F;
//        double z = (double)pos.getZ() + (double)0.5F;
//
//        if(soundClock > 0 && state.get(RUNNING) == false) {
//            soundClock = 0;
//        }
//        if(state.get(RUNNING)) {
//            soundClock = soundClock + 1;
//            if(soundClock > 160){
//                world.playSoundClient(x, y, z, ModSounds.MINER_MACHINE_RUN, SoundCategory.BLOCKS, 1F, 1F, false);
//                world.addParticleClient(ParticleTypes.LARGE_SMOKE, x, y+1, z, 0.0F, 0.0F, 0.0F);
//                soundClock = 0;
//            }
//            world.addParticleClient(ParticleTypes.FLASH, x, y, z, 0.0F, 0.0F, 0.0F);
//        }
//    }
} 