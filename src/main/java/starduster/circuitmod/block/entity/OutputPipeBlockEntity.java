package starduster.circuitmod.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.BasePipeBlock;
import starduster.circuitmod.block.ItemPipeBlock;
import starduster.circuitmod.item.network.ItemNetwork;
import starduster.circuitmod.item.network.ItemNetworkManager;
import starduster.circuitmod.item.network.ItemRoute;
import starduster.circuitmod.network.PipeNetworkAnimator;

import java.util.List;

public class OutputPipeBlockEntity extends BlockEntity implements Inventory {
    private static final int INVENTORY_SIZE = 1; 
    private static final int COOLDOWN_TICKS = 1; // Faster throughput for better item flow 
    public static final int ANIMATION_TICKS = 5; 
    
    private DefaultedList<ItemStack> inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private int transferCooldown = -1;
    private long lastTickTime;
    @Nullable Direction lastInputDirection = null; // Package-private for access from other pipe classes 
    private int lastAnimationTick = -1; 
    @Nullable BlockPos extractedFrom = null; // Track which inventory the current item was extracted from
    
    // Network reconnection flag for world load
    private boolean needsNetworkReconnection = false;
    
    public OutputPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OUTPUT_PIPE, pos, state);
    }
    
    /**
     * Called when the pipe is placed to connect to the item network.
     */
    public void onPlaced() {
        if (world != null && !world.isClient) {
            ItemNetworkManager.connectPipe(world, pos);
        }
    }
    
    /**
     * Called when the pipe is removed to disconnect from the item network.
     */
    public void onRemoved() {
        if (world != null && !world.isClient) {
            ItemNetworkManager.disconnectPipe(world, pos);
        }
    }

    /**
     * Called every tick to update the output pipe's state.
     */
    public static void tick(World world, BlockPos pos, BlockState state, OutputPipeBlockEntity blockEntity) {
        if (world.isClient()) {
            return;
        }
        
        // Debug logging control - set to true only when debugging
        final boolean DEBUG_LOGGING = false;
        
        // Handle network reconnection after world load
        if (blockEntity.needsNetworkReconnection) {
            // Only log if debug logging is enabled
            if (DEBUG_LOGGING && world.getTime() % 200 == 0) {
                Circuitmod.LOGGER.info("[OUTPUT-PIPE-RECONNECT] Reconnecting output pipe at {} to network after world load", pos);
            }
            ItemNetworkManager.connectPipe(world, pos);
            blockEntity.needsNetworkReconnection = false;
        }
        
        // Check for new unconnected inventories every 20 ticks (reduced from 5) for better performance
        if (world.getTime() % 20 == 0) {
            checkForUnconnectedInventories(world, pos, blockEntity);
        }
        
        // Monitor inventory changes for closer destinations gaining space - reduced frequency
        ItemNetwork network = ItemNetworkManager.getNetworkForPipe(pos);
        if (network != null && world.getTime() % 20 == 0) { // Reduced from 5 to 20 ticks
            network.monitorInventoryChanges();
        }
        
        // Periodically invalidate route cache to ensure fresh routes - reduced frequency
        if (network != null && world.getTime() % 40 == 0) { // Reduced from 20 to 40 ticks
            network.invalidateRouteCache();
            // Only log if debug logging is enabled
            if (DEBUG_LOGGING && world.getTime() % 200 == 0) {
                Circuitmod.LOGGER.info("[OUTPUT-PIPE-TICK] Periodic route cache invalidation at {}", pos);
            }
        }
        
        if (!blockEntity.needsCooldown()) {
            boolean didWork = false;
            
            // Only log if debug logging is enabled
            if (DEBUG_LOGGING && world.getTime() % 200 == 0) {
                Circuitmod.LOGGER.info("[OUTPUT-PIPE-TICK] Starting tick at {}, isEmpty: {}", pos, blockEntity.isEmpty());
            }
            
            // First priority: Route any existing item
            if (!blockEntity.isEmpty()) {
                // Only log if debug logging is enabled
                if (DEBUG_LOGGING && world.getTime() % 200 == 0) {
                    Circuitmod.LOGGER.info("[OUTPUT-PIPE-TICK] Has item {}, attempting to route from {}",
                        blockEntity.getStack(0).getItem().getName().getString(), pos);
                }
                didWork = routeItemThroughNetwork(world, pos, blockEntity);
                
                if (didWork && DEBUG_LOGGING && world.getTime() % 200 == 0) {
                    Circuitmod.LOGGER.info("[OUTPUT-PIPE-TICK] Successfully routed item from {}", pos);
                } else if (!didWork && DEBUG_LOGGING && world.getTime() % 200 == 0) {
                    Circuitmod.LOGGER.info("[OUTPUT-PIPE-TICK] Failed to route item from {}, item remains in pipe", pos);
                }
            }
            
            // Second priority: Extract new items only if pipe is now empty
            if (blockEntity.isEmpty()) {
                // Only log if debug logging is enabled
                if (DEBUG_LOGGING && world.getTime() % 200 == 0) {
                    Circuitmod.LOGGER.info("[OUTPUT-PIPE-TICK] Pipe is empty, attempting extraction at {}", pos);
                }
                boolean extracted = extractFromConnectedInventories(world, pos, state, blockEntity);
                if (extracted && DEBUG_LOGGING && world.getTime() % 200 == 0) {
                    Circuitmod.LOGGER.info("[OUTPUT-PIPE-TICK] Successfully extracted new item at {}", pos);
                }
                didWork = extracted;
            }
            
            if (didWork) {
                blockEntity.setTransferCooldown(3); // Match faster throughput
            }
        }
    }
    
    /**
     * Route an item through the network to its destination.
     */
    private static boolean routeItemThroughNetwork(World world, BlockPos pos, OutputPipeBlockEntity blockEntity) {
        ItemStack currentStack = blockEntity.getStack(0);
        if (currentStack.isEmpty()) {
            Circuitmod.LOGGER.info("[OUTPUT-PIPE-ROUTE] No item in pipe at {}", pos);
            return false;
        }

        Circuitmod.LOGGER.info("[OUTPUT-PIPE-ROUTE] Starting routing for {} from {}", 
            currentStack.getItem().getName().getString(), pos);

        // Get the network for this pipe
        ItemNetwork network = ItemNetworkManager.getNetworkForPipe(pos);
        if (network == null) {
            Circuitmod.LOGGER.info("[OUTPUT-PIPE-ROUTE] No network found for pipe at {}", pos);
            return false;
        }

        Circuitmod.LOGGER.info("[OUTPUT-PIPE-ROUTE] Found network {} for pipe at {}", 
            network.getNetworkId(), pos);

        // Find a route for this item, excluding where we extracted it from, with fallback support
        ItemRoute route = network.findRouteWithFallback(currentStack, pos, blockEntity.extractedFrom);
        if (route == null) {
            Circuitmod.LOGGER.info("[OUTPUT-PIPE-ROUTE] No route found for {} from {} (excluding {})", 
                currentStack.getItem().getName().getString(), pos, blockEntity.extractedFrom);
            return false;
        }

        Circuitmod.LOGGER.info("[OUTPUT-PIPE-ROUTE] Found route from {} to {} for {}", 
            route.getSource(), route.getDestination(), currentStack.getItem().getName().getString());

        // Get the next step towards the destination
        BlockPos nextStep = route.getNextPosition(pos);
        if (nextStep == null) {
            Circuitmod.LOGGER.info("[OUTPUT-PIPE-ROUTE] No next step found in route from {}", pos);
            return false;
        }

        Circuitmod.LOGGER.info("[OUTPUT-PIPE-ROUTE] Next step: {} -> {}", pos, nextStep);

        // Try to transfer to the next step
        if (transferToAdjacentPipe(world, pos, nextStep, blockEntity, currentStack)) {
            Circuitmod.LOGGER.info("[OUTPUT-PIPE-ROUTE] Successfully transferred {} from {} to {}", 
                currentStack.getItem().getName().getString(), pos, nextStep);
            blockEntity.extractedFrom = null; // Clear extraction source after successful transfer
            return true;
        }

        Circuitmod.LOGGER.info("[OUTPUT-PIPE-ROUTE] Failed to transfer {} from {} to {}", 
            currentStack.getItem().getName().getString(), pos, nextStep);
        return false;
    }
    
    /**
     * Checks for new unconnected inventories around the pipe and triggers a network rescan if needed.
     */
    private static void checkForUnconnectedInventories(World world, BlockPos pos, OutputPipeBlockEntity blockEntity) {
        ItemNetwork network = ItemNetworkManager.getNetworkForPipe(pos);
        if (network == null) {
            return;
        }
        
        // Check all 6 directions for inventories
        boolean foundNewInventory = false;
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.offset(direction);
            BlockState blockState = world.getBlockState(neighborPos);
            
            // Skip if it's a pipe
            if (blockState.getBlock() instanceof starduster.circuitmod.block.BasePipeBlock) {
                continue;
            }
            
            // Use the same inventory detection logic as in ItemNetwork
            net.minecraft.block.Block block = blockState.getBlock();
            Inventory inventory = null;
            
            // Check if block implements InventoryProvider interface
            if (block instanceof net.minecraft.block.InventoryProvider) {
                inventory = ((net.minecraft.block.InventoryProvider)block).getInventory(blockState, world, neighborPos);
            } 
            // Check if block has entity AND entity implements Inventory
            else if (blockState.hasBlockEntity()) {
                net.minecraft.block.entity.BlockEntity blockEntity2 = world.getBlockEntity(neighborPos);
                if (blockEntity2 instanceof Inventory inv) {
                    // Special case: Handle double chests properly
                    if (inv instanceof net.minecraft.block.entity.ChestBlockEntity && 
                        block instanceof net.minecraft.block.ChestBlock) {
                        inventory = net.minecraft.block.ChestBlock.getInventory((net.minecraft.block.ChestBlock)block, blockState, world, neighborPos, true);
                    } else {
                        inventory = inv;
                    }
                }
            }
            
            // If we found an inventory that's not in our network, trigger a rescan
            if (inventory != null && !network.getConnectedInventories().containsKey(neighborPos)) {
                Circuitmod.LOGGER.info("[OUTPUT-PIPE-DISCOVERY] Found new inventory at {} next to pipe at {}", neighborPos, pos);
                foundNewInventory = true;
                break; // Only need to find one to trigger rescan
            }
        }
        
        // If we found a new inventory, trigger a network rescan
        if (foundNewInventory) {
            Circuitmod.LOGGER.info("[OUTPUT-PIPE-DISCOVERY] Triggering network rescan due to new inventory discovery");
            network.forceRescanAllInventories();
        }
    }
    
    /**
     * Checks if two positions are adjacent (1 block apart).
     */
    private static boolean isAdjacent(BlockPos pos1, BlockPos pos2) {
        return Math.abs(pos1.getX() - pos2.getX()) + Math.abs(pos1.getY() - pos2.getY()) + Math.abs(pos1.getZ() - pos2.getZ()) == 1;
    }
    
    /**
     * Checks if an inventory at the given position can accept the item.
     */
    private static boolean canTransferToInventory(World world, BlockPos inventoryPos, ItemStack itemStack) {
        if (world.getBlockEntity(inventoryPos) instanceof Inventory inventory) {
            // Get the connection direction from pipe to inventory
            for (Direction direction : Direction.values()) {
                if (inventory instanceof net.minecraft.inventory.SidedInventory sidedInventory) {
                    int[] availableSlots = sidedInventory.getAvailableSlots(direction);
                    for (int slot : availableSlots) {
                        if (sidedInventory.canInsert(slot, itemStack, direction)) {
                            ItemStack stackInSlot = sidedInventory.getStack(slot);
                            if (stackInSlot.isEmpty() || 
                                (ItemStack.areItemsAndComponentsEqual(stackInSlot, itemStack) && 
                                 stackInSlot.getCount() + itemStack.getCount() <= stackInSlot.getMaxCount())) {
                                return true;
                            }
                        }
                    }
                } else {
                    // Regular inventory - check if any slot can accept the item
                    for (int slot = 0; slot < inventory.size(); slot++) {
                        ItemStack stackInSlot = inventory.getStack(slot);
                        if (stackInSlot.isEmpty() || 
                            (ItemStack.areItemsAndComponentsEqual(stackInSlot, itemStack) && 
                             stackInSlot.getCount() + itemStack.getCount() <= stackInSlot.getMaxCount())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * Transfers an item directly to an adjacent inventory (not a pipe).
     */
    private static boolean transferToInventory(World world, BlockPos fromPos, BlockPos toPos, 
                                             OutputPipeBlockEntity fromPipe, ItemStack stack) {
        if (world.getBlockEntity(toPos) instanceof Inventory inventory) {
            // Try to insert the item
            ItemStack remaining = insertItemIntoInventory(inventory, stack.copy());
            if (!ItemStack.areEqual(remaining, stack)) {
                // Some or all of the item was inserted
                int transferred = stack.getCount() - remaining.getCount();
                fromPipe.getStack(0).decrement(transferred);
                fromPipe.markDirty();
                return transferred > 0;
            }
        }
        return false;
    }
    
    /**
     * Helper method to insert an item into an inventory, handling SidedInventory properly.
     */
    private static ItemStack insertItemIntoInventory(Inventory inventory, ItemStack stack) {
        if (inventory instanceof net.minecraft.inventory.SidedInventory sidedInventory) {
            // Try all directions for SidedInventory
            for (Direction direction : Direction.values()) {
                int[] availableSlots = sidedInventory.getAvailableSlots(direction);
                for (int slot : availableSlots) {
                    if (sidedInventory.canInsert(slot, stack, direction)) {
                        ItemStack stackInSlot = sidedInventory.getStack(slot);
                        if (stackInSlot.isEmpty()) {
                            sidedInventory.setStack(slot, stack.copy());
                            return ItemStack.EMPTY;
                        } else if (ItemStack.areItemsAndComponentsEqual(stackInSlot, stack)) {
                            int canAdd = Math.min(stack.getCount(), stackInSlot.getMaxCount() - stackInSlot.getCount());
                            if (canAdd > 0) {
                                stackInSlot.increment(canAdd);
                                stack.decrement(canAdd);
                                if (stack.isEmpty()) {
                                    return ItemStack.EMPTY;
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Handle regular inventory
            for (int slot = 0; slot < inventory.size(); slot++) {
                ItemStack stackInSlot = inventory.getStack(slot);
                if (stackInSlot.isEmpty()) {
                    inventory.setStack(slot, stack.copy());
                    return ItemStack.EMPTY;
                } else if (ItemStack.areItemsAndComponentsEqual(stackInSlot, stack)) {
                    int canAdd = Math.min(stack.getCount(), stackInSlot.getMaxCount() - stackInSlot.getCount());
                    if (canAdd > 0) {
                        stackInSlot.increment(canAdd);
                        stack.decrement(canAdd);
                        if (stack.isEmpty()) {
                            return ItemStack.EMPTY;
                        }
                    }
                }
            }
        }
        return stack; // Return remaining items
    }
    
    /**
     * Get the next step in a route from the current position.
     */
    private static BlockPos getNextStepInRoute(ItemRoute route, BlockPos currentPos) {
        List<BlockPos> path = route.getPath();
        
        for (int i = 0; i < path.size() - 1; i++) {
            if (path.get(i).equals(currentPos)) {
                return path.get(i + 1);
            }
        }
        
        return null;
    }
    
    /**
     * Transfer item to an adjacent pipe only.
     */
    private static boolean transferToAdjacentPipe(World world, BlockPos fromPos, BlockPos toPos, 
                                                   OutputPipeBlockEntity fromPipe, ItemStack stack) {
        Circuitmod.LOGGER.info("[OUTPUT-PIPE-TRANSFER] Attempting transfer {} from {} to {}", 
            stack.getItem().getName().getString(), fromPos, toPos);
            
        // Debug the block state at the target position
        BlockState targetBlockState = world.getBlockState(toPos);
        net.minecraft.block.Block targetBlock = targetBlockState.getBlock();
        Circuitmod.LOGGER.info("[OUTPUT-PIPE-TRANSFER] Target block type: {} at {}", 
            targetBlock.getClass().getSimpleName(), toPos);
        Circuitmod.LOGGER.info("[OUTPUT-PIPE-TRANSFER] Is BasePipeBlock? {}", 
            targetBlock instanceof BasePipeBlock);
        Circuitmod.LOGGER.info("[OUTPUT-PIPE-TRANSFER] Is ItemPipeBlock? {}", 
            targetBlock instanceof starduster.circuitmod.block.ItemPipeBlock);
            
        // Only transfer to pipes, not inventories (Output pipes don't output to inventories)
        if (!(targetBlock instanceof BasePipeBlock)) {
            Circuitmod.LOGGER.info("[OUTPUT-PIPE-TRANSFER] Target {} is not a pipe block", toPos);
            return false;
        }
        
        if (world.getBlockEntity(toPos) instanceof Inventory targetPipe) {
            if (targetPipe.isEmpty()) {
                Circuitmod.LOGGER.info("[OUTPUT-PIPE-TRANSFER] Target pipe {} is empty, proceeding with transfer", toPos);
                
                // Transfer the stack to the target pipe
                targetPipe.setStack(0, stack.copy());
                
                // CRITICAL: Clear the item from the source pipe to prevent duplication
                fromPipe.setStack(0, ItemStack.EMPTY);
                Circuitmod.LOGGER.info("[OUTPUT-PIPE-TRANSFER] Cleared source item from {} to prevent duplication", fromPos);
            
                // Update target pipe's input direction and source exclusion
                if (targetPipe instanceof ItemPipeBlockEntity itemPipe) {
                    Direction direction = getDirectionTowards(fromPos, toPos);
                    if (direction != null) {
                        itemPipe.lastInputDirection = direction.getOpposite();
                        itemPipe.setTransferCooldown(COOLDOWN_TICKS);
                        // CRITICAL: Set sourceExclusion to prevent infinite loops
                        itemPipe.sourceExclusion = fromPos;
                        Circuitmod.LOGGER.info("[OUTPUT-PIPE-TRANSFER] Set input direction {} and sourceExclusion {} for target pipe {}", 
                            direction.getOpposite(), fromPos, toPos);
                    }
                } else if (targetPipe instanceof SortingPipeBlockEntity sortingPipe) {
                    Direction direction = getDirectionTowards(fromPos, toPos);
                    if (direction != null) {
                        sortingPipe.setLastInputDirection(direction.getOpposite());
                        sortingPipe.setTransferCooldown(COOLDOWN_TICKS);
                        // sourceExclusion removed - network routing handles loop prevention properly
                        Circuitmod.LOGGER.info("[OUTPUT-PIPE-TRANSFER] Set input direction {} for SORTING pipe {}", 
                            direction.getOpposite(), toPos);
                    }
                }
            
                // Send animation
                if (world instanceof ServerWorld serverWorld) {
                    fromPipe.sendAnimationIfAllowed(serverWorld, stack, fromPos, toPos);
                }
            
                targetPipe.markDirty();
                fromPipe.markDirty(); // Mark source pipe dirty since we cleared its item
                
                Circuitmod.LOGGER.info("[OUTPUT-PIPE-TRANSFER] Successfully transferred {} from {} to {}", 
                    stack.getItem().getName().getString(), fromPos, toPos);
                return true;
            } else {
                Circuitmod.LOGGER.info("[OUTPUT-PIPE-TRANSFER] Target pipe {} is not empty, cannot transfer", toPos);
                return false;
            }
        } else {
            Circuitmod.LOGGER.info("[OUTPUT-PIPE-TRANSFER] Target {} is not an inventory", toPos);
            return false;
        }
    }
    
    /**
     * Get the direction from one position to another.
     */
    private static Direction getDirectionTowards(BlockPos from, BlockPos to) {
        BlockPos diff = to.subtract(from);
        
        if (diff.getX() == 1) return Direction.EAST;
        if (diff.getX() == -1) return Direction.WEST;
        if (diff.getY() == 1) return Direction.UP;
        if (diff.getY() == -1) return Direction.DOWN;
        if (diff.getZ() == 1) return Direction.SOUTH;
        if (diff.getZ() == -1) return Direction.NORTH;
        
        return null;
    }
    
    /**
     * Extract items from all connected inventories (not pipes).
     */
    private static boolean extractFromConnectedInventories(World world, BlockPos pos, BlockState state, OutputPipeBlockEntity blockEntity) {
        // Only extract if we have room (output pipe can hold 1 item)
        if (!blockEntity.isEmpty()) {
            Circuitmod.LOGGER.info("[OUTPUT-PIPE-EXTRACT-SKIP] Pipe at {} already has item {}, skipping extraction", 
                pos, blockEntity.getStack(0).getItem().getName().getString());
            return false; 
        }
        
        Circuitmod.LOGGER.info("[OUTPUT-PIPE-EXTRACT-SCAN] Scanning for inventories around {}", pos);
        
        // Check all six directions for inventories to extract from (regardless of connections)
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.offset(direction);
            
            // Don't extract from other pipes
            if (world.getBlockState(neighborPos).getBlock() instanceof BasePipeBlock) {
                continue;
            }
            
            // Check if there's an inventory to extract from
            Inventory neighborInventory = getInventoryAt(world, neighborPos);
            if (neighborInventory != null) {
                Circuitmod.LOGGER.info("[OUTPUT-PIPE-EXTRACT-FOUND] Found inventory at {} in direction {}: {}", 
                    neighborPos, direction, neighborInventory.getClass().getSimpleName());
                
                Direction extractDirection = direction.getOpposite();
                Circuitmod.LOGGER.info("[OUTPUT-PIPE-EXTRACT-FOUND] Extract direction: {} (from pipe perspective)", extractDirection);
                
                // First, try to find a full stack to extract
                int fullStackSlot = findFullStackSlot(neighborInventory, extractDirection);
                Circuitmod.LOGGER.info("[OUTPUT-PIPE-EXTRACT-SCAN] Full stack slot: {}", fullStackSlot);
                
                if (fullStackSlot >= 0) {
                    ItemStack stackToExtract = neighborInventory.getStack(fullStackSlot);
                    Circuitmod.LOGGER.info("[OUTPUT-PIPE-EXTRACT-ATTEMPT] Attempting to extract full stack: {} x{} from slot {}", 
                        stackToExtract.getItem().getName().getString(), stackToExtract.getCount(), fullStackSlot);
                    
                    if (extract(blockEntity, neighborInventory, fullStackSlot, extractDirection)) {
                        blockEntity.lastInputDirection = direction;
                        blockEntity.extractedFrom = neighborPos; // Store where we extracted from
                        blockEntity.markDirty();
                        
                        // Send animation for item entering the pipe
                        if (world instanceof ServerWorld serverWorld) {
                            ItemStack extractedStack = blockEntity.getStack(0);
                            blockEntity.sendAnimationIfAllowed(serverWorld, extractedStack, neighborPos, pos);
                        }
                        
                        Circuitmod.LOGGER.info("[OUTPUT-PIPE-EXTRACT-SUCCESS] Extracted full stack from {} at {}", direction, pos);
                        return true;
                    } else {
                        Circuitmod.LOGGER.info("[OUTPUT-PIPE-EXTRACT-FAILED] Failed to extract full stack from slot {}", fullStackSlot);
                    }
                }
                
                // If no full stack found, try to find the largest available stack
                int largestStackSlot = findLargestStackSlot(neighborInventory, extractDirection);
                Circuitmod.LOGGER.info("[OUTPUT-PIPE-EXTRACT-SCAN] Largest stack slot: {}", largestStackSlot);
                
                if (largestStackSlot >= 0) {
                    ItemStack stackToExtract = neighborInventory.getStack(largestStackSlot);
                    Circuitmod.LOGGER.info("[OUTPUT-PIPE-EXTRACT-ATTEMPT] Attempting to extract largest stack: {} x{} from slot {}", 
                        stackToExtract.getItem().getName().getString(), stackToExtract.getCount(), largestStackSlot);
                    
                    if (extract(blockEntity, neighborInventory, largestStackSlot, extractDirection)) {
                        blockEntity.lastInputDirection = direction;
                        blockEntity.extractedFrom = neighborPos; // Store where we extracted from
                        blockEntity.markDirty();
                        
                        // Send animation for item entering the pipe
                        if (world instanceof ServerWorld serverWorld) {
                            ItemStack extractedStack = blockEntity.getStack(0);
                            blockEntity.sendAnimationIfAllowed(serverWorld, extractedStack, neighborPos, pos);
                        }
                        
                        Circuitmod.LOGGER.info("[OUTPUT-PIPE-EXTRACT-SUCCESS] Extracted largest stack from {} at {}", direction, pos);
                        return true;
                    } else {
                        Circuitmod.LOGGER.info("[OUTPUT-PIPE-EXTRACT-FAILED] Failed to extract largest stack from slot {}", largestStackSlot);
                    }
                }
            } else {
                Circuitmod.LOGGER.info("[OUTPUT-PIPE-EXTRACT-SCAN] No inventory found at {} in direction {}", neighborPos, direction);
            }
        }
        
        Circuitmod.LOGGER.info("[OUTPUT-PIPE-EXTRACT-SCAN] No items extracted from any inventory around {}", pos);
        return false;
    }
    

    

    
    /**
     * Extract an item from an inventory into the pipe.
     */
    private static boolean extract(OutputPipeBlockEntity pipe, Inventory inventory, int slot, Direction side) {
        ItemStack stack = inventory.getStack(slot);
        if (!stack.isEmpty() && canExtract(inventory, stack, slot, side)) {
            // Only extract if pipe is empty
            if (pipe.isEmpty()) {
                // Create a copy for the pipe
                ItemStack extracted = stack.copy();
                pipe.setStack(0, extracted);
                
                // IMPORTANT: Decrement the original stack directly to prevent duplication
                int extractedCount = extracted.getCount();
                stack.setCount(stack.getCount() - extractedCount);
                
                // If the original stack is now empty, clear the slot
                if (stack.getCount() <= 0) {
                    inventory.setStack(slot, ItemStack.EMPTY);
                }
                
                inventory.markDirty();
                
                Circuitmod.LOGGER.info("[OUTPUT-PIPE-EXTRACT] Extracted {} x{} from slot {}, remaining: {}", 
                    extracted.getItem().getName().getString(), extractedCount, slot, stack.getCount());
                
                return true;
            }
        }
        return false;
    }
    

    
    /**
     * Send an animation if enough time has passed since the last one to prevent duplicates
     */
    private void sendAnimationIfAllowed(ServerWorld world, ItemStack stack, BlockPos from, BlockPos to) {
        int currentTick = (int) world.getTime();
        
        // Only send animation if at least 3 ticks have passed since the last one
        if (currentTick - this.lastAnimationTick >= 3) {
            PipeNetworkAnimator.sendMoveAnimation(world, stack.copy(), from, to, ANIMATION_TICKS);
            this.lastAnimationTick = currentTick;
        }
    }
    
    public void setTransferCooldown(int cooldown) {
        this.transferCooldown = cooldown;
    }
    
    public int getTransferCooldown() {
        return this.transferCooldown;
    }
    
    /**
     * Gets the current network this pipe belongs to.
     */
    public ItemNetwork getNetwork() {
        return ItemNetworkManager.getNetworkForPipe(pos);
    }
    
    private boolean needsCooldown() {
        return this.transferCooldown > 0;
    }
    
    // Inventory implementation
    
    @Override
    public int size() {
        return this.inventory.size();
    }

    @Override
    public boolean isEmpty() {
        return this.inventory.get(0).isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        return this.inventory.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        return slot >= 0 && slot < this.inventory.size() && !this.inventory.get(slot).isEmpty() && amount > 0 ? 
            this.inventory.get(slot).split(amount) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot) {
        return slot >= 0 && slot < this.inventory.size() ? 
            this.inventory.set(slot, ItemStack.EMPTY) : ItemStack.EMPTY;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot >= 0 && slot < this.inventory.size()) {
            this.inventory.set(slot, stack);
            
            if (stack.getCount() > this.getMaxCountPerStack()) {
                stack.setCount(this.getMaxCountPerStack());
            }
        }
    }

    @Override
    public boolean canPlayerUse(net.minecraft.entity.player.PlayerEntity player) {
        return false; // Output pipes are not directly accessible to players
    }

    @Override
    public void clear() {
        this.inventory.clear();
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        
        // Save the inventory
        Inventories.writeNbt(nbt, this.inventory, registries);
        
        nbt.putInt("TransferCooldown", this.transferCooldown);
        
        // Save the last input direction if we have one
        if (this.lastInputDirection != null) {
            nbt.putInt("LastInputDir", this.lastInputDirection.ordinal());
        }
        
        // Save the last animation tick
        nbt.putInt("LastAnimationTick", this.lastAnimationTick);
        
        // Save where item was extracted from
        if (this.extractedFrom != null) {
            nbt.putInt("ExtractedFromX", this.extractedFrom.getX());
            nbt.putInt("ExtractedFromY", this.extractedFrom.getY());
            nbt.putInt("ExtractedFromZ", this.extractedFrom.getZ());
        }
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        
        // Load the inventory
        Inventories.readNbt(nbt, this.inventory, registries);
        
        this.transferCooldown = nbt.getInt("TransferCooldown", -1);
        
        // Load the last input direction if saved
        if (nbt.contains("LastInputDir")) {
            int dirOrdinal = nbt.getInt("LastInputDir", 0);
            this.lastInputDirection = Direction.values()[dirOrdinal];
        } else {
            this.lastInputDirection = null;
        }
        
        // Load the last animation tick
        this.lastAnimationTick = nbt.getInt("LastAnimationTick", -1);
        
        // Load where item was extracted from
        if (nbt.contains("ExtractedFromX") && nbt.contains("ExtractedFromY") && nbt.contains("ExtractedFromZ")) {
            int x = nbt.getInt("ExtractedFromX", 0);
            int y = nbt.getInt("ExtractedFromY", 0);
            int z = nbt.getInt("ExtractedFromZ", 0);
            this.extractedFrom = new BlockPos(x, y, z);
        } else {
            this.extractedFrom = null;
        }
        
        // Flag for network reconnection after loading
        needsNetworkReconnection = true;
    }
    
    // ===== HELPER METHODS =====
    
    /**
     * Get the inventory at a specific position in the world.
     */
    @Nullable
    private static Inventory getInventoryAt(World world, BlockPos pos) {
        BlockState blockState = world.getBlockState(pos);
        net.minecraft.block.Block block = blockState.getBlock();
        
        Circuitmod.LOGGER.info("[OUTPUT-PIPE-GET-INVENTORY] Checking for inventory at {}, block: {}", 
            pos, block.getName().getString());
        
        // Follow the exact pattern from Minecraft hopper code
        Inventory inventory = getBlockInventoryAt(world, pos, blockState);
        if (inventory != null) {
            Circuitmod.LOGGER.info("[OUTPUT-PIPE-GET-INVENTORY] Found inventory: {} with {} slots", 
                inventory.getClass().getSimpleName(), inventory.size());
            
            // Log inventory contents for debugging
            Circuitmod.LOGGER.info("[OUTPUT-PIPE-GET-INVENTORY] Logging contents of first 5 slots:");
            boolean hasItems = false;
            for (int i = 0; i < Math.min(inventory.size(), 5); i++) { // Only log first 5 slots
                ItemStack stack = inventory.getStack(i);
                if (!stack.isEmpty()) {
                    Circuitmod.LOGGER.info("[OUTPUT-PIPE-GET-INVENTORY] Slot {}: {} x{}", 
                        i, stack.getItem().getName().getString(), stack.getCount());
                    hasItems = true;
                } else {
                    Circuitmod.LOGGER.info("[OUTPUT-PIPE-GET-INVENTORY] Slot {}: EMPTY", i);
                }
            }
            if (!hasItems) {
                Circuitmod.LOGGER.info("[OUTPUT-PIPE-GET-INVENTORY] No items found in first 5 slots");
            }
            
            return inventory;
        }
        
        Circuitmod.LOGGER.info("[OUTPUT-PIPE-GET-INVENTORY] No inventory found at {}", pos);
        return null;
    }
    
    /**
     * Get block inventory at position - follows Minecraft hopper pattern exactly
     */
    @Nullable
    private static Inventory getBlockInventoryAt(World world, BlockPos pos, BlockState state) {
        net.minecraft.block.Block block = state.getBlock();
        
        // Check if block implements InventoryProvider interface (like chests, shulker boxes)
        if (block instanceof net.minecraft.block.InventoryProvider) {
            Circuitmod.LOGGER.info("[OUTPUT-PIPE-GET-INVENTORY] Block {} implements InventoryProvider", block.getName().getString());
            return ((net.minecraft.block.InventoryProvider)block).getInventory(state, world, pos);
        } 
        // Check if block has entity AND entity implements Inventory
        else if (state.hasBlockEntity() && world.getBlockEntity(pos) instanceof Inventory inventory) {
            Circuitmod.LOGGER.info("[OUTPUT-PIPE-GET-INVENTORY] BlockEntity: {}", inventory.getClass().getSimpleName());
            
            // Special case: Handle double chests properly (exactly like Minecraft hopper)
            if (inventory instanceof net.minecraft.block.entity.ChestBlockEntity && 
                block instanceof net.minecraft.block.ChestBlock) {
                Circuitmod.LOGGER.info("[OUTPUT-PIPE-GET-INVENTORY] Handling chest block with special logic");
                return net.minecraft.block.ChestBlock.getInventory((net.minecraft.block.ChestBlock)block, state, world, pos, true);
            }
            
            return inventory;
        }
        
        return null;
    }
    
    /**
     * Transfer an item from the pipe to a target inventory.
     */
    private static ItemStack transfer(Inventory from, Inventory to, ItemStack stack, @Nullable Direction side) {
        if (to instanceof SidedInventory sidedInventory && side != null) {
            int[] slots = sidedInventory.getAvailableSlots(side);
            
            for (int i = 0; i < slots.length && !stack.isEmpty(); i++) {
                stack = tryInsertIntoSlot(from, to, stack, slots[i], side);
            }
        } else {
            int size = to.size();
            
            for (int i = 0; i < size && !stack.isEmpty(); i++) {
                stack = tryInsertIntoSlot(from, to, stack, i, side);
            }
        }
        
        return stack;
    }
    
    /**
     * Try to insert an item into a specific slot of an inventory.
     */
    private static ItemStack tryInsertIntoSlot(Inventory from, Inventory to, ItemStack stack, int slot, @Nullable Direction side) {
        ItemStack slotStack = to.getStack(slot);
        
        if (canInsert(to, stack, slot, side)) {
            if (slotStack.isEmpty()) {
                // Slot is empty, insert the whole stack
                to.setStack(slot, stack);
                return ItemStack.EMPTY;
            } else if (canMergeItems(slotStack, stack)) {
                // Items can be merged
                int maxCount = Math.min(slotStack.getMaxCount(), to.getMaxCountPerStack());
                int spaceLeft = maxCount - slotStack.getCount();
                
                if (spaceLeft > 0) {
                    int transferAmount = Math.min(spaceLeft, stack.getCount());
                    slotStack.increment(transferAmount);
                    stack.decrement(transferAmount);
                    to.markDirty();
                }
            }
        }
        
        return stack;
    }
    
    /**
     * Check if an item can be inserted into a specific slot of an inventory.
     */
    private static boolean canInsert(Inventory inventory, ItemStack stack, int slot, @Nullable Direction side) {
        if (!inventory.isValid(slot, stack)) {
            return false;
        }
        
        if (inventory instanceof SidedInventory sidedInventory) {
            return sidedInventory.canInsert(slot, stack, side);
        }
        
        return true;
    }
    
    /**
     * Check if an item can be extracted from a specific slot of an inventory.
     */
    private static boolean canExtract(Inventory inventory, ItemStack stack, int slot, Direction side) {
        Circuitmod.LOGGER.info("[OUTPUT-PIPE-CAN-EXTRACT] Checking extraction for slot {} from {} in direction {}", 
            slot, inventory.getClass().getSimpleName(), side);
        
        if (inventory instanceof SidedInventory sidedInventory) {
            boolean canExtract = sidedInventory.canExtract(slot, stack, side);
            Circuitmod.LOGGER.info("[OUTPUT-PIPE-CAN-EXTRACT] SidedInventory.canExtract returned: {}", canExtract);
            return canExtract;
        }
        
        Circuitmod.LOGGER.info("[OUTPUT-PIPE-CAN-EXTRACT] Not a SidedInventory, allowing extraction");
        return true;
    }
    
    /**
     * Check if two items can be merged.
     */
    private static boolean canMergeItems(ItemStack first, ItemStack second) {
        return first.getCount() < first.getMaxCount() && 
               ItemStack.areItemsEqual(first, second);
    }
    
    /**
     * Find the best slot containing a full stack that can be extracted.
     */
    private static int findFullStackSlot(Inventory inventory, Direction extractDirection) {
        Circuitmod.LOGGER.info("[OUTPUT-PIPE-FIND-FULL] Scanning {} slots for full stacks", inventory.size());
        
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            
            if (!stack.isEmpty()) {
                Circuitmod.LOGGER.info("[OUTPUT-PIPE-FIND-FULL] Slot {}: {} x{} (max: {})", 
                    i, stack.getItem().getName().getString(), stack.getCount(), stack.getMaxCount());
                
                if (stack.getCount() == stack.getMaxCount()) {
                    boolean canExtract = canExtract(inventory, stack, i, extractDirection);
                    Circuitmod.LOGGER.info("[OUTPUT-PIPE-FIND-FULL] Slot {} is full stack, canExtract: {}", i, canExtract);
                    
                    if (canExtract) {
                        Circuitmod.LOGGER.info("[OUTPUT-PIPE-FIND-FULL] Found full stack at slot {}", i);
                        return i;
                    }
                }
            }
        }
        
        Circuitmod.LOGGER.info("[OUTPUT-PIPE-FIND-FULL] No full stack found");
        return -1; // No full stack found
    }
    
    /**
     * Find the slot with the largest stack that can be extracted.
     */
    private static int findLargestStackSlot(Inventory inventory, Direction extractDirection) {
        Circuitmod.LOGGER.info("[OUTPUT-PIPE-FIND-LARGEST] Scanning {} slots for largest stacks", inventory.size());
        
        int bestSlot = -1;
        int largestCount = 0;
        
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            
            if (!stack.isEmpty()) {
                Circuitmod.LOGGER.info("[OUTPUT-PIPE-FIND-LARGEST] Slot {}: {} x{}", 
                    i, stack.getItem().getName().getString(), stack.getCount());
                
                if (stack.getCount() > largestCount) {
                    boolean canExtract = canExtract(inventory, stack, i, extractDirection);
                    Circuitmod.LOGGER.info("[OUTPUT-PIPE-FIND-LARGEST] Slot {} has larger stack ({} > {}), canExtract: {}", 
                        i, stack.getCount(), largestCount, canExtract);
                    
                    if (canExtract) {
                        bestSlot = i;
                        largestCount = stack.getCount();
                        Circuitmod.LOGGER.info("[OUTPUT-PIPE-FIND-LARGEST] New best slot: {} with count {}", i, largestCount);
                    }
                }
            }
        }
        
        Circuitmod.LOGGER.info("[OUTPUT-PIPE-FIND-LARGEST] Best slot found: {} with count {}", bestSlot, largestCount);
        return bestSlot;
    }
    
} 