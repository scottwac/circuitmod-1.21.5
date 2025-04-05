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
import starduster.circuitmod.power.EnergyNetwork;
import starduster.circuitmod.power.IPowerConnectable;

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
        
        if (!world.isClient) {
            // Check for adjacent power cables and connect to their network
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof CreativeConsumerBlockEntity consumer) {
                // Look for adjacent networks
                for (Direction dir : Direction.values()) {
                    BlockPos neighborPos = pos.offset(dir);
                    BlockEntity be = world.getBlockEntity(neighborPos);
                    
                    if (be instanceof IPowerConnectable) {
                        IPowerConnectable connectable = (IPowerConnectable) be;
                        EnergyNetwork network = connectable.getNetwork();
                        
                        if (network != null) {
                            // Found a network, join it
                            network.addBlock(pos, consumer);
                            break;
                        }
                    }
                }
            }
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return validateTicker(type, ModBlockEntities.CREATIVE_CONSUMER_BLOCK_ENTITY, CreativeConsumerBlockEntity::tick);
    }
} 