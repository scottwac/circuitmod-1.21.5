package starduster.circuitmod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.block.entity.CreativeConsumerBlockEntity;
import starduster.circuitmod.block.entity.ModBlockEntities;
import starduster.circuitmod.power.IPowerConnectable;
import starduster.circuitmod.power.EnergyNetworkManager;
import starduster.circuitmod.Circuitmod;
import net.minecraft.server.world.ServerWorld;

public class CreativeConsumerBlock extends BlockWithEntity {
    public static final MapCodec<CreativeConsumerBlock> CODEC = createCodec(CreativeConsumerBlock::new);

    @Override
    public MapCodec<CreativeConsumerBlock> getCodec() {
        return CODEC;
    }

    public CreativeConsumerBlock(Settings settings) {
        super(settings);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CreativeConsumerBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof CreativeConsumerBlockEntity consumer) {
                // Display network info when right-clicked
                player.sendMessage(Text.literal("§bCreative Consumer Status:"), false);
                
                if (consumer.getNetwork() != null) {
                    player.sendMessage(Text.literal("§7Network ID: §b" + consumer.getNetwork().getNetworkId()), false);
                    player.sendMessage(Text.literal("§7Connected to network with " + consumer.getNetwork().getSize() + " blocks"), false);
                    player.sendMessage(Text.literal("§7Energy stored: " + consumer.getNetwork().getStoredEnergy() + "/" + consumer.getNetwork().getMaxStorage()), false);
                    player.sendMessage(Text.literal("§7Last tick energy produced: " + consumer.getNetwork().getLastTickEnergyProduced()), false);
                    player.sendMessage(Text.literal("§7Last tick energy consumed: " + consumer.getNetwork().getLastTickEnergyConsumed()), false);
                    player.sendMessage(Text.literal("§7Energy received in last tick: " + consumer.getLastReceivedEnergy()), false);
                } else {
                    player.sendMessage(Text.literal("§cNot connected to any network!"), false);
                }
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        
        if (!world.isClient()) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof IPowerConnectable) {
                // Use the standardized network connection method
                EnergyNetworkManager.onBlockPlaced(world, pos, (IPowerConnectable) blockEntity);
                Circuitmod.LOGGER.info("[CREATIVE-CONSUMER] Block placed at {}, attempting network connection", pos);
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
            Circuitmod.LOGGER.info("[CREATIVE-CONSUMER] Scheduled tick at {}, checking network connections", pos);
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return validateTicker(type, ModBlockEntities.CREATIVE_CONSUMER_BLOCK_ENTITY, CreativeConsumerBlockEntity::tick);
    }

    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        if (!moved) {
            // Only handle network removal if the block is actually being removed, not moved
            EnergyNetworkManager.onBlockRemoved(world, pos);
        }
        
        super.onStateReplaced(state, world, pos, moved);
    }
} 