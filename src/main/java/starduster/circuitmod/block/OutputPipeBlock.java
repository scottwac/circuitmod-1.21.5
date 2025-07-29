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
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.entity.ModBlockEntities;
import starduster.circuitmod.block.entity.OutputPipeBlockEntity;
import starduster.circuitmod.item.network.ItemNetworkManager;
import net.minecraft.text.Text;
import net.minecraft.inventory.Inventory;
import java.util.Map;
import starduster.circuitmod.item.network.ItemNetwork;

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
        
        if (!world.isClient()) {
            // Connect to pipe network
            ItemNetworkManager.connectPipe(world, pos);
            
            // Notify the block entity that it was placed
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof OutputPipeBlockEntity outputPipe) {
                outputPipe.onPlaced();
            }
            
            Circuitmod.LOGGER.debug("[OUTPUT-PIPE-PLACE] Output pipe placed at {}", pos);
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
            OutputPipeBlockEntity.tick(tickWorld, pos, tickState, (OutputPipeBlockEntity) blockEntity);
        });
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof OutputPipeBlockEntity pipe) {
                // Display output pipe and network info when right-clicked
                player.sendMessage(Text.literal("§6Output Pipe Status:"), false);
                
                ItemNetwork network = pipe.getNetwork();
                if (network != null) {
                    player.sendMessage(Text.literal("§7Network ID: §9" + network.getNetworkId()), false);
                    player.sendMessage(Text.literal("§7Connected to network with §9" + network.getSize() + "§7 pipes"), false);
                    
                    // Show inventory information
                    Map<BlockPos, Inventory> connectedInventories = network.getConnectedInventories();
                    player.sendMessage(Text.literal("§7Connected inventories: §9" + connectedInventories.size()), false);
                    
                    if (!connectedInventories.isEmpty()) {
                        player.sendMessage(Text.literal("§7Inventory positions:"), false);
                        int count = 0;
                        for (BlockPos invPos : connectedInventories.keySet()) {
                            if (count >= 3) { // Limit display to avoid spam
                                player.sendMessage(Text.literal("§7  ... and " + (connectedInventories.size() - 3) + " more"), false);
                                break;
                            }
                            Inventory inv = connectedInventories.get(invPos);
                            String invType = inv.getClass().getSimpleName();
                            player.sendMessage(Text.literal("§7  §9" + invPos + "§7 (" + invType + ")"), false);
                            count++;
                        }
                    }
                    
                    // Show current pipe state
                    if (!pipe.isEmpty()) {
                        ItemStack currentItem = pipe.getStack(0);
                        player.sendMessage(Text.literal("§7Current item: §9" + currentItem.getItem().getName().getString() + "§7 x" + currentItem.getCount()), false);
                    } else {
                        player.sendMessage(Text.literal("§7Current item: §9Empty"), false);
                    }
                    
                    // Show transfer cooldown
                    player.sendMessage(Text.literal("§7Transfer cooldown: §9" + pipe.getTransferCooldown()), false);
                    
                    // Show output-specific info
                    player.sendMessage(Text.literal("§7Function: §9Extracts from adjacent inventories"), false);
                    player.sendMessage(Text.literal("§7Extract rate: §9Every 20 ticks (1 second)"), false);
                    
                } else {
                    player.sendMessage(Text.literal("§cNot connected to any network!"), false);
                    player.sendMessage(Text.literal("§7Try breaking and replacing the pipe"), false);
                }
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    protected void handleScheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random, BlockEntity blockEntity) {
        // Let the normal tick system handle pipe ticking
        // The hop-by-hop system doesn't need forced scheduled ticks
        if (blockEntity instanceof OutputPipeBlockEntity outputPipe) {
            Circuitmod.LOGGER.debug("[OUTPUT-PIPE-SCHEDULED-TICK] Scheduled tick for output pipe at {}", pos);
            // The normal tick method will be called by the ticker - no need to force it
        }
    }
}