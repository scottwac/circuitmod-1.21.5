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

/**
 * Block entity for item pipes, which can transfer items between inventories
 * using the network-based routing system.
 */
public class ItemPipeBlockEntity extends BlockEntity implements Inventory {
    private static final int INVENTORY_SIZE = 1; 
    private static final int COOLDOWN_TICKS = 3; // Improved throughput (was 5, now 3)
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
        
        // Check if we're in a network, if not try to reconnect
        ItemNetwork currentNetwork = ItemNetworkManager.getNetworkForPipe(pos);
        if (currentNetwork == null) {
            Circuitmod.LOGGER.info("[PIPE-TICK] Pipe at {} not in network, attempting to reconnect", pos);
            ItemNetworkManager.connectPipe(world, pos);
            currentNetwork = ItemNetworkManager.getNetworkForPipe(pos);
            if (currentNetwork != null) {
                Circuitmod.LOGGER.info("[PIPE-TICK] Successfully reconnected to network {}", currentNetwork.getNetworkId());
            }
        }
        
        // Handle network reconnection after world load
        if (blockEntity.needsNetworkReconnection) {
            Circuitmod.LOGGER.info("[PIPE-RECONNECT] Reconnecting pipe at {} to network after world load", pos);
            ItemNetworkManager.connectPipe(world, pos);
            blockEntity.needsNetworkReconnection = false;
        }
        
        // Check for new unconnected inventories every 5 ticks (was 10) for faster discovery
        if (world.getTime() % 5 == 0) {
            checkForUnconnectedInventories(world, pos, blockEntity);
        }
        
        // Monitor inventory changes for closer destinations gaining space
        if (currentNetwork != null && world.getTime() % 5 == 0) { // Check every 5 ticks instead of 20
            currentNetwork.monitorInventoryChanges();
        }
        
        // Periodically invalidate route cache to ensure fresh routes
        if (currentNetwork != null && world.getTime() % 20 == 0) { // Every 20 ticks (1 second)
            currentNetwork.invalidateRouteCache();
            Circuitmod.LOGGER.info("[PIPE-TICK] Periodic route cache invalidation at {}", pos);
        }
        
        // Only proceed with item processing if we have an item to process
        if (blockEntity.getStack(0).isEmpty()) {
            return;
        }
        
        Circuitmod.LOGGER.info("[PIPE-TICK] Processing item at {} - {}", pos, blockEntity.getStack(0).getItem().getName().getString());
        
        blockEntity.transferCooldown--;
        blockEntity.lastTickTime = world.getTime();
        
