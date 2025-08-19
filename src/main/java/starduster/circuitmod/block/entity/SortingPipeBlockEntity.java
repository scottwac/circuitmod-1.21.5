package starduster.circuitmod.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.networkblocks.BasePipeBlock;
import starduster.circuitmod.item.network.ItemNetworkManager;
import starduster.circuitmod.item.network.ItemNetwork;
import starduster.circuitmod.network.PipeNetworkAnimator;
import starduster.circuitmod.screen.SortingPipeScreenHandler;
import starduster.circuitmod.util.ImplementedInventory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SortingPipe - Routes items based on directional filters.
 * Items matching a filter go in that direction, others go to any unfiltered direction.
 * Uses the same hop-by-hop logic as ItemPipe but with filtering.
 */
public class SortingPipeBlockEntity extends BlockEntity implements NamedScreenHandlerFactory, ImplementedInventory {
    
    // Debug logging control - set to true only when debugging
    private static final boolean DEBUG_LOGGING = false;

    
    /**
     * Result of pathfinding evaluation containing both score and path
     */
    private static class PathResult {
        final int score;
        final List<BlockPos> path;
        
        PathResult(int score, List<BlockPos> path) {
            this.score = score;
            this.path = new ArrayList<>(path);
        }
    }
    // Main item storage (1 slot for current item being processed)
    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(1, ItemStack.EMPTY);
    
    // Filter inventory (6 slots for directional filters: N, E, S, W, Up, Down)
    private final DefaultedList<ItemStack> filterInventory = DefaultedList.ofSize(6, ItemStack.EMPTY);
    
    private static final int COOLDOWN_TICKS = 2; // Much faster movement
    private static final int STUCK_TIMEOUT = 100; // Same as ItemPipe
    
    private int transferCooldown = 0;
    private Direction lastInputDirection = null;
    private int stuckTimer = 0;

    // Direction to slot mapping for filters
    public static final Direction[] DIRECTION_ORDER = {
        Direction.NORTH,  // Slot 0
        Direction.EAST,   // Slot 1  
        Direction.SOUTH,  // Slot 2
        Direction.WEST,   // Slot 3
        Direction.UP,     // Slot 4
        Direction.DOWN    // Slot 5
    };

