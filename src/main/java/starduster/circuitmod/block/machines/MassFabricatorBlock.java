package starduster.circuitmod.block.machines;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import starduster.circuitmod.block.entity.ModBlockEntities;
import starduster.circuitmod.block.entity.MassFabricatorBlockEntity;
import starduster.circuitmod.power.EnergyNetworkManager;
import starduster.circuitmod.power.IPowerConnectable;
import starduster.circuitmod.Circuitmod;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

public class MassFabricatorBlock extends BlockWithEntity {
    public static final MapCodec<MassFabricatorBlock> CODEC = createCodec(MassFabricatorBlock::new);

    @Override
    public MapCodec<MassFabricatorBlock> getCodec() {
        return CODEC;
    }

    public MassFabricatorBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new MassFabricatorBlockEntity(pos, state);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof MassFabricatorBlockEntity) {
                player.openHandledScreen((MassFabricatorBlockEntity) blockEntity);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        
        if (!world.isClient()) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof IPowerConnectable) {
                // Use the standardized network connection method
                EnergyNetworkManager.onBlockPlaced(world, pos, (IPowerConnectable) blockEntity);
                Circuitmod.LOGGER.info("[MASS-FABRICATOR] Block placed at {}, attempting network connection", pos);
            }
        }
    }
    
    @Override
    protected BlockState getStateForNeighborUpdate(
        BlockState state,
        net.minecraft.world.WorldView world,
        net.minecraft.world.tick.ScheduledTickView tickView,
        BlockPos pos,
        Direction direction,
        BlockPos neighborPos,
        BlockState neighborState,
        net.minecraft.util.math.random.Random random
    ) {
        // Schedule a block tick to handle network connections (avoid modifying world during neighbor update)
        if (world instanceof World realWorld && !realWorld.isClient()) {
            realWorld.scheduleBlockTick(pos, this, 1);
        }
        
        return state;
    }
    
    @Override
    public void scheduledTick(BlockState state, net.minecraft.server.world.ServerWorld world, BlockPos pos, net.minecraft.util.math.random.Random random) {
        super.scheduledTick(state, world, pos, random);
        
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof IPowerConnectable) {
            // Try to connect to networks when neighbors change
            EnergyNetworkManager.findAndJoinNetwork(world, pos, (IPowerConnectable) blockEntity);
            Circuitmod.LOGGER.info("[MASS-FABRICATOR] Scheduled tick at {}, checking network connections", pos);
        }
    }

    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        if (!moved) {
            // Only handle network removal if the block is actually being removed, not moved
            EnergyNetworkManager.onBlockRemoved(world, pos);
        }
        
        super.onStateReplaced(state, world, pos, moved);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : 
            validateTicker(type, ModBlockEntities.MASS_FABRICATOR_BLOCK_ENTITY, MassFabricatorBlockEntity::tick);
    }
} 