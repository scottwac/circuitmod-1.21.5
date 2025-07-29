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
        final boolean DEBUG_LOGGING = true;
        
        // Check if we're in a network, if not try to reconnect
        ItemNetwork currentNetwork = ItemNetworkManager.getNetworkForPipe(pos);
        if (currentNetwork == null) {
            // Only log if debug logging is enabled
            if (DEBUG_LOGGING && world.getTime() % 100 == 0) {
                Circuitmod.LOGGER.info("[OUTPUT-PIPE-TICK] Pipe at {} not in network, attempting to reconnect", pos);
            }
            ItemNetworkManager.connectPipe(world, pos);
            currentNetwork = ItemNetworkManager.getNetworkForPipe(pos);
            if (currentNetwork != null && DEBUG_LOGGING && world.getTime() % 100 == 0) {
                Circuitmod.LOGGER.info("[OUTPUT-PIPE-TICK] Successfully reconnected to network {}", currentNetwork.getNetworkId());
            }
        }
        
        // Handle network reconnection after world load
        if (blockEntity.needsNetworkReconnection) {
            // Only log if debug logging is enabled
            if (DEBUG_LOGGING && world.getTime() % 100 == 0) {
                Circuitmod.LOGGER.info("[OUTPUT-PIPE-RECONNECT] Reconnecting pipe at {} to network after world load", pos);
            }
            ItemNetworkManager.connectPipe(world, pos);
            blockEntity.needsNetworkReconnection = false;
        }
        
        // Check for new unconnected inventories every 20 ticks for better performance
        if (world.getTime() % 20 == 0) {
            checkForUnconnectedInventories(world, pos, blockEntity);
        }
        
        if (!blockEntity.needsCooldown()) {
            boolean didWork = false;
            
            // If we have an item, use simplified network routing to find destination
            if (!blockEntity.isEmpty()) {
                ItemStack item = blockEntity.getStack(0);
                
                // Get the network for this pipe
                ItemNetwork network = ItemNetworkManager.getNetworkForPipe(pos);
                if (network != null) {
                    // Only log if debug logging is enabled
                    if (DEBUG_LOGGING && world.getTime() % 100 == 0) {
                        Circuitmod.LOGGER.info("[OUTPUT-PIPE-ROUTING] Pipe at {} routing item with extractedFrom: {}", pos, blockEntity.extractedFrom);
                    }
                    
                    // Find a destination for this item, excluding where we extracted it from
                    BlockPos destination = network.findDestinationForItem(item, blockEntity.extractedFrom);
                    
                    if (destination != null) {
                        // Try to transfer directly to the destination
                        if (transferToInventoryAt(world, destination, blockEntity)) {
                            didWork = true;
                        }
                    } else {
                        // No destination found, try adjacent transfer as fallback
                        if (transferToAdjacentInventory(world, pos, blockEntity)) {
                            didWork = true;
                        }
                    }
                } else {
                    // No network, try direct adjacent transfer as fallback
                    // Only log if debug logging is enabled
                    if (DEBUG_LOGGING && world.getTime() % 100 == 0) {
                        Circuitmod.LOGGER.info("[OUTPUT-PIPE-TRANSFER] No network found, trying adjacent transfer");
                    }
                    if (transferToAdjacentInventory(world, pos, blockEntity)) {
                        didWork = true;
                    }
                }
            }
            
            if (didWork) {
                blockEntity.setTransferCooldown(COOLDOWN_TICKS);
            }
        }
    }
    
    /**
     * Routes an item through the network to its destination.
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

        // Find a destination for this item, excluding where we extracted it from
        BlockPos destination = network.findDestinationForItem(currentStack, blockEntity.extractedFrom);
        if (destination == null) {
            Circuitmod.LOGGER.info("[OUTPUT-PIPE-ROUTE] No destination found for {} from {} (excluding {})", 
                currentStack.getItem().getName().getString(), pos, blockEntity.extractedFrom);
            return false;
        }

        Circuitmod.LOGGER.info("[OUTPUT-PIPE-ROUTE] Found destination {} for {}", 
            destination, currentStack.getItem().getName().getString());

        // Try to transfer directly to the destination
        return transferToInventoryAt(world, destination, blockEntity);
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
            // network.forceRescanAllInventories(); // This method no longer exists
        }
    }
    
    /**
     * Checks if two positions are adjacent (1 block apart).
     */
    private static boolean isAdjacent(BlockPos pos1, BlockPos pos2) {
        return Math.abs(pos1.getX() - pos2.getX()) + Math.abs(pos1.getY() - pos2.getY()) + Math.abs(pos1.getZ() - pos2.getZ()) == 1;
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
     * Transfers an item to an inventory at the specified position.
     */
    private static boolean transferToInventoryAt(World world, BlockPos inventoryPos, OutputPipeBlockEntity blockEntity) {
        Inventory inventory = getInventoryAt(world, inventoryPos);
        if (inventory == null) {
            return false;
        }
        
        ItemStack currentStack = blockEntity.getStack(0);
        if (currentStack.isEmpty()) {
            return false;
        }
        
        // Check if inventory has space
        if (!hasSpaceForItem(inventory, currentStack)) {
            Circuitmod.LOGGER.info("[OUTPUT-PIPE-INVENTORY-TRANSFER] Inventory full at {}, cannot transfer {}",
                inventoryPos, currentStack.getItem().getName().getString());
            return false;
        }
        
        Circuitmod.LOGGER.info("[OUTPUT-PIPE-INVENTORY-TRANSFER] Transferring {} x{} to {} at {}", 
            currentStack.getItem().getName().getString(), currentStack.getCount(),
            inventory.getClass().getSimpleName(), inventoryPos);
        
        Direction transferDirection = getDirectionTowards(blockEntity.getPos(), inventoryPos);
        
        // Try to insert the FULL stack
        ItemStack stackToInsert = currentStack.copy();
        ItemStack remaining = transfer(blockEntity, inventory, stackToInsert, transferDirection);
        
        Circuitmod.LOGGER.info("[OUTPUT-PIPE-INVENTORY-TRANSFER] Transfer result: {} remaining from {} original", 
            remaining.getCount(), currentStack.getCount());
        
        if (remaining.isEmpty() || remaining.getCount() < currentStack.getCount()) {
            // Successfully transferred at least part of the stack
            blockEntity.setStack(0, remaining);
            blockEntity.markDirty();
            
            // Send animation for item leaving the pipe
            if (world instanceof ServerWorld serverWorld) {
                blockEntity.sendAnimationIfAllowed(serverWorld, currentStack, blockEntity.getPos(), inventoryPos);
            }
            
            Circuitmod.LOGGER.info("[OUTPUT-PIPE-INVENTORY-TRANSFER] Successfully transferred {} items to {}", 
                currentStack.getCount() - remaining.getCount(), inventoryPos);
            return true;
        }
        
        Circuitmod.LOGGER.info("[OUTPUT-PIPE-INVENTORY-TRANSFER] Failed to transfer any items to {} - inventory appears to be full", inventoryPos);
        return false;
    }
    
    /**
     * Transfers an item to an adjacent inventory.
     */
    private static boolean transferToAdjacentInventory(World world, BlockPos pipePos, OutputPipeBlockEntity blockEntity) {
        ItemStack currentStack = blockEntity.getStack(0);
        if (currentStack.isEmpty()) {
            return false;
        }
        
        // Try to transfer to any adjacent inventory
        for (Direction direction : Direction.values()) {
            BlockPos adjacentPos = pipePos.offset(direction);
            Inventory inventory = getInventoryAt(world, adjacentPos);
            
            if (inventory != null && hasSpaceForItem(inventory, currentStack)) {
                // Try to transfer
                ItemStack stackToInsert = currentStack.copy();
                ItemStack remaining = transfer(blockEntity, inventory, stackToInsert, direction);
                
                if (remaining.isEmpty() || remaining.getCount() < currentStack.getCount()) {
                    // Successfully transferred at least part of the stack
                    blockEntity.setStack(0, remaining);
                    blockEntity.markDirty();
                    
                    // Send animation
                    if (world instanceof ServerWorld serverWorld) {
                        blockEntity.sendAnimationIfAllowed(serverWorld, currentStack, pipePos, adjacentPos);
                    }
                    
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Check if an inventory has space for a specific item stack.
     */
    private static boolean hasSpaceForItem(Inventory inventory, ItemStack itemStack) {
        // Check each slot to see if we can insert at least one item
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack slotStack = inventory.getStack(i);
            
            // Empty slot = space available
            if (slotStack.isEmpty()) {
                return true;
            }
            
            // Same item type and not at max capacity = space available
            if (ItemStack.areItemsEqual(slotStack, itemStack)) {
                int maxCount = Math.min(slotStack.getMaxCount(), inventory.getMaxCountPerStack());
                if (slotStack.getCount() < maxCount) {
                    return true;
                }
            }
        }
        
        // No space found in any slot
        return false;
    }
    
    /**
     * Gets the direction from one position to another.
     */
    private static Direction getDirectionTowards(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();
        
        // Find the largest difference to determine primary direction
        int maxDiff = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz));
        
        if (maxDiff == Math.abs(dx)) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        } else if (maxDiff == Math.abs(dy)) {
            return dy > 0 ? Direction.UP : Direction.DOWN;
        } else {
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }
    
    /**
     * Transfers items between inventories.
     */
    private static ItemStack transfer(Inventory from, Inventory to, ItemStack stack, @Nullable Direction side) {
        if (stack.isEmpty()) {
            return stack;
        }
        
        // Try to insert into each slot
        for (int slot = 0; slot < to.size(); slot++) {
            if (canInsert(to, stack, slot, side)) {
                ItemStack slotStack = to.getStack(slot);
                
                if (slotStack.isEmpty()) {
                    // Empty slot - insert the stack
                    to.setStack(slot, stack.copy());
                    return ItemStack.EMPTY;
                } else if (ItemStack.areItemsEqual(slotStack, stack)) {
                    // Same item type - try to merge
                    int maxCount = Math.min(slotStack.getMaxCount(), to.getMaxCountPerStack());
                    int spaceAvailable = maxCount - slotStack.getCount();
                    
                    if (spaceAvailable > 0) {
                        int toTransfer = Math.min(spaceAvailable, stack.getCount());
                        slotStack.increment(toTransfer);
                        stack.decrement(toTransfer);
                        
                        if (stack.isEmpty()) {
                            return ItemStack.EMPTY;
                        }
                    }
                }
            }
        }
        
        return stack;
    }
    
    /**
     * Checks if an inventory can accept an item in a specific slot.
     */
    private static boolean canInsert(Inventory inventory, ItemStack stack, int slot, @Nullable Direction side) {
        if (inventory instanceof net.minecraft.inventory.SidedInventory sidedInventory) {
            return sidedInventory.canInsert(slot, stack, side);
        }
        return true;
    }
    
    /**
     * Gets an inventory at the specified position.
     */
    @Nullable
    private static Inventory getInventoryAt(World world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof Inventory) {
            return (Inventory) blockEntity;
        }
        return null;
    }
    
    /**
     * Extracts items from connected inventories.
     */
    private static boolean extractFromConnectedInventories(World world, BlockPos pos, BlockState state, OutputPipeBlockEntity blockEntity) {
        // This method is no longer needed with simplified network logic
        // Items are now routed directly through the network
        return false;
    }
    
    /**
     * Send animation for item transfer.
     */
    private void sendAnimationIfAllowed(ServerWorld world, ItemStack stack, BlockPos from, BlockPos to) {
        // Animation logic would go here
    }
    
    public void setTransferCooldown(int cooldown) {
        this.transferCooldown = cooldown;
    }
    
    public int getTransferCooldown() {
        return this.transferCooldown;
    }
    
    public ItemNetwork getNetwork() {
        return ItemNetworkManager.getNetworkForPipe(pos);
    }
    
    private boolean needsCooldown() {
        return this.transferCooldown > 0;
    }
    
    // Inventory implementation
    @Override
    public int size() {
        return inventory.size();
    }

    @Override
    public boolean isEmpty() {
        return inventory.isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        return inventory.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        return Inventories.splitStack(inventory, slot, amount);
    }

    @Override
    public ItemStack removeStack(int slot) {
        return Inventories.removeStack(inventory, slot);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        inventory.set(slot, stack);
        if (stack.getCount() > getMaxCountPerStack()) {
            stack.setCount(getMaxCountPerStack());
        }
    }

    @Override
    public boolean canPlayerUse(net.minecraft.entity.player.PlayerEntity player) {
        if (world.getBlockEntity(pos) != this) {
            return false;
        }
        return player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clear() {
        inventory.clear();
    }
    
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putInt("transfer_cooldown", this.transferCooldown);
        nbt.putLong("last_tick_time", this.lastTickTime);
        nbt.putInt("last_animation_tick", this.lastAnimationTick);
        
        if (this.lastInputDirection != null) {
            nbt.putInt("last_input_direction", this.lastInputDirection.ordinal());
        }
        
        if (this.extractedFrom != null) {
            nbt.putInt("extracted_from_x", this.extractedFrom.getX());
            nbt.putInt("extracted_from_y", this.extractedFrom.getY());
            nbt.putInt("extracted_from_z", this.extractedFrom.getZ());
        }
        
        Inventories.writeNbt(nbt, this.inventory, registries);
    }
    
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        this.transferCooldown = nbt.getInt("transfer_cooldown").orElse(-1);
        this.lastTickTime = nbt.getLong("last_tick_time").orElse(0L);
        this.lastAnimationTick = nbt.getInt("last_animation_tick").orElse(-1);
        
        if (nbt.contains("last_input_direction")) {
            int dirOrdinal = nbt.getInt("last_input_direction").orElse(0);
            this.lastInputDirection = Direction.values()[dirOrdinal];
        }
        
        if (nbt.contains("extracted_from_x") && nbt.contains("extracted_from_y") && nbt.contains("extracted_from_z")) {
            int x = nbt.getInt("extracted_from_x").orElse(0);
            int y = nbt.getInt("extracted_from_y").orElse(0);
            int z = nbt.getInt("extracted_from_z").orElse(0);
            this.extractedFrom = new BlockPos(x, y, z);
        }
        
        Inventories.readNbt(nbt, this.inventory, registries);
        this.needsNetworkReconnection = true;
    }
} 