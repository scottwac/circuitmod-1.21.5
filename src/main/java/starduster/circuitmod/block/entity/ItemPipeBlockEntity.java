package starduster.circuitmod.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.world.World;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.ItemPipeBlock;
import starduster.circuitmod.item.network.ItemNetwork;
import starduster.circuitmod.item.network.ItemNetworkManager;
import starduster.circuitmod.item.network.ItemRoute;
import starduster.circuitmod.network.PipeNetworkAnimator;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Block entity for item pipes, which can transfer items between inventories
 * using the network-based routing system.
 */
public class ItemPipeBlockEntity extends BlockEntity implements Inventory {
    private static final int INVENTORY_SIZE = 1; 
    private static final int COOLDOWN_TICKS = 1; // Faster throughput for better item flow
    public static final int ANIMATION_TICKS = 5;
    
    private DefaultedList<ItemStack> inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);
    int transferCooldown = -1; // Package-private for access from other pipe classes
    private long lastTickTime;
    Direction lastInputDirection; // Package-private for access from other pipe classes
    @Nullable BlockPos sourceExclusion = null; // Package-private - Track where this item came from
    private int lastAnimationTick = -1; // Track when we last sent an animation to prevent duplicates
    
    // Network reconnection flag for world load
    private boolean needsNetworkReconnection = false;
    
    public ItemPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ITEM_PIPE, pos, state);
    }
    
    /**
     * Called when this pipe is added to the world - connects to network.
     */
    public void onPlaced() {
        if (world != null && !world.isClient()) {
            ItemNetworkManager.connectPipe(world, pos);
        }
    }
    
    /**
     * Called when this pipe is removed from the world - disconnects from network.
     */
    public void onRemoved() {
        if (world != null && !world.isClient()) {
            ItemNetworkManager.disconnectPipe(world, pos);
        }
    }
    
    /**
     * Gets the current network this pipe belongs to.
     */
    public ItemNetwork getNetwork() {
        return ItemNetworkManager.getNetworkForPipe(pos);
    }

    /**
     * Called every tick to update the pipe's state using network-based routing.
     */
    public static void tick(World world, BlockPos pos, BlockState state, ItemPipeBlockEntity blockEntity) {
        if (world.isClient) {
            return;
        }
        
        // Debug logging control - set to true only when debugging
        final boolean DEBUG_LOGGING = true;
        
        // Check if we're in a network, if not try to reconnect
        ItemNetwork currentNetwork = ItemNetworkManager.getNetworkForPipe(pos);
        if (currentNetwork == null) {
            // Only log if debug logging is enabled
            if (DEBUG_LOGGING && world.getTime() % 100 == 0) {
                Circuitmod.LOGGER.info("[PIPE-TICK] Pipe at {} not in network, attempting to reconnect", pos);
            }
            ItemNetworkManager.connectPipe(world, pos);
            currentNetwork = ItemNetworkManager.getNetworkForPipe(pos);
            if (currentNetwork != null && DEBUG_LOGGING && world.getTime() % 100 == 0) {
                Circuitmod.LOGGER.info("[PIPE-TICK] Successfully reconnected to network {}", currentNetwork.getNetworkId());
            }
        }
        
        // Handle network reconnection after world load
        if (blockEntity.needsNetworkReconnection) {
            // Only log if debug logging is enabled
            if (DEBUG_LOGGING && world.getTime() % 100 == 0) {
                Circuitmod.LOGGER.info("[PIPE-RECONNECT] Reconnecting pipe at {} to network after world load", pos);
            }
            ItemNetworkManager.connectPipe(world, pos);
            blockEntity.needsNetworkReconnection = false;
        }
        
        // Check for new unconnected inventories every 20 ticks (reduced from 5) for better performance
        if (world.getTime() % 20 == 0) {
            checkForUnconnectedInventories(world, pos, blockEntity);
        }
        
        // Monitor inventory changes for closer destinations gaining space - reduced frequency
        if (world.getTime() % 40 == 0) {
            // Only log if debug logging is enabled
            if (DEBUG_LOGGING && world.getTime() % 200 == 0) {
                Circuitmod.LOGGER.info("[PIPE-MONITOR] Monitoring inventory changes at {}", pos);
            }
        }
        
        if (!blockEntity.needsCooldown()) {
            boolean didWork = false;
            
            // Clear any Air items that might be in the pipe
            ItemStack currentItem = blockEntity.getStack(0);
            if (currentItem.getItem() == net.minecraft.item.Items.AIR) {
                Circuitmod.LOGGER.info("[PIPE-CLEANUP] Clearing Air item from pipe at {}", pos);
                blockEntity.setStack(0, ItemStack.EMPTY);
                currentItem = ItemStack.EMPTY;
            }
            
            // If we have an item, use simplified network routing to find destination
            if (!blockEntity.isEmpty()) {
                ItemStack item = blockEntity.getStack(0);
                
                // Debug logging to see what item we have
                Circuitmod.LOGGER.info("[PIPE-ITEM] Pipe at {} has item: {} x{} (sourceExclusion: {})", 
                    pos, item.getItem().getName().getString(), item.getCount(), blockEntity.sourceExclusion);
                
                // Get the network for this pipe
                ItemNetwork network = ItemNetworkManager.getNetworkForPipe(pos);
                if (network != null) {
                    // Only log if debug logging is enabled
                    if (DEBUG_LOGGING && world.getTime() % 100 == 0) {
                        Circuitmod.LOGGER.info("[PIPE-ROUTING] Pipe at {} routing item with sourceExclusion: {}", pos, blockEntity.sourceExclusion);
                    }
                    
                    // Find a destination for this item
                    BlockPos destination = network.findDestinationForItem(item, blockEntity.sourceExclusion);
                    
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
                        Circuitmod.LOGGER.info("[PIPE-TRANSFER] No network found, trying adjacent transfer");
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
    private static boolean routeItemThroughNetwork(World world, BlockPos pipePos, ItemPipeBlockEntity blockEntity) {
        ItemNetwork network = blockEntity.getNetwork();
        if (network == null) {
            // No network, try direct adjacent transfer as fallback
            return transferToAdjacentInventory(world, pipePos, blockEntity);
        }
        
        ItemStack currentItem = blockEntity.getStack(0);
        if (currentItem.isEmpty()) {
            return false;
        }
        
        // Find a destination for this item, excluding the source if we have one
        BlockPos destination;
        if (blockEntity.sourceExclusion != null) {
            Circuitmod.LOGGER.info("[PIPE-ROUTING] Pipe at {} routing item with sourceExclusion: {}", pipePos, blockEntity.sourceExclusion);
            destination = network.findDestinationForItem(currentItem, blockEntity.sourceExclusion);
        } else {
            destination = network.findDestinationForItem(currentItem, null);
        }
        
        if (destination == null) {
            // No destination found, try direct adjacent transfer as fallback
            return transferToAdjacentInventory(world, pipePos, blockEntity);
        }
        
        // Try to transfer directly to the destination
        return transferToInventoryAt(world, destination, blockEntity);
    }
    
    /**
     * Transfers an item to an inventory at the specified position.
     */
    private static boolean transferToInventoryAt(World world, BlockPos inventoryPos, ItemPipeBlockEntity blockEntity) {
        Inventory inventory = getInventoryAt(world, inventoryPos);
        if (inventory == null) {
            Circuitmod.LOGGER.info("[ITEM-PIPE-TRANSFER] No inventory found at {}", inventoryPos);
            return false;
        }
        
        ItemStack currentStack = blockEntity.getStack(0);
        if (currentStack.isEmpty()) {
            Circuitmod.LOGGER.info("[ITEM-PIPE-TRANSFER] No item to transfer");
            return false;
        }
        
        Circuitmod.LOGGER.info("[ITEM-PIPE-TRANSFER] Attempting to transfer {} to inventory at {}", 
            currentStack.getItem().getName().getString(), inventoryPos);
        
        // Check if inventory has space
        if (!hasSpaceForItem(inventory, currentStack)) {
            Circuitmod.LOGGER.info("[ITEM-PIPE-TRANSFER] Inventory at {} has no space for {}", 
                inventoryPos, currentStack.getItem().getName().getString());
            return false;
        }
        
        // Try to transfer
        ItemStack stackToInsert = currentStack.copy();
        ItemStack remaining = transfer(blockEntity, inventory, stackToInsert, Direction.NORTH);
        
        if (remaining.isEmpty() || remaining.getCount() < currentStack.getCount()) {
            // Successfully transferred at least part of the stack
            blockEntity.setStack(0, remaining);
            blockEntity.markDirty();
            
            Circuitmod.LOGGER.info("[ITEM-PIPE-TRANSFER] Successfully transferred {} to inventory at {}", 
                currentStack.getItem().getName().getString(), inventoryPos);
            
            // Send animation
            if (world instanceof ServerWorld serverWorld) {
                blockEntity.sendAnimationIfAllowed(serverWorld, currentStack, blockEntity.getPos(), inventoryPos);
            }
            
            return true;
        } else {
            Circuitmod.LOGGER.info("[ITEM-PIPE-TRANSFER] Failed to transfer {} to inventory at {}", 
                currentStack.getItem().getName().getString(), inventoryPos);
            return false;
        }
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
     * Transfers item to a pipe at the specified position.
     */
    private static boolean transferToPipeAt(World world, BlockPos targetPipePos, ItemPipeBlockEntity blockEntity) {
        BlockEntity targetEntity = world.getBlockEntity(targetPipePos);
        
        // Handle ItemPipeBlockEntity
        if (targetEntity instanceof ItemPipeBlockEntity targetPipe) {
            if (!targetPipe.isEmpty()) {
                return false; // Target pipe is full
            }
            
            ItemStack currentStack = blockEntity.getStack(0);
            Direction transferDirection = getDirectionTowards(blockEntity.getPos(), targetPipePos);
            
            // Transfer the item
            ItemStack transferredItem = blockEntity.removeStack(0);
            targetPipe.setStack(0, transferredItem);
            
            // CRITICAL: Set sourceExclusion to current position to prevent infinite loops
            targetPipe.sourceExclusion = blockEntity.getPos();
            
            // Set animation and direction info
            targetPipe.lastInputDirection = transferDirection;
            targetPipe.transferCooldown = COOLDOWN_TICKS;
            targetPipe.markDirty();
            
            // Clear source pipe
            blockEntity.setStack(0, ItemStack.EMPTY);
            blockEntity.markDirty();
            
            // Send animation
            if (world instanceof ServerWorld serverWorld) {
                blockEntity.sendAnimationIfAllowed(serverWorld, currentStack, blockEntity.getPos(), targetPipePos);
            }
            
            return true;
        }
        // Handle SortingPipeBlockEntity  
        else if (targetEntity instanceof SortingPipeBlockEntity targetSortingPipe) {
            if (!targetSortingPipe.isEmpty()) {
                return false; // Target pipe is full
            }
            
            ItemStack currentStack = blockEntity.getStack(0);
            Direction transferDirection = getDirectionTowards(blockEntity.getPos(), targetPipePos);
            
            // Transfer the item
            ItemStack transferredItem = blockEntity.removeStack(0);
            targetSortingPipe.setStack(0, transferredItem);
            
            // sourceExclusion removed - network routing handles loop prevention properly
            
            // Set animation and direction info
            targetSortingPipe.setLastInputDirection(transferDirection);
            targetSortingPipe.setTransferCooldown(COOLDOWN_TICKS);
            targetSortingPipe.markDirty();
            
            // Clear source pipe
            blockEntity.setStack(0, ItemStack.EMPTY);
            blockEntity.markDirty();
            
            // Send animation
            if (world instanceof ServerWorld serverWorld) {
                blockEntity.sendAnimationIfAllowed(serverWorld, currentStack, blockEntity.getPos(), targetPipePos);
            }
            
            Circuitmod.LOGGER.info("[PIPE-TRANSFER-TO-SORTING] Successfully transferred {} to sorting pipe at {}", 
                transferredItem.getItem().getName().getString(), targetPipePos);
            
            return true;
        }
        
        return false;
    }
    
    /**
     * Fallback method to transfer to any adjacent inventory.
     */
    private static boolean transferToAdjacentInventory(World world, BlockPos pipePos, ItemPipeBlockEntity blockEntity) {
        ItemStack currentStack = blockEntity.getStack(0);
        
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pipePos.offset(direction);
            Inventory neighborInventory = getInventoryAt(world, neighborPos);
            
            if (neighborInventory != null && !(world.getBlockEntity(neighborPos) instanceof ItemPipeBlockEntity)) {
                ItemStack remaining = transfer(blockEntity, neighborInventory, currentStack.copy(), direction.getOpposite());
                
                if (remaining.isEmpty() || remaining.getCount() < currentStack.getCount()) {
                    // Successfully transferred at least part of the stack
                    blockEntity.setStack(0, remaining);
                    blockEntity.markDirty();
                    
                    // Send animation for item leaving the pipe
                    if (world instanceof ServerWorld serverWorld) {
                        blockEntity.sendAnimationIfAllowed(serverWorld, currentStack, pipePos, neighborPos);
                    }
                    
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Gets the direction from one position to another.
     */
    private static Direction getDirectionTowards(BlockPos from, BlockPos to) {
        BlockPos diff = to.subtract(from);
        
        if (diff.getX() > 0) return Direction.EAST;
        if (diff.getX() < 0) return Direction.WEST;
        if (diff.getY() > 0) return Direction.UP;
        if (diff.getY() < 0) return Direction.DOWN;
        if (diff.getZ() > 0) return Direction.SOUTH;
        if (diff.getZ() < 0) return Direction.NORTH;
        
        return Direction.UP; // Default fallback
    }

    /**
     * Extract an item from the inventory above this pipe.
     */
    private static boolean extractFromAbove(World world, BlockPos pos, ItemPipeBlockEntity blockEntity) {
        if (!blockEntity.isEmpty()) {
            return false; // Don't extract if we're already holding an item
        }
        
        // Get the inventory above us
        BlockPos abovePos = pos.up();
        BlockState aboveState = world.getBlockState(abovePos);
        
        // Don't extract from other pipes
        if (aboveState.getBlock() instanceof ItemPipeBlock) {
            return false;
        }
        
        // Check if there's an inventory to extract from
        Inventory aboveInventory = getInventoryAt(world, abovePos);
        if (aboveInventory != null) {
            Direction extractDirection = Direction.DOWN;
            
            // First, try to find a full stack to extract
            int fullStackSlot = findFullStackSlot(aboveInventory, extractDirection);
            if (fullStackSlot >= 0) {
                if (extract(blockEntity, aboveInventory, fullStackSlot, extractDirection)) {
                    // Set input direction to UP, but allow immediate downward flow
                    blockEntity.lastInputDirection = Direction.UP;
                    // CRITICAL: Set sourceExclusion to prevent item from going back to the inventory
                    blockEntity.sourceExclusion = abovePos;
                    blockEntity.markDirty();
                    
                    // Send animation for item entering the pipe
                    if (world instanceof ServerWorld serverWorld) {
                        BlockPos animationFromPos = pos.up();
                        ItemStack extractedStack = blockEntity.getStack(0);
                        blockEntity.sendAnimationIfAllowed(serverWorld, extractedStack, animationFromPos, pos);
                    }
                    
                    Circuitmod.LOGGER.info("[PIPE-EXTRACT] Extracted full stack from slot " + fullStackSlot + " at " + pos);
                    return true;
                }
            }
            
            // If no full stack found, try to find the largest available stack
            int largestStackSlot = findLargestStackSlot(aboveInventory, extractDirection);
            if (largestStackSlot >= 0) {
                if (extract(blockEntity, aboveInventory, largestStackSlot, extractDirection)) {
                    // Set input direction to UP, but allow immediate downward flow
                    blockEntity.lastInputDirection = Direction.UP;
                    // CRITICAL: Set sourceExclusion to prevent item from going back to the inventory
                    blockEntity.sourceExclusion = abovePos;
                    blockEntity.markDirty();
                    
                    // Send animation for item entering the pipe
                    if (world instanceof ServerWorld serverWorld) {
                        BlockPos animationFromPos = pos.up();
                        ItemStack extractedStack = blockEntity.getStack(0);
                        blockEntity.sendAnimationIfAllowed(serverWorld, extractedStack, animationFromPos, pos);
                    }
                    
                    Circuitmod.LOGGER.info("[PIPE-EXTRACT] Extracted largest stack from slot " + largestStackSlot + " at " + pos);
                    return true;
                }
            }
        }
        
        // Check for items on top of the pipe
        if (!blockEntity.isEmpty()) {
            return false;
        }
        
        Box box = new Box(pos.getX(), pos.getY() + 1, pos.getZ(), 
                          pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1);
        for (ItemEntity itemEntity : world.getEntitiesByClass(ItemEntity.class, box, EntityPredicates.VALID_ENTITY)) {
            if (extract(blockEntity, itemEntity)) {
                blockEntity.lastInputDirection = Direction.UP;
                // For ItemEntities, no specific source exclusion needed
                blockEntity.sourceExclusion = null;
                blockEntity.markDirty();
                
                // Send animation for item entering the pipe
                if (world instanceof ServerWorld serverWorld) {
                    BlockPos animationFromPos = pos.up();
                    ItemStack extractedStack = blockEntity.getStack(0);
                    blockEntity.sendAnimationIfAllowed(serverWorld, extractedStack, animationFromPos, pos);
                }
                
                Circuitmod.LOGGER.info("[PIPE-EXTRACT] Extracted item from entity at " + pos);
                return true;
            }
        }
        
        return false;
    }
    

    
    /**
     * Get the BooleanProperty for a direction.
     */
    private static BooleanProperty getDirectionProperty(Direction direction) {
        return switch (direction) {
            case NORTH -> ItemPipeBlock.NORTH;
            case EAST -> ItemPipeBlock.EAST;
            case SOUTH -> ItemPipeBlock.SOUTH;
            case WEST -> ItemPipeBlock.WEST;
            case UP -> ItemPipeBlock.UP;
            case DOWN -> ItemPipeBlock.DOWN;
        };
    }
    
    /**
     * Find the best slot containing a full stack that can be extracted.
     * Prioritizes stacks with higher max stack sizes (more efficient transport).
     * Returns the slot index if found, -1 if no full stack is available.
     */
    private static int findFullStackSlot(Inventory inventory, Direction side) {
        int bestSlot = -1;
        int bestMaxStackSize = 0;
        
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && 
                stack.getCount() >= stack.getMaxCount() && 
                canExtract(inventory, stack, i, side)) {
                
                // Prioritize items with higher max stack sizes for more efficient transport
                int maxStackSize = stack.getMaxCount();
                if (maxStackSize > bestMaxStackSize) {
                    bestSlot = i;
                    bestMaxStackSize = maxStackSize;
                }
            }
        }
        
        return bestSlot;
    }
    
    /**
     * Find the slot containing the largest stack that can be extracted.
     * Returns the slot index if found, -1 if no stack is available.
     */
    private static int findLargestStackSlot(Inventory inventory, Direction side) {
        int largestSlot = -1;
        int largestCount = 0;
        
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && 
                stack.getCount() > largestCount && 
                canExtract(inventory, stack, i, side)) {
                largestSlot = i;
                largestCount = stack.getCount();
            }
        }
        
        return largestSlot;
    }
    
    /**
     * Extract an item from an inventory into the pipe.
     */
    private static boolean extract(ItemPipeBlockEntity pipe, Inventory inventory, int slot, Direction side) {
        ItemStack stack = inventory.getStack(slot);
        
        // Debug logging to see what we're trying to extract
        Circuitmod.LOGGER.info("[PIPE-EXTRACT] Trying to extract from slot {}: {} x{}", 
            slot, stack.getItem().getName().getString(), stack.getCount());
        
        // Don't extract Air items
        if (stack.getItem() == net.minecraft.item.Items.AIR) {
            Circuitmod.LOGGER.info("[PIPE-EXTRACT] Skipping Air item in slot {}", slot);
            return false;
        }
        
        if (!stack.isEmpty() && canExtract(inventory, stack, slot, side)) {
            // Only extract if pipe is empty (we want to move full stacks at once)
            if (pipe.isEmpty()) {
                // Create a copy for the pipe
                ItemStack extracted = stack.copy();
                pipe.setStack(0, extracted); // Put in pipe slot 0, not the inventory slot
                
                // CRITICAL: sourceExclusion will be set by the calling method that knows the inventory position
                
                // IMPORTANT: Decrement the original stack directly to prevent duplication
                int extractedCount = extracted.getCount();
                stack.setCount(stack.getCount() - extractedCount);
                
                // If the original stack is now empty, clear the slot
                if (stack.getCount() <= 0) {
                    inventory.setStack(slot, ItemStack.EMPTY);
                }
                
                inventory.markDirty();
                
                Circuitmod.LOGGER.info("[PIPE-EXTRACT] Successfully extracted {} x{} from slot {}, remaining: {}", 
                    extracted.getItem().getName().getString(), extractedCount, slot, stack.getCount());
                
                return true;
            } else {
                Circuitmod.LOGGER.info("[PIPE-EXTRACT] Pipe is not empty, cannot extract");
            }
        } else {
            Circuitmod.LOGGER.info("[PIPE-EXTRACT] Cannot extract: isEmpty={}, canExtract={}", 
                stack.isEmpty(), canExtract(inventory, stack, slot, side));
        }
        return false;
    }
    
    /**
     * Extract an item from an ItemEntity into the pipe.
     */
    private static boolean extract(ItemPipeBlockEntity pipe, ItemEntity itemEntity) {
        // Only extract if pipe is empty (we want to move full stacks at once)
        if (pipe.isEmpty()) {
            ItemStack entityStack = itemEntity.getStack().copy();
            
            // Don't extract Air items
            if (entityStack.getItem() == net.minecraft.item.Items.AIR) {
                Circuitmod.LOGGER.info("[PIPE-EXTRACT] Skipping Air item from ItemEntity");
                return false;
            }
            
            // Take the entire stack from the item entity
            pipe.setStack(0, entityStack);
            itemEntity.discard();
            return true;
        }
        return false;
    }
    
    /**
     * Transfer an item from the pipe to a target inventory.
     */
    private static ItemStack transfer(Inventory from, Inventory to, ItemStack stack, @Nullable Direction side) {
        if (to instanceof SidedInventory sidedInventory && side != null) {
            int[] slots = sidedInventory.getAvailableSlots(side);
            
            for (int i = 0; i < slots.length && !stack.isEmpty(); i++) {
                int slotIndex = slots[i];
                stack = tryInsertIntoSlot(from, to, stack, slotIndex, side);
            }
        } else {
            int size = to.size();
            
            for (int i = 0; i < size && !stack.isEmpty(); i++) {
                stack = tryInsertIntoSlot(from, to, stack, i, side);
            }
        }
        
        // Mark destination inventory as dirty (the insert methods handle this)
        to.markDirty();
        
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
                to.setStack(slot, stack.copy());
                to.markDirty();
                // Consume all items from the source stack
                stack.setCount(0);
                return stack; // Return the now-empty stack
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
        if (inventory instanceof SidedInventory sidedInventory) {
            return sidedInventory.canExtract(slot, stack, side);
        }
        
        return true;
    }
    
    /**
     * Get the inventory at a specific position in the world using hopper-style detection.
     */
    @Nullable
    private static Inventory getInventoryAt(World world, BlockPos pos) {
        BlockState blockState = world.getBlockState(pos);
        net.minecraft.block.Block block = blockState.getBlock();
        
        Circuitmod.LOGGER.info("[PIPE-GET-INVENTORY] Checking for inventory at {}, block: {}", 
            pos, block.getName().getString());
        
        // Check if block implements InventoryProvider interface
        if (block instanceof net.minecraft.block.InventoryProvider) {
            Inventory inventory = ((net.minecraft.block.InventoryProvider)block).getInventory(blockState, world, pos);
            Circuitmod.LOGGER.info("[PIPE-GET-INVENTORY] Found InventoryProvider: {}", 
                inventory != null ? inventory.getClass().getSimpleName() : "null");
            return inventory;
        } 
        // Check if block has entity AND entity implements Inventory
        else if (blockState.hasBlockEntity()) {
            net.minecraft.block.entity.BlockEntity blockEntity = world.getBlockEntity(pos);
            Circuitmod.LOGGER.info("[PIPE-GET-INVENTORY] BlockEntity: {}", 
                blockEntity != null ? blockEntity.getClass().getSimpleName() : "null");
                
            if (blockEntity instanceof Inventory inv) {
                // Special case: Handle double chests properly
                if (inv instanceof net.minecraft.block.entity.ChestBlockEntity && 
                    block instanceof net.minecraft.block.ChestBlock) {
                    Inventory doubleChest = net.minecraft.block.ChestBlock.getInventory(
                        (net.minecraft.block.ChestBlock)block, blockState, world, pos, true);
                    Circuitmod.LOGGER.info("[PIPE-GET-INVENTORY] Found chest inventory: {}", 
                        doubleChest != null ? doubleChest.getClass().getSimpleName() : "null");
                    return doubleChest;
                } else {
                    Circuitmod.LOGGER.info("[PIPE-GET-INVENTORY] Found BlockEntity inventory: {}", 
                        inv.getClass().getSimpleName());
                    return inv;
                }
            }
        }
        
        Circuitmod.LOGGER.info("[PIPE-GET-INVENTORY] No inventory found at {}", pos);
        return null;
    }
    
    /**
     * Check if two items can be merged.
     */
    private static boolean canMergeItems(ItemStack first, ItemStack second) {
        return first.getCount() < first.getMaxCount() && 
               ItemStack.areItemsEqual(first, second);
    }
    
    /**
     * Send an animation if enough time has passed since the last one to prevent duplicates
     */
    private void sendAnimationIfAllowed(ServerWorld world, ItemStack stack, BlockPos from, BlockPos to) {
        int currentTick = (int) world.getTime();
        
        // Only send animation if at least 3 ticks have passed since the last one
        if (currentTick - this.lastAnimationTick >= 3) {
            // Send animation starting immediately
            PipeNetworkAnimator.sendMoveAnimation(world, stack.copy(), from, to, ANIMATION_TICKS);
            this.lastAnimationTick = currentTick;
        } else {
            Circuitmod.LOGGER.info("[PIPE-ANIMATION-SKIP] Skipped duplicate animation at {} (last: {}, current: {})", 
                this.getPos(), this.lastAnimationTick, currentTick);
        }
    }
    
    public void setTransferCooldown(int cooldown) {
        this.transferCooldown = cooldown;
    }
    
    public int getTransferCooldown() {
        return this.transferCooldown;
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
    
    /**
     * Check if the pipe is full (holding a full stack).
     */
    public boolean isFull() {
        ItemStack stack = this.inventory.get(0);
        if (stack.isEmpty()) {
            return false;
        }
        // Check if the stack is at its maximum capacity (item's max stack size)
        return stack.getCount() >= stack.getMaxCount();
    }

    @Override
    public ItemStack getStack(int slot) {
        ItemStack stack = this.inventory.get(0);
        // Only log occasionally to avoid spam
        if (world != null && world.isClient() && !stack.isEmpty() && world.getTime() % 200 == 0) {
            Circuitmod.LOGGER.info("[PIPE-GET-STACK] getStack called at " + this.getPos() + 
                                  ", slot: " + slot + 
                                  ", item: " + stack.getItem().getName().getString() + 
                                  ", count: " + stack.getCount());
        }
        return stack;
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack stack = !this.inventory.get(0).isEmpty() ? this.inventory.get(0).split(amount) : ItemStack.EMPTY;
        if (!stack.isEmpty()) {
            markDirty();
        }
        return stack;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack stack = this.inventory.get(0);
        this.inventory.set(0, ItemStack.EMPTY);
        markDirty();
        return stack;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot >= 0 && slot < inventory.size()) {
            // Debug logging to see when items are set in pipes
            if (stack.getItem() == net.minecraft.item.Items.AIR) {
                Circuitmod.LOGGER.info("[PIPE-SET-STACK] WARNING: Setting Air item in pipe at {} slot {}", pos, slot);
            } else if (!stack.isEmpty()) {
                Circuitmod.LOGGER.info("[PIPE-SET-STACK] Setting item {} x{} in pipe at {} slot {}", 
                    stack.getItem().getName().getString(), stack.getCount(), pos, slot);
            }
            
            inventory.set(slot, stack);
            if (stack.getCount() > getMaxCountPerStack()) {
                stack.setCount(getMaxCountPerStack());
            }
        }
    }

    @Override
    public boolean canPlayerUse(net.minecraft.entity.player.PlayerEntity player) {
        return false; // Players don't use pipe inventory directly
    }

    @Override
    public void clear() {
        this.inventory.clear();
        markDirty();
    }
    

    
    /**
     * Get the direction from which the last item entered this pipe.
     */
    public @Nullable Direction getLastInputDirection() {
        return this.lastInputDirection;
    }
    
    /**
     * Debug method to log the current state of the pipe.
     */
    public void debugLogState(String context) {
        Circuitmod.LOGGER.info("[PIPE-DEBUG-" + context + "] Pipe at " + this.getPos() + 
                              ", hasItem: " + !getStack(0).isEmpty() + 
                              ", lastInputDir: " + lastInputDirection + 
                              ", isClient: " + (world != null && world.isClient()));
    }
    
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        
        // Save inventory
        Inventories.writeNbt(nbt, inventory, registries);
        
        // Save transfer state
        nbt.putInt("transferCooldown", transferCooldown);
        
        // Save direction info
        if (lastInputDirection != null) {
            nbt.putInt("lastInputDirection", lastInputDirection.ordinal());
        }
        
        nbt.putLong("lastTickTime", lastTickTime);
    }
    
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        
        // Load inventory
        Inventories.readNbt(nbt, inventory, registries);
        
        // Load transfer state
        transferCooldown = nbt.getInt("transferCooldown").orElse(-1);
        
        // Load direction info
        if (nbt.contains("lastInputDirection")) {
            int ordinal = nbt.getInt("lastInputDirection").orElse(-1);
            if (ordinal >= 0 && ordinal < Direction.values().length) {
                lastInputDirection = Direction.values()[ordinal];
            }
        }
        
        lastTickTime = nbt.getLong("lastTickTime").orElse(0L);
        
        // Flag for network reconnection after loading
        needsNetworkReconnection = true;
    }
    
    //
    // CLIENT SYNC
    //
    @Override
    public @Nullable BlockEntityUpdateS2CPacket toUpdatePacket() {
        // Called by Minecraft when it needs to send a block‑entity update to the client
        // Only log occasionally to prevent spam
        if (world != null && world.getTime() % 100 == 0) {
            Circuitmod.LOGGER.info("[PIPE-UPDATE-PACKET] Creating update packet at " + this.getPos() + 
                                  ", hasItem: " + !getStack(0).isEmpty() + 
                                  ", lastInputDir: " + lastInputDirection);
        }
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        // send exactly the same data as writeNbt(...)
        // Only log occasionally to prevent spam
        if (world != null && world.getTime() % 100 == 0) {
            Circuitmod.LOGGER.info("[PIPE-CHUNK-DATA] Creating initial chunk data at " + this.getPos() + 
                                  ", hasItem: " + !getStack(0).isEmpty() + 
                                  ", lastInputDir: " + lastInputDirection);
        }
        NbtCompound tag = new NbtCompound();
        writeNbt(tag, registries);
        return tag;
    }

    @Override
    public void markDirty() {
        super.markDirty();
        
        // Only log markDirty calls occasionally to prevent spam
        if (world != null && world.getTime() % 100 == 0) { // Only log every 5 seconds
            Circuitmod.LOGGER.info("[PIPE-MARK-DIRTY] markDirty called at " + this.getPos() + 
                                  ", hasItem: " + !getStack(0).isEmpty() + 
                                  ", lastInputDir: " + lastInputDirection + 
                                  ", isClient: " + (world != null && world.isClient()));
        }
        
        if (world != null && !world.isClient()) {
            // Build the vanilla packet containing our NBT:
            BlockEntityUpdateS2CPacket pkt = BlockEntityUpdateS2CPacket.create(this);
            // Send to every player within 64 blocks in this dimension:
            ServerWorld sw = (ServerWorld)world;
            int playerCount = sw.getServer().getPlayerManager().getPlayerList().size();
            
            // Only log broadcast occasionally to prevent spam
            if (world.getTime() % 100 == 0) {
                Circuitmod.LOGGER.info("[PIPE-BROADCAST] Broadcasting to " + playerCount + " players at " + this.getPos());
            }
            
            sw.getServer().getPlayerManager()
                .sendToAround(
                    /* except= */ null,
                    this.pos.getX(), this.pos.getY(), this.pos.getZ(),
                    /* radius= */ 64.0,
                    sw.getRegistryKey(),
                    pkt
                );
        }
    }

    /**
     * Checks for new unconnected inventories and pipes around the pipe and triggers a network rescan if needed.
     */
    private static void checkForUnconnectedInventories(World world, BlockPos pos, ItemPipeBlockEntity blockEntity) {
        ItemNetwork network = ItemNetworkManager.getNetworkForPipe(pos);
        if (network == null) {
            return;
        }
        
        // Count current inventories in network
        int currentInventoryCount = network.getConnectedInventories().size();
        
        // Check all 6 directions for inventories AND pipes
        boolean foundNewConnection = false;
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.offset(direction);
            BlockState blockState = world.getBlockState(neighborPos);
            
            // Check for new pipes (any pipe type)
            if (blockState.getBlock() instanceof starduster.circuitmod.block.BasePipeBlock) {
                // Check if this pipe is in a different network that we should merge with
                ItemNetwork neighborNetwork = ItemNetworkManager.getNetworkForPipe(neighborPos);
                if (neighborNetwork != null && neighborNetwork != network) {
                    Circuitmod.LOGGER.info("[PIPE-DISCOVERY] Found new pipe connection at {} with different network {}", neighborPos, neighborNetwork.getNetworkId());
                    foundNewConnection = true;
                    break;
                }
                continue; // Skip inventory check for pipes
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
                Circuitmod.LOGGER.info("[PIPE-DISCOVERY] Found new inventory at {} next to pipe at {}", neighborPos, pos);
                foundNewConnection = true;
                break; // Only need to find one to trigger rescan
            }
        }
        
        // If we found a new connection, trigger a network rescan
        if (foundNewConnection) {
            Circuitmod.LOGGER.info("[PIPE-DISCOVERY] Triggering network rescan due to new connection discovery");

        }
    }
} 