package starduster.circuitmod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.block.entity.ModBlockEntities;
import starduster.circuitmod.block.entity.OutputPipeBlockEntity;
import starduster.circuitmod.item.network.ItemNetworkManager;
import net.minecraft.text.Text;
import net.minecraft.inventory.Inventory;
import starduster.circuitmod.item.network.ItemNetwork;
import java.util.Map;

public class OutputPipeBlock extends BasePipeBlock {
    public static final MapCodec<OutputPipeBlock> CODEC = createCodec(OutputPipeBlock::new);

    @Override
    public MapCodec<OutputPipeBlock> getCodec() {
        return CODEC;
    }

    public OutputPipeBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new OutputPipeBlockEntity(pos, state);
    }
    
    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        
        // Connect to item network
        if (!world.isClient && world.getBlockEntity(pos) instanceof OutputPipeBlockEntity blockEntity) {
            blockEntity.onPlaced();
        }
    }
    
    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        if (!moved) {
            // Disconnect from item network before removal
            BlockEntity entity = world.getBlockEntity(pos);
            if (entity instanceof OutputPipeBlockEntity blockEntity) {
                blockEntity.onRemoved();
            }
        }
        
        super.onStateReplaced(state, world, pos, moved);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient) {
            return null;
        }
        
        return validateTicker(type, ModBlockEntities.OUTPUT_PIPE, (tickWorld, pos, tickState, blockEntity) -> {
            OutputPipeBlockEntity.tick(tickWorld, pos, tickState, blockEntity);
        });
    }

    @Override
    protected void handleScheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random, BlockEntity blockEntity) {
        // Force an immediate tick of the output pipe
        if (blockEntity instanceof OutputPipeBlockEntity outputPipe) {
            OutputPipeBlockEntity.tick(world, pos, state, outputPipe);
        }
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof OutputPipeBlockEntity pipe) {
                // Display output pipe and network info when right-clicked
                player.sendMessage(Text.literal("§6Output Pipe Status:"), false);
                
                if (pipe.getNetwork() != null) {
                    ItemNetwork network = pipe.getNetwork();
                    player.sendMessage(Text.literal("§7Network ID: §9" + network.getNetworkId()), false);
                    player.sendMessage(Text.literal("§7Connected to network with §9" + network.getSize() + "§7 pipes"), false);
                    
                    // Show inventory information
                    Map<BlockPos, Inventory> connectedInventories = network.getConnectedInventories();
                    player.sendMessage(Text.literal("§7Connected inventories: §9" + connectedInventories.size()), false);
                    
                    if (!connectedInventories.isEmpty()) {
                        player.sendMessage(Text.literal("§7Inventory positions:"), false);
                        for (BlockPos invPos : connectedInventories.keySet()) {
                            Inventory inv = connectedInventories.get(invPos);
                            String invType = inv.getClass().getSimpleName();
                            player.sendMessage(Text.literal("§7  §9" + invPos + "§7 (" + invType + ")"), false);
                        }
                    }
                    
                    // Show source and destination counts
                    Map<BlockPos, Inventory> sourceInventories = network.getSourceInventories();
                    Map<BlockPos, Inventory> destinationInventories = network.getDestinationInventories();
                    player.sendMessage(Text.literal("§7Sources: §9" + sourceInventories.size() + "§7, Destinations: §9" + destinationInventories.size()), false);
                    
                    // Show current pipe state
                    if (!pipe.isEmpty()) {
                        ItemStack currentItem = pipe.getStack(0);
                        player.sendMessage(Text.literal("§7Current item: §9" + currentItem.getItem().getName().getString() + "§7 x" + currentItem.getCount()), false);
                    } else {
                        player.sendMessage(Text.literal("§7Current item: §9Empty"), false);
                    }
                    
                    // Show transfer cooldown
                    player.sendMessage(Text.literal("§7Transfer cooldown: §9" + pipe.getTransferCooldown()), false);
                    
                    // Show redstone power status
                    boolean isPowered = world.getReceivedRedstonePower(pos) > 0;
                    String powerStatus = isPowered ? "§aPowered" : "§cUnpowered";
                    player.sendMessage(Text.literal("§7Redstone power: " + powerStatus), false);
                    
                } else {
                    player.sendMessage(Text.literal("§cNot connected to any network!"), false);
                }
            }
        }
        return ActionResult.SUCCESS;
    }
} 