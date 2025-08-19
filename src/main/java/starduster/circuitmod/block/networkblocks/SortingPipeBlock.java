package starduster.circuitmod.block.networkblocks;

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
import starduster.circuitmod.block.entity.SortingPipeBlockEntity;
import starduster.circuitmod.item.network.ItemNetworkManager;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import net.minecraft.inventory.Inventory;
import java.util.Map;
import starduster.circuitmod.item.network.ItemNetwork;
import java.util.HashMap;

public class SortingPipeBlock extends BasePipeBlock {
    public static final MapCodec<SortingPipeBlock> CODEC = createCodec(SortingPipeBlock::new);

    @Override
    public MapCodec<SortingPipeBlock> getCodec() {
        return CODEC;
    }

    public SortingPipeBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SortingPipeBlockEntity(pos, state);
    }
    
    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        
        if (!world.isClient()) {
            // Connect to pipe network
            ItemNetworkManager.connectPipe(world, pos);
            
            // Notify the block entity that it was placed
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof SortingPipeBlockEntity sortingPipe) {
                sortingPipe.onPlaced();
            }
            
            Circuitmod.LOGGER.debug("[SORTING-PIPE-PLACE] Sorting pipe placed at {}", pos);
        }
    }
    
    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        if (!moved) {
            // Disconnect from item network before removal
            BlockEntity entity = world.getBlockEntity(pos);
            if (entity instanceof SortingPipeBlockEntity blockEntity) {
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
        
        return validateTicker(type, ModBlockEntities.SORTING_PIPE, (tickWorld, pos, tickState, blockEntity) -> {
            SortingPipeBlockEntity.tick(tickWorld, pos, tickState, (SortingPipeBlockEntity) blockEntity);
        });
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof SortingPipeBlockEntity pipe) {
                // Check if this is a double-click (within 3 seconds of last click)
                long currentTime = world.getTime();
                String playerId = player.getUuidAsString();
                String key = playerId + "_" + pos.toString();
                
                // Get the last click time for this player and pipe
                Long lastClickTime = lastClickTimes.get(key);
                boolean isDoubleClick = lastClickTime != null && (currentTime - lastClickTime) < 60; // 3 seconds (60 ticks)
                
                if (isDoubleClick) {
                    // Double click - open the GUI
                    lastClickTimes.remove(key); // Clear the timer
                    player.openHandledScreen(pipe);
                    return ActionResult.CONSUME;
                } else {
                    // Single click - show network info
                    lastClickTimes.put(key, currentTime);
                    
                    // Display sorting pipe and network info when right-clicked
                    player.sendMessage(Text.literal("§6Sorting Pipe Status:"), false);
                    
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
                        
                        // Show filter information
                        boolean hasFilters = false;
                        for (int i = 0; i < 6; i++) {
                            ItemStack filterStack = pipe.getFilterStack(i);
                            if (!filterStack.isEmpty()) {
                                if (!hasFilters) {
                                    player.sendMessage(Text.literal("§7Active filters:"), false);
                                    hasFilters = true;
                                }
                                Direction dir = SortingPipeBlockEntity.DIRECTION_ORDER[i];
                                player.sendMessage(Text.literal("§7  §9" + dir + "§7: " + filterStack.getItem().getName().getString()), false);
                            }
                        }
                        
                        if (!hasFilters) {
                            player.sendMessage(Text.literal("§7No filters configured - items can go any direction"), false);
                        }
                        
                        player.sendMessage(Text.literal("§7Right-click again within 3 seconds to open GUI"), false);
                        
                    } else {
                        player.sendMessage(Text.literal("§cNot connected to any network!"), false);
                        player.sendMessage(Text.literal("§7Try breaking and replacing the pipe"), false);
                        player.sendMessage(Text.literal("§7Right-click again within 3 seconds to open GUI"), false);
                    }
                }
            }
        }
        return ActionResult.SUCCESS;
    }
    
    // Track last click times for double-click detection
    private static final Map<String, Long> lastClickTimes = new HashMap<>();

    @Override
    protected void handleScheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random, BlockEntity blockEntity) {
        // Let the normal tick system handle pipe ticking
        // The hop-by-hop system doesn't need forced scheduled ticks
        if (blockEntity instanceof SortingPipeBlockEntity sortingPipe) {
            Circuitmod.LOGGER.debug("[SORTING-PIPE-SCHEDULED-TICK] Scheduled tick for sorting pipe at {}", pos);
            // The normal tick method will be called by the ticker - no need to force it
        }
    }
}