    public SortingPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SORTING_PIPE, pos, state);
    }
    
    public void onPlaced() {
        if (world != null && !world.isClient()) {
            ItemNetworkManager.connectPipe(world, pos);
        }
    }
    
    public void onRemoved() {
        if (world != null && !world.isClient) {
            ItemNetworkManager.disconnectPipe(world, pos);
        }
    }

    public static void tick(World world, BlockPos pos, BlockState state, SortingPipeBlockEntity blockEntity) {
        if (world.isClient()) return;
        
        if (blockEntity.isEmpty()) return;
        
        blockEntity.transferCooldown--;
        if (blockEntity.transferCooldown > 0) return;
        
        ItemStack currentItem = blockEntity.getStack(0);
        
        // Debug logging for sorting pipe activity
        if (DEBUG_LOGGING && world.getTime() % 20 == 0) { // Log every second
            Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] At {}: processing item {}, cooldown: {}, stuck timer: {}", 
                pos, currentItem.getItem().getName().getString(), blockEntity.transferCooldown, blockEntity.stuckTimer);
        }
        
        // Try to move the item using filtering logic
        if (blockEntity.tryMoveItemWithFiltering(world, pos, currentItem)) {
            blockEntity.transferCooldown = COOLDOWN_TICKS;
            blockEntity.stuckTimer = 0;
            blockEntity.markDirty();
            
            if (DEBUG_LOGGING) {
                Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] Successfully moved item {} from {}", 
                    currentItem.getItem().getName().getString(), pos);
            }
        } else {
            // Item couldn't move - increment stuck timer
            blockEntity.stuckTimer++;
            
            if (DEBUG_LOGGING && world.getTime() % 20 == 0) { // Log every second when stuck
                Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] Item {} stuck at {} for {} ticks", 
                    currentItem.getItem().getName().getString(), pos, blockEntity.stuckTimer);
            }
            
            if (blockEntity.stuckTimer >= STUCK_TIMEOUT) {
                Circuitmod.LOGGER.warn("[SORTING-PIPE] Item {} stuck at {} for {} ticks, attempting emergency unstuck", 
                    currentItem.getItem().getName().getString(), pos, blockEntity.stuckTimer);
                
                if (blockEntity.emergencyUnstuck(world, pos, currentItem)) {
                    blockEntity.transferCooldown = COOLDOWN_TICKS;
                    blockEntity.stuckTimer = 0;
                    blockEntity.markDirty();
                }
            }
        }
    }

    /**
     * Try to move item with filtering logic applied.
     * Priority:
     * 1. Try directions allowed by filters (if item has specific filter)
     * 2. Use improved pathfinding for allowed directions
     * 3. Try unfiltered directions (if item has no specific filter)
     * 4. Try any direction as emergency fallback
     */
    private boolean tryMoveItemWithFiltering(World world, BlockPos pos, ItemStack item) {
        List<Direction> allowedDirections = getAllowedDirectionsForItem(item);
        
        if (DEBUG_LOGGING) {
            Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] Item {} at {} - allowed directions: {}", 
                item.getItem().getName().getString(), pos, allowedDirections);
        }
        
        // DIAGNOSTIC: Check if any allowed directions actually have pipes
        boolean anyAllowedHasPipe = allowedDirections.stream()
            .anyMatch(dir -> world.getBlockState(pos.offset(dir)).getBlock() instanceof BasePipeBlock);
        if (!anyAllowedHasPipe && !allowedDirections.isEmpty()) {
            Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] WARNING: No pipes found in allowed directions! Checking all directions:");
            for (Direction dir : Direction.values()) {
                BlockPos checkPos = pos.offset(dir);
                BlockState checkState = world.getBlockState(checkPos);
                Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] Direction {} at {}: {} (isPipe: {})", 
                    dir, checkPos, checkState.getBlock().getClass().getSimpleName(), 
                    checkState.getBlock() instanceof BasePipeBlock);
            }
            
            // FALLBACK: If no pipes in allowed directions, use any available pipes
            List<Direction> availablePipes = new ArrayList<>();
            for (Direction dir : Direction.values()) {
                if ((lastInputDirection == null || dir != lastInputDirection) && 
                    world.getBlockState(pos.offset(dir)).getBlock() instanceof BasePipeBlock) {
                    availablePipes.add(dir);
                }
            }
            if (!availablePipes.isEmpty()) {
                Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] FALLBACK: Using available pipes instead: {}", availablePipes);
                allowedDirections = availablePipes;
            }
        }
        
        // Step 1: Try allowed directions first (try inventories, then pipes)
        for (Direction direction : allowedDirections) {
            if (lastInputDirection != null && direction == lastInputDirection) {
                if (DEBUG_LOGGING) {
                    Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] Skipping direction {} (backwards - came from this direction)", direction);
                }
                continue; // Don't go backwards
            }
            
            BlockPos targetPos = pos.offset(direction);
            if (DEBUG_LOGGING) {
                Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] Trying direction {} to {}", direction, targetPos);
                // DIAGNOSTIC: Log what block is actually at the target position
                BlockState targetState = world.getBlockState(targetPos);
                Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] Target block at {}: {} (isPipe: {})", 
                    targetPos, targetState.getBlock().getClass().getSimpleName(), 
                    targetState.getBlock() instanceof BasePipeBlock);
            }
            
            // Try to deliver to inventory first
            if (tryInsertIntoInventory(world, targetPos, item)) {
                if (DEBUG_LOGGING) {
                    Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] Successfully delivered to inventory at {}", targetPos);
                }
                // Trigger animation only when movement actually succeeds
                if (world instanceof ServerWorld serverWorld) {
                    List<BlockPos> path = new ArrayList<>();
                    path.add(pos);
                    path.add(targetPos);
                    PipeNetworkAnimator.startItemPath(serverWorld, item, pos, path);
                }
                removeStack(0);
                return true;
            }
            
            // Then try to pass to pipe
            if (tryPassToPipe(world, targetPos, item, direction)) {
                if (DEBUG_LOGGING) {
                    Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] Successfully passed to pipe at {}", targetPos);
                }
                // Trigger animation only when movement actually succeeds
                if (world instanceof ServerWorld serverWorld) {
                    List<BlockPos> path = new ArrayList<>();
                    path.add(pos);
                    path.add(targetPos);
                    PipeNetworkAnimator.startItemPath(serverWorld, item, pos, path);
                }
                removeStack(0);
                return true;
            }
            
            if (DEBUG_LOGGING) {
                Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] Failed to move in direction {}", direction);
            }
        }
        
        // Step 2: If we have multiple allowed directions, use pathfinding to find the best one
        if (allowedDirections.size() > 1) {
            Direction bestDirection = findBestDirectionWithPathfinding(world, pos, item, allowedDirections);
            if (bestDirection != null) {
                BlockPos targetPos = pos.offset(bestDirection);
                if (tryPassToPipe(world, targetPos, item, bestDirection)) {
                    // Trigger animation only when movement actually succeeds
                    if (world instanceof ServerWorld serverWorld) {
                        List<BlockPos> path = new ArrayList<>();
                        path.add(pos);
                        path.add(targetPos);
                        PipeNetworkAnimator.startItemPath(serverWorld, item, pos, path);
                    }
                    removeStack(0);
                    return true;
                }
            }
        }
        
        // Step 3: If no allowed directions worked, this might be an emergency case
        // Try any direction that isn't backwards as absolute fallback
        if (DEBUG_LOGGING) {
            Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] No allowed directions worked, trying fallback directions");
        }
        for (Direction direction : Direction.values()) {
            if (lastInputDirection != null && direction == lastInputDirection) {
                if (DEBUG_LOGGING) {
                    Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] Skipping fallback direction {} (backwards - came from this direction)", direction);
                }
                continue;
            }
            if (allowedDirections.contains(direction)) {
                if (DEBUG_LOGGING) {
                    Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] Skipping fallback direction {} (already tried)", direction);
                }
                continue; // Already tried these
            }
            
            BlockPos targetPos = pos.offset(direction);
            if (DEBUG_LOGGING) {
                Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] Trying fallback direction {} to {}", direction, targetPos);
            }
            
            if (tryInsertIntoInventory(world, targetPos, item)) {
                // Trigger animation only when movement actually succeeds
                if (world instanceof ServerWorld serverWorld) {
                    List<BlockPos> path = new ArrayList<>();
                    path.add(pos);
                    path.add(targetPos);
                    PipeNetworkAnimator.startItemPath(serverWorld, item, pos, path);
                }
                removeStack(0);
                if (DEBUG_LOGGING) {
                    Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] Used fallback direction {} for item {}", 
                        direction, item.getItem().getName().getString());
                }
                return true;
            }
            
            if (tryPassToPipe(world, targetPos, item, direction)) {
                // Trigger animation only when movement actually succeeds
                if (world instanceof ServerWorld serverWorld) {
                    List<BlockPos> path = new ArrayList<>();
                    path.add(pos);
                    path.add(targetPos);
                    PipeNetworkAnimator.startItemPath(serverWorld, item, pos, path);
                }
                removeStack(0);
                if (DEBUG_LOGGING) {
                    Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] Used fallback direction {} for item {}", 
                        direction, item.getItem().getName().getString());
                }
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Emergency unstuck method - similar to ItemPipe but considers filters
     */
    private boolean emergencyUnstuck(World world, BlockPos pos, ItemStack item) {
        Circuitmod.LOGGER.info("[SORTING-EMERGENCY] Attempting to unstuck item {} at {}", 
            item.getItem().getName().getString(), pos);
        
        // Try any direction, ignoring filters and input direction
        for (Direction direction : Direction.values()) {
            BlockPos nextPos = pos.offset(direction);
            
            if (tryPassToPipe(world, nextPos, item, direction)) {
                removeStack(0);
                Circuitmod.LOGGER.info("[SORTING-EMERGENCY] Successfully unstuck item to {}", nextPos);
                return true;
            }
        }
        
        // Last resort: try to dump into any adjacent inventory
        for (Direction direction : Direction.values()) {
            BlockPos targetPos = pos.offset(direction);
            if (!(world.getBlockState(targetPos).getBlock() instanceof BasePipeBlock)) {
                Inventory inventory = getInventoryAt(world, targetPos);
                if (inventory != null) {
                    ItemStack remaining = insertIntoInventory(inventory, item.copy());
                    if (remaining.getCount() < item.getCount()) {
                        setStack(0, remaining);
                        inventory.markDirty();
                        Circuitmod.LOGGER.info("[SORTING-EMERGENCY] Partially delivered item to inventory at {}", targetPos);
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Find the best direction using pathfinding, considering only allowed directions.
     */
    private Direction findBestDirectionWithPathfinding(World world, BlockPos pos, ItemStack item, List<Direction> allowedDirections) {
        Direction bestDirection = null;
        int bestScore = -1;
        List<BlockPos> bestPath = null;
        
        for (Direction direction : allowedDirections) {
            if (lastInputDirection != null && direction == lastInputDirection) continue; // Don't go backwards
            
            BlockPos nextPos = pos.offset(direction);
            PathResult result = evaluatePathScoreImproved(world, nextPos, item, direction, 0, new HashSet<>(), new ArrayList<>());
            
            if (result.score > bestScore) {
                bestScore = result.score;
                bestDirection = direction;
                bestPath = result.path;
            }
        }
        
        return bestDirection;
    }
    
    /**
     * Improved pathfinding that explores multiple branches simultaneously.
     * Uses a visited set to prevent infinite loops and explores all possible paths.
     */
    private PathResult evaluatePathScoreImproved(World world, BlockPos pos, ItemStack item, Direction direction, int depth, Set<BlockPos> visited, List<BlockPos> currentPath) {
        if (depth >= 12 || visited.contains(pos)) { // Use same depth as ItemPipe
            return new PathResult(0, currentPath);
        }
        
        visited.add(pos);
        List<BlockPos> path = new ArrayList<>(currentPath);
        path.add(pos);
        BlockState state = world.getBlockState(pos);
        
        // Found an inventory - score based on distance and availability
        if (!(state.getBlock() instanceof BasePipeBlock)) {
            Inventory inventory = getInventoryAt(world, pos);
            if (inventory != null) {
                if (hasSpaceForItem(inventory, item)) {
                    visited.remove(pos); // Remove from visited for other paths
                    return new PathResult(200 - (depth * 15), path); // Higher base score for better pathfinding
                } else {
                    visited.remove(pos);
                    return new PathResult(5, path); // Inventory with no space gets minimal score
                }
            }
            visited.remove(pos);
            return new PathResult(-20, path); // Not an inventory - negative score
        }
        
        // Found a pipe - explore all possible directions from this pipe
        BlockEntity pipeEntity = world.getBlockEntity(pos);
        if (pipeEntity instanceof Inventory pipe && pipe.isEmpty()) {
            int bestScore = -1;
            List<BlockPos> bestPath = new ArrayList<>(path);
            
            // Explore all directions from this pipe (full branching)
            for (Direction exploreDir : Direction.values()) {
                if (exploreDir == direction.getOpposite()) continue; // Don't go backwards
                
                BlockPos explorePos = pos.offset(exploreDir);
                PathResult result = evaluatePathScoreImproved(world, explorePos, item, exploreDir, depth + 1, visited, path);
                if (result.score > bestScore) {
                    bestScore = result.score;
                    bestPath = result.path;
                }
            }
            
            visited.remove(pos); // Remove from visited for other paths
            return new PathResult(bestScore > 0 ? bestScore - 2 : bestScore, bestPath); // Subtract 2 for each pipe hop
        }
        
        visited.remove(pos);
        return new PathResult(-10, path); // Blocked pipe or invalid path
    }
    
    /**
     * Get all directions that are allowed for a specific item based on filters.
     */
    private List<Direction> getAllowedDirectionsForItem(ItemStack item) {
        List<Direction> allowedDirections = new ArrayList<>();
        
        // Debug: Log all filters
        if (DEBUG_LOGGING) {
            Circuitmod.LOGGER.info("[SORTING-FILTER-DEBUG] Checking filters for item: {}", item.getItem().getName().getString());
            for (int i = 0; i < 6; i++) {
                ItemStack filterStack = getFilterStack(i);
                if (!filterStack.isEmpty()) {
                    Circuitmod.LOGGER.info("[SORTING-FILTER-DEBUG] Filter {} ({}): {}", i, DIRECTION_ORDER[i], filterStack.getItem().getName().getString());
                }
            }
        }
        
        // Check if this item has a specific filter set
        for (int i = 0; i < 6; i++) {
            ItemStack filterStack = getFilterStack(i);
            if (!filterStack.isEmpty() && ItemStack.areItemsAndComponentsEqual(item, filterStack)) {
                // This item has a specific filter - it can ONLY go in this direction
                Direction direction = DIRECTION_ORDER[i];
                allowedDirections.add(direction);
                if (DEBUG_LOGGING) {
                    Circuitmod.LOGGER.info("[SORTING-FILTER-DEBUG] Item matches filter in direction: {}", direction);
                }
            }
        }
        
        // If this item has specific filters, it can ONLY go in those directions
        if (!allowedDirections.isEmpty()) {
            if (DEBUG_LOGGING) {
                Circuitmod.LOGGER.info("[SORTING-FILTER-DEBUG] Item has specific filter, allowed directions: {}", allowedDirections);
            }
            return allowedDirections;
        }
        
        // If this item has no specific filter, it can go in any direction that doesn't have ANY filter
        // (because filtered directions are reserved for their specific items)
        for (int i = 0; i < 6; i++) {
            ItemStack filterStack = getFilterStack(i);
            if (filterStack.isEmpty()) {
                Direction direction = DIRECTION_ORDER[i];
                allowedDirections.add(direction);
                if (DEBUG_LOGGING) {
                    Circuitmod.LOGGER.info("[SORTING-FILTER-DEBUG] Direction {} has no filter, allowing item", direction);
                }
            }
        }
        
        // If all directions are filtered for other items, allow all directions as fallback
        if (allowedDirections.isEmpty()) {
            for (Direction dir : Direction.values()) {
                allowedDirections.add(dir);
            }
            if (DEBUG_LOGGING) {
                Circuitmod.LOGGER.info("[SORTING-FILTER-DEBUG] All directions filtered, allowing all as fallback");
            }
        }
        
        if (DEBUG_LOGGING) {
            Circuitmod.LOGGER.info("[SORTING-FILTER-DEBUG] Final allowed directions: {}", allowedDirections);
        }
        return allowedDirections;
    }
    
    /**
     * Try to insert item into an inventory.
     */
    private boolean tryInsertIntoInventory(World world, BlockPos pos, ItemStack item) {
        if (world.getBlockState(pos).getBlock() instanceof BasePipeBlock) {
            return false;
        }
        
        Inventory inventory = getInventoryAt(world, pos);
        if (inventory == null) return false;
        
        if (!hasSpaceForItem(inventory, item)) return false;
        
        ItemStack remaining = insertIntoInventory(inventory, item.copy());
        if (remaining.isEmpty()) {
            inventory.markDirty();
            
            // Note: Animation is now handled by continuous path system in findBestDirectionWithPathfinding
            
            if (DEBUG_LOGGING) {
                Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] Delivered {} to inventory at {}", 
                    item.getItem().getName().getString(), pos);
            }
            return true;
        }
        
        return false;
    }
    
    /**
     * Try to pass item to another pipe.
     */
    private boolean tryPassToPipe(World world, BlockPos pos, ItemStack item, Direction direction) {
        if (DEBUG_LOGGING) {
            Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] tryPassToPipe: checking {} for item {}", pos, item.getItem().getName().getString());
        }
        
        if (!(world.getBlockState(pos).getBlock() instanceof BasePipeBlock)) {
            if (DEBUG_LOGGING) {
                Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] Target at {} is not a pipe block", pos);
            }
            return false;
        }
        
        BlockEntity targetEntity = world.getBlockEntity(pos);
        if (!(targetEntity instanceof Inventory targetPipe)) {
            if (DEBUG_LOGGING) {
                Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] Target at {} is not an inventory", pos);
            }
            return false;
        }
        
        if (DEBUG_LOGGING) {
            Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] Target pipe at {} has space: {}", pos, hasSpaceForItem(targetPipe, item));
        }
        
        // Check if the target pipe has space (not just if it's empty)
        if (!hasSpaceForItem(targetPipe, item)) {
            if (DEBUG_LOGGING) {
                Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] Target pipe at {} has no space for item", pos);
            }
            return false;
        }
        
        // Insert the item into the target pipe
        ItemStack remaining = insertIntoInventory(targetPipe, item.copy());
        if (!remaining.isEmpty()) {
            if (DEBUG_LOGGING) {
                Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] Could not insert item into pipe at {}", pos);
            }
            return false;
        }
        
        // Set up the target pipe's movement direction
        if (targetEntity instanceof ItemPipeBlockEntity targetItemPipe) {
            targetItemPipe.setMovementDirection(direction);
            targetItemPipe.setSourcePosition(this.pos);
            targetItemPipe.setTransferCooldown(1);
        } else if (targetEntity instanceof SortingPipeBlockEntity targetSortingPipe) {
            targetSortingPipe.setLastInputDirection(direction);
            targetSortingPipe.setTransferCooldown(1);
        }
        
        targetPipe.markDirty();
        
        // Note: Animation is now handled by continuous path system in findBestDirectionWithPathfinding
        
        if (DEBUG_LOGGING) {
            Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] Successfully passed {} to pipe at {}", 
                item.getItem().getName().getString(), pos);
        }
        return true;
    }
    
    // Helper methods (similar to ItemPipe)
    private boolean hasSpaceForItem(Inventory inventory, ItemStack item) {
        if (DEBUG_LOGGING) {
            Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] hasSpaceForItem: checking inventory with {} slots for item {}", 
                inventory.size(), item.getItem().getName().getString());
        }
        
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack slotStack = inventory.getStack(slot);
            
            if (slotStack.isEmpty()) {
                if (DEBUG_LOGGING) {
                    Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] hasSpaceForItem: slot {} is empty, returning true", slot);
                }
                return true;
            }
            
            if (ItemStack.areItemsEqual(slotStack, item)) {
                int maxCount = Math.min(slotStack.getMaxCount(), inventory.getMaxCountPerStack());
                if (slotStack.getCount() < maxCount) {
                    if (DEBUG_LOGGING) {
                        Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] hasSpaceForItem: slot {} has matching item with space ({} < {}), returning true", 
                            slot, slotStack.getCount(), maxCount);
                    }
                    return true;
                }
            }
        }
        
        if (DEBUG_LOGGING) {
            Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] hasSpaceForItem: no space found, returning false");
        }
        return false;
    }
    
    private ItemStack insertIntoInventory(Inventory inventory, ItemStack stack) {
        if (inventory instanceof SidedInventory sidedInventory) {
            for (Direction side : Direction.values()) {
                int[] availableSlots = sidedInventory.getAvailableSlots(side);
                for (int slot : availableSlots) {
                    if (sidedInventory.canInsert(slot, stack, side)) {
                        stack = insertIntoSlot(inventory, stack, slot);
                        if (stack.isEmpty()) break;
                    }
                }
                if (stack.isEmpty()) break;
            }
        } else {
            for (int slot = 0; slot < inventory.size(); slot++) {
                stack = insertIntoSlot(inventory, stack, slot);
                if (stack.isEmpty()) break;
            }
        }
        return stack;
    }
    
    private ItemStack insertIntoSlot(Inventory inventory, ItemStack stack, int slot) {
        ItemStack slotStack = inventory.getStack(slot);
        
        if (slotStack.isEmpty()) {
            inventory.setStack(slot, stack.copy());
            return ItemStack.EMPTY;
        } else if (ItemStack.areItemsEqual(slotStack, stack)) {
            int maxCount = Math.min(slotStack.getMaxCount(), inventory.getMaxCountPerStack());
            int spaceLeft = maxCount - slotStack.getCount();
            
            if (spaceLeft > 0) {
                int transferAmount = Math.min(spaceLeft, stack.getCount());
                slotStack.increment(transferAmount);
                stack.decrement(transferAmount);
            }
        }
        
        return stack;
    }
    
    @Nullable
    private static Inventory getInventoryAt(World world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof Inventory inventory) {
            BlockState state = world.getBlockState(pos);
            if (blockEntity instanceof net.minecraft.block.entity.ChestBlockEntity && 
                state.getBlock() instanceof net.minecraft.block.ChestBlock chestBlock) {
                return net.minecraft.block.ChestBlock.getInventory(chestBlock, state, world, pos, true);
            }
            return inventory;
        }
        return null;
    }
    
    private Direction getOppositeDirection(Direction direction) {
        return direction != null ? direction.getOpposite() : null;
    }

    // Filter inventory access methods
    public DefaultedList<ItemStack> getFilterInventory() {
        return filterInventory;
    }

    public ItemStack getFilterStack(int slot) {
        return filterInventory.get(slot);
    }

    public void setFilterStack(int slot, ItemStack stack) {
        filterInventory.set(slot, stack);
        markDirty();
    }

    // Main inventory implementation (ImplementedInventory)
    @Override
    public DefaultedList<ItemStack> getItems() {
        return inventory;
    }

    // Screen handling
    @Override
    public Text getDisplayName() {
        return Text.translatable("block.circuitmod.sorting_pipe");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new SortingPipeScreenHandler(syncId, playerInventory, this);
    }

    // Setters for external control
    public void setLastInputDirection(Direction direction) {
        this.lastInputDirection = direction;
    }

    public Direction getLastInputDirection() {
        return this.lastInputDirection;
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

    // NBT serialization
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        
        // Save main inventory
        Inventories.writeNbt(nbt, inventory, registries);
        
        // Save filter inventory
        NbtCompound filtersNbt = new NbtCompound();
        Inventories.writeNbt(filtersNbt, filterInventory, registries);
        nbt.put("Filters", filtersNbt);
        
        nbt.putInt("TransferCooldown", transferCooldown);
        nbt.putInt("StuckTimer", stuckTimer);
        
        if (lastInputDirection != null) {
            nbt.putInt("LastInputDir", lastInputDirection.ordinal());
        }
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        
        // Load main inventory
        Inventories.readNbt(nbt, inventory, registries);
        
        // Load filter inventory
        if (nbt.contains("Filters")) {
            NbtCompound filtersNbt = nbt.getCompound("Filters").orElse(new NbtCompound());
            Inventories.readNbt(filtersNbt, filterInventory, registries);
        }
        
        transferCooldown = nbt.getInt("TransferCooldown").orElse(0);
        stuckTimer = nbt.getInt("StuckTimer").orElse(0);
        
        if (nbt.contains("LastInputDir")) {
            int dirOrdinal = nbt.getInt("LastInputDir").orElse(-1);
            if (dirOrdinal >= 0 && dirOrdinal < Direction.values().length) {
                lastInputDirection = Direction.values()[dirOrdinal];
            }
        }
    }

    // Networking
    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        var nbt = new NbtCompound();
        writeNbt(nbt, registries);
        return nbt;
    }
}