        if (!blockEntity.needsCooldown()) {
            boolean didWork = false;
            
            // If we have an item, use network routing to find destination
            if (!blockEntity.isEmpty()) {
                ItemStack item = blockEntity.getStack(0);
                
                // Get the network for this pipe
                ItemNetwork network = ItemNetworkManager.getNetworkForPipe(pos);
                if (network != null) {
                    Circuitmod.LOGGER.info("[PIPE-ROUTING] Pipe at {} routing item with sourceExclusion: {}", pos, blockEntity.sourceExclusion);
                    // Find a route for this item with fallback support, excluding the source
                    ItemRoute route = network.findRouteWithFallback(item, pos, blockEntity.sourceExclusion);
                    if (route != null) {
                        // Get the next step in the route
                        BlockPos nextStep = route.getNextPosition(pos);
                        if (nextStep != null) {
                            Circuitmod.LOGGER.info("[PIPE-TRANSFER] Reached destination {}, transferring to inventory", nextStep);
                                                    if (nextStep.equals(route.getDestination())) {
                            // If next step is the destination, transfer directly to inventory
                            if (transferToInventoryAt(world, nextStep, blockEntity)) {
                                didWork = true;
                            } else {
                                // Transfer failed - try to find alternative destination
                                Circuitmod.LOGGER.info("[PIPE-FALLBACK] Transfer to {} failed, looking for alternatives", nextStep);
                                ItemRoute alternativeRoute = network.findRouteExcludingFailed(item, pos, blockEntity.sourceExclusion, nextStep);
                                if (alternativeRoute != null) {
                                    Circuitmod.LOGGER.info("[PIPE-FALLBACK] Found alternative route: {} -> {}", 
                                        pos, alternativeRoute.getDestination());
                                    
                                    // Try the alternative destination
                                    BlockPos altNextStep = alternativeRoute.getNextPosition(pos);
                                    if (altNextStep != null) {
                                        if (altNextStep.equals(alternativeRoute.getDestination())) {
                                            // Try alternative inventory
                                            if (transferToInventoryAt(world, altNextStep, blockEntity)) {
                                                didWork = true;
                                                Circuitmod.LOGGER.info("[PIPE-FALLBACK] Successfully transferred to alternative destination {}", altNextStep);
                                            }
                                        } else {
                                            // Move towards alternative destination
                                            if (transferToPipeAt(world, altNextStep, blockEntity)) {
                                                didWork = true;
                                                Circuitmod.LOGGER.info("[PIPE-FALLBACK] Moving towards alternative destination via {}", altNextStep);
                                            }
                                        }
                                    }
                                } else {
                                    Circuitmod.LOGGER.info("[PIPE-FALLBACK] No alternative destinations available for {}", item.getItem().getName().getString());
                                }
                            }
                        } else {
                            // Otherwise, transfer to the next pipe in the route
                            Circuitmod.LOGGER.info("[PIPE-TRANSFER] Moving to next pipe at {}", nextStep);
                            if (transferToPipeAt(world, nextStep, blockEntity)) {
                                didWork = true;
                            }
                        }
                        }
                    } else {
                        Circuitmod.LOGGER.info("[PIPE-TRANSFER] No route found - item stuck at {} with sourceExclusion {}", pos, blockEntity.sourceExclusion);
                        
                        // If item has been stuck for too long, try one more time to find alternatives before dropping
                        if (blockEntity.transferCooldown <= -300) { // Been stuck for 15+ seconds
                            // Final attempt to find ANY available destination
                            ItemRoute lastChanceRoute = network.findRouteWithFallback(item, pos, blockEntity.sourceExclusion);
                            if (lastChanceRoute != null) {
                                Circuitmod.LOGGER.info("[PIPE-LAST-CHANCE] Found route after long delay: {} -> {}", 
                                    pos, lastChanceRoute.getDestination());
                                // Reset cooldown and try this route
                                blockEntity.transferCooldown = 0;
                            } else {
                                // No routes available anywhere - drop the item
                                Circuitmod.LOGGER.info("[PIPE-OVERFLOW] No destinations available, dropping stuck item {} at {}", 
                                    blockEntity.getStack(0).getItem().getName().getString(), pos);
                                
                                // Drop the item into the world
                                ItemStack droppedStack = blockEntity.removeStack(0);
                                if (!droppedStack.isEmpty() && world instanceof ServerWorld serverWorld) {
                                    // Drop at pipe position + 0.5 offset
                                    double x = pos.getX() + 0.5;
                                    double y = pos.getY() + 1.0; // Slightly above the pipe
                                    double z = pos.getZ() + 0.5;
                                    
                                    net.minecraft.entity.ItemEntity itemEntity = new net.minecraft.entity.ItemEntity(
                                        serverWorld, x, y, z, droppedStack);
                                    serverWorld.spawnEntity(itemEntity);
                                    
                                    blockEntity.markDirty();
                                }
                                didWork = true; // Reset cooldown since we handled the item
                            }
                        }
                    }
                } else {
                    // No network, try direct adjacent transfer as fallback
                    Circuitmod.LOGGER.info("[PIPE-TRANSFER] No network found, trying adjacent transfer");
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
        
        // Find a route for this item
        ItemRoute route = network.findRoute(currentItem);
        if (route == null) {
            // No route found, try direct adjacent transfer as fallback
            return transferToAdjacentInventory(world, pipePos, blockEntity);
        }
        
        // Get the next step in the route
        BlockPos nextStep = getNextStepInRoute(route, pipePos);
        if (nextStep == null) {
            return false;
        }
        
        // If next step is the destination, transfer directly to inventory
        if (nextStep.equals(route.getDestination())) {
            return transferToInventoryAt(world, nextStep, blockEntity);
        }
        
        // Otherwise, transfer to the next pipe in the route
        return transferToPipeAt(world, nextStep, blockEntity);
    }
    
    /**
     * Gets the next step in a route from the current position.
     */
    private static BlockPos getNextStepInRoute(ItemRoute route, BlockPos currentPos) {
        java.util.List<BlockPos> path = route.getPath();
        
        for (int i = 0; i < path.size() - 1; i++) {
            if (path.get(i).equals(currentPos)) {
                return path.get(i + 1);
            }
        }
        
        return null; // Current position not found in route
    }
    
    /**
     * Transfers item to a specific inventory location.
     */
    private static boolean transferToInventoryAt(World world, BlockPos inventoryPos, ItemPipeBlockEntity blockEntity) {
        Circuitmod.LOGGER.info("[PIPE-INVENTORY-TRANSFER] Attempting to transfer to inventory at {}", inventoryPos);
        
        Inventory inventory = getInventoryAt(world, inventoryPos);
        if (inventory == null) {
            Circuitmod.LOGGER.info("[PIPE-INVENTORY-TRANSFER] No inventory found at {}", inventoryPos);
            return false;
        }
        
        ItemStack currentStack = blockEntity.getStack(0);
        if (currentStack.isEmpty()) {
            Circuitmod.LOGGER.info("[PIPE-INVENTORY-TRANSFER] No item to transfer");
            return false;
        }
        
        // Check if inventory has any space for this item type
        if (!hasSpaceForItem(inventory, currentStack)) {
            Circuitmod.LOGGER.info("[PIPE-INVENTORY-TRANSFER] Inventory at {} is full - no space for {}", 
                inventoryPos, currentStack.getItem().getName().getString());
            
            // Invalidate route cache to force recalculation when inventory is full
            ItemNetwork network = ItemNetworkManager.getNetworkForPipe(blockEntity.getPos());
            if (network != null) {
                network.invalidateRouteCache();
                Circuitmod.LOGGER.info("[PIPE-INVENTORY-TRANSFER] Invalidated route cache due to full inventory at {}", inventoryPos);
            }
            
            return false;
        }
        
        Circuitmod.LOGGER.info("[PIPE-INVENTORY-TRANSFER] Transferring {} x{} to {} at {}", 
            currentStack.getItem().getName().getString(), currentStack.getCount(),
            inventory.getClass().getSimpleName(), inventoryPos);
        
        Direction transferDirection = getDirectionTowards(blockEntity.getPos(), inventoryPos);
        
        // Try to insert the FULL stack
        ItemStack stackToInsert = currentStack.copy();
        ItemStack remaining = transfer(blockEntity, inventory, stackToInsert, transferDirection);
        
        Circuitmod.LOGGER.info("[PIPE-INVENTORY-TRANSFER] Transfer result: {} remaining from {} original", 
            remaining.getCount(), currentStack.getCount());
        
        if (remaining.isEmpty() || remaining.getCount() < currentStack.getCount()) {
            // Successfully transferred at least part of the stack
            blockEntity.setStack(0, remaining);
            blockEntity.markDirty();
            
            // Send animation for item leaving the pipe
            if (world instanceof ServerWorld serverWorld) {
                blockEntity.sendAnimationIfAllowed(serverWorld, currentStack, blockEntity.getPos(), inventoryPos);
            }
            
            Circuitmod.LOGGER.info("[PIPE-INVENTORY-TRANSFER] Successfully transferred {} items to {}", 
                currentStack.getCount() - remaining.getCount(), inventoryPos);
            return true;
        }
        
        Circuitmod.LOGGER.info("[PIPE-INVENTORY-TRANSFER] Failed to transfer any items to {} - inventory appears to be full", inventoryPos);
        
        // Invalidate route cache to force recalculation when transfer fails
        ItemNetwork network = ItemNetworkManager.getNetworkForPipe(blockEntity.getPos());
        if (network != null) {
            network.invalidateRouteCache();
            Circuitmod.LOGGER.info("[PIPE-INVENTORY-TRANSFER] Invalidated route cache due to failed transfer to {}", inventoryPos);
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
                
                Circuitmod.LOGGER.info("[PIPE-EXTRACT] Extracted {} x{} from slot {}, remaining: {}", 
                    extracted.getItem().getName().getString(), extractedCount, slot, stack.getCount());
                
                return true;
            }
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
        if (world != null && world.isClient() && !stack.isEmpty() && world.getTime() % 20 == 0) {
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
        // Only log when actually setting a non-empty stack or clearing a stack
        if (!stack.isEmpty() || !this.inventory.get(0).isEmpty()) {
            Circuitmod.LOGGER.info("[PIPE-SET-STACK] setStack called at " + this.getPos() + 
                                  ", slot: " + slot + 
                                  ", item: " + (stack.isEmpty() ? "empty" : stack.getItem().getName().getString()) +
                                  ", count: " + stack.getCount() +
                                  ", isClient: " + (world != null && world.isClient()));
        }
        
        // Ensure the stack doesn't exceed the item's maximum stack size
        if (!stack.isEmpty() && stack.getCount() > stack.getMaxCount()) {
            stack.setCount(stack.getMaxCount());
        }
        
        this.inventory.set(0, stack);
        
        // Reset input direction when pipe becomes empty to prevent stale data
        if (stack.isEmpty()) {
            this.lastInputDirection = null;
        }
        
        markDirty();
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
        Circuitmod.LOGGER.info("[PIPE-UPDATE-PACKET] Creating update packet at " + this.getPos() + 
                              ", hasItem: " + !getStack(0).isEmpty() + 
                              ", lastInputDir: " + lastInputDirection);
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        // send exactly the same data as writeNbt(...)
        Circuitmod.LOGGER.info("[PIPE-CHUNK-DATA] Creating initial chunk data at " + this.getPos() + 
                              ", hasItem: " + !getStack(0).isEmpty() + 
                              ", lastInputDir: " + lastInputDirection);
        NbtCompound tag = new NbtCompound();
        writeNbt(tag, registries);
        return tag;
    }

    @Override
    public void markDirty() {
        super.markDirty();
        
        // Only log markDirty calls occasionally to prevent spam
        if (world != null && world.getTime() % 20 == 0) { // Only log every second
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
            if (world.getTime() % 20 == 0) {
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
            network.forceRescanAllInventories();
        }
    }
} 