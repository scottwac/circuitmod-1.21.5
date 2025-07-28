package starduster.circuitmod.block.entity;

import net.minecraft.block.Block;
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
import starduster.circuitmod.block.BasePipeBlock;
import starduster.circuitmod.block.ItemPipeBlock;
import starduster.circuitmod.item.network.ItemNetwork;
import starduster.circuitmod.item.network.ItemNetworkManager;
import starduster.circuitmod.item.network.ItemRoute;
import starduster.circuitmod.network.PipeNetworkAnimator;
import starduster.circuitmod.screen.SortingPipeScreenHandler;
import starduster.circuitmod.util.ImplementedInventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SortingPipeBlockEntity extends BlockEntity implements NamedScreenHandlerFactory, ImplementedInventory {
    // Main item storage (1 slot for current item being processed)
    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(1, ItemStack.EMPTY);
    
    // Filter inventory (6 slots for directional filters: N, E, S, W, Up, Down)
    private final DefaultedList<ItemStack> filterInventory = DefaultedList.ofSize(6, ItemStack.EMPTY);
    
    private int transferCooldown = 0;
    Direction lastInputDirection = null; // Package-private for access from other pipe classes
    private int lastAnimationTick = -1; // Track when we last sent an animation to prevent duplicates
    // sourceExclusion removed - network routing handles source exclusion properly

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
        Circuitmod.LOGGER.info("[SORTING-PIPE-CONSTRUCTOR] Created sorting pipe block entity at {}", pos);
    }
    
    /**
     * Called when the pipe is placed to connect to the item network.
     */
    public void onPlaced() {
        if (world != null && !world.isClient) {
            Circuitmod.LOGGER.info("[SORTING-PIPE-PLACE] Connecting sorting pipe to network at {}", pos);
            
            // Schedule the connection for the next tick to ensure block entity is fully initialized
            world.getServer().execute(() -> {
                ItemNetworkManager.connectPipe(world, pos);
                
                // Verify connection
                ItemNetwork network = ItemNetworkManager.getNetworkForPipe(pos);
                if (network != null) {
                    Circuitmod.LOGGER.info("[SORTING-PIPE-PLACE] Successfully connected to network {}", network.getNetworkId());
                } else {
                    Circuitmod.LOGGER.error("[SORTING-PIPE-PLACE] FAILED to connect to network!");
                }
            });
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

    public static void tick(World world, BlockPos pos, BlockState state, SortingPipeBlockEntity blockEntity) {
        Circuitmod.LOGGER.info("[SORTING-PIPE-TICK-START] Tick called for sorting pipe at {}", pos);
        
        if (world.isClient) {
            return;
        }
        
        // Check if pipe is in a network
        ItemNetwork currentNetwork = ItemNetworkManager.getNetworkForPipe(pos);
        if (currentNetwork == null) {
            Circuitmod.LOGGER.info("[SORTING-PIPE-TICK] Pipe at {} not in network, attempting to reconnect", pos);
            ItemNetworkManager.connectPipe(world, pos);
            currentNetwork = ItemNetworkManager.getNetworkForPipe(pos);
            if (currentNetwork != null) {
                Circuitmod.LOGGER.info("[SORTING-PIPE-TICK] Successfully reconnected to network {}", currentNetwork.getNetworkId());
            }
        }

        // sourceExclusion timeout mechanism removed - no longer needed

        // Log every 20 ticks (1 second) to verify ticking
        if (world.getTime() % 20 == 0) {
            Circuitmod.LOGGER.info("[SORTING-PIPE-TICK] Sorting pipe at {} is ticking, has item: {}, in network: {}", 
                pos, !blockEntity.isEmpty(), currentNetwork != null);
        }

        // Check for new unconnected inventories every 5 ticks (was 10) for faster discovery
        if (world.getTime() % 5 == 0) {
            checkForUnconnectedInventories(world, pos, blockEntity);
        }
        
        // Force network refresh if item has been stuck for too long
        ItemNetwork network = ItemNetworkManager.getNetworkForPipe(pos);
        if (network != null && blockEntity.transferCooldown <= -60) { // Been stuck for 3+ seconds
            Circuitmod.LOGGER.info("[SORTING-PIPE-FORCE-REFRESH] Item stuck for too long, forcing network refresh at {}", pos);
            network.forceRescanAllInventories();
            blockEntity.transferCooldown = 0; // Reset cooldown to try again
        }
        
        // TEMPORARY DEBUG: Force refresh every 50 ticks (2.5 seconds) to catch new chests and space changes
        if (network != null && world.getTime() % 50 == 0) {
            Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] Forcing periodic network refresh at {}", pos);
            network.forceRescanAllInventories();
            network.monitorInventoryChanges(); // Monitor for space changes in closer destinations
        }

        // Decrease transfer cooldown
        if (blockEntity.transferCooldown > 0) {
            blockEntity.transferCooldown--;
            if (!blockEntity.isEmpty()) {
                Circuitmod.LOGGER.info("[SORTING-PIPE-TICK] On cooldown, has item {} at {}", 
                    blockEntity.getStack(0).getItem().getName().getString(), pos);
            }
            return;
        }

        // Log start of tick
        if (!blockEntity.isEmpty()) {
            Circuitmod.LOGGER.info("[SORTING-PIPE-TICK] Processing item {} at {}", 
                blockEntity.getStack(0).getItem().getName().getString(), pos);
        }

        // Try to extract items from adjacent inventories into this pipe
        if (blockEntity.isEmpty()) {
            extractFromAdjacentInventories(world, pos, blockEntity);
        }
        
        // Process items in the pipe
        if (!blockEntity.isEmpty()) {
            Circuitmod.LOGGER.info("[SORTING-PIPE-ROUTING] Attempting to route {} from {}", 
                blockEntity.getStack(0).getItem().getName().getString(), pos);
            
            // TEMPORARY DEBUG: Force refresh every 100 ticks (5 seconds) to catch new chests
            if (network != null && world.getTime() % 100 == 0) {
                Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] Forcing periodic network refresh at {}", pos);
                network.forceRescanAllInventories();
            }
            
            if (routeItemThroughNetwork(world, pos, blockEntity)) {
                blockEntity.transferCooldown = 3; // Match faster throughput (was 8, now 3)
                Circuitmod.LOGGER.info("[SORTING-PIPE-ROUTING] Successfully routed item from {}", pos);
            } else {
                Circuitmod.LOGGER.info("[SORTING-PIPE-ROUTING] Failed to route item from {}", pos);
            }
        }
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
        
        nbt.putInt("TransferCooldown", this.transferCooldown);
        
        if (this.lastInputDirection != null) {
            nbt.putInt("LastInputDir", this.lastInputDirection.ordinal());
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
        
        this.transferCooldown = nbt.getInt("TransferCooldown", -1);
        
        if (nbt.contains("LastInputDir")) {
            int dirOrdinal = nbt.getInt("LastInputDir", 0);
            if (dirOrdinal >= 0 && dirOrdinal < Direction.values().length) {
                this.lastInputDirection = Direction.values()[dirOrdinal];
            } else {
                this.lastInputDirection = null;
            }
        } else {
            this.lastInputDirection = null;
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

    // Getters and setters for pipe logic compatibility
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
    
    // Sorting pipe logic methods
    private static void extractFromAdjacentInventories(World world, BlockPos pos, SortingPipeBlockEntity blockEntity) {
        // Try to extract from inventories in all directions except where we last came from
        for (Direction direction : Direction.values()) {
            if (direction == blockEntity.lastInputDirection) {
                continue; // Don't extract from where we just came from
            }
            
            BlockPos adjacentPos = pos.offset(direction);
            if (world.getBlockEntity(adjacentPos) instanceof net.minecraft.inventory.Inventory inventory) {
                // Skip if it's another pipe to avoid conflicts
                if (world.getBlockState(adjacentPos).getBlock() instanceof starduster.circuitmod.block.BasePipeBlock) {
                    continue;
                }
                
                // Try to extract an item from this inventory
                for (int i = 0; i < inventory.size(); i++) {
                    ItemStack stack = inventory.getStack(i);
                    if (!stack.isEmpty() && canExtract(inventory, stack, i, direction.getOpposite())) {
                        // Extract one item
                        ItemStack extracted = stack.copy();
                        extracted.setCount(1);
                        
                        blockEntity.setStack(0, extracted);
                        stack.decrement(1);
                        inventory.markDirty();
                        blockEntity.lastInputDirection = direction;
                        // sourceExclusion removed - network routing handles source prevention properly
                        blockEntity.markDirty();
                        return; // Only extract one item at a time
                    }
                }
            }
        }
    }
    
    /**
     * Check if an inventory has space for a specific item.
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
     * Route an item through the network while respecting sorting filters.
     */
    private static boolean routeItemThroughNetwork(World world, BlockPos pos, SortingPipeBlockEntity blockEntity) {
        ItemStack currentStack = blockEntity.getStack(0);
        if (currentStack.isEmpty()) {
            return false;
        }
        
        // Get the network for this pipe
        ItemNetwork network = ItemNetworkManager.getNetworkForPipe(pos);
        if (network == null) {
            return false;
        }
        
        // FILTER FIRST: Get allowed directions for this item based on filters
        List<Direction> allowedDirections = getAllowedDirectionsForItem(blockEntity, currentStack);
        
        Circuitmod.LOGGER.info("[SORTING-PIPE-FILTER] Item {} at {} - allowed directions: {}", 
            currentStack.getItem().getName().getString(), pos, allowedDirections);
        
        // Use network routing to find destinations, but filter by allowed directions
        List<BlockPos> allDestinations = network.findDestinationsForItem(currentStack, null);
        List<BlockPos> filteredDestinations = new ArrayList<>();
        
        Circuitmod.LOGGER.info("[SORTING-PIPE-FILTER] Network found {} total destinations", allDestinations.size());
        
        // For each destination, check if it's reachable via any of our allowed directions
        for (BlockPos destination : allDestinations) {
            // Check if this destination is reachable via any of our allowed directions
            boolean isReachable = isDestinationReachableViaAllowedDirections(world, pos, destination, allowedDirections, network);
            
            if (isReachable) {
                filteredDestinations.add(destination);
                Circuitmod.LOGGER.info("[SORTING-PIPE-FILTER] Added filtered destination: {} (reachable via allowed directions)", destination);
            } else {
                Circuitmod.LOGGER.info("[SORTING-PIPE-FILTER] Destination {} not reachable via allowed directions", destination);
            }
        }
        
        if (filteredDestinations.isEmpty()) {
            Circuitmod.LOGGER.info("[SORTING-PIPE-FILTER] No destinations reachable via allowed directions");
            return false;
        }
        
        // Find the best route to a filtered destination
        ItemRoute bestRoute = null;
        int shortestDistance = Integer.MAX_VALUE;
        
        for (BlockPos destination : filteredDestinations) {
            List<BlockPos> path = network.findPath(pos, destination);
            if (path != null && path.size() < shortestDistance) {
                shortestDistance = path.size();
                bestRoute = new ItemRoute(pos, destination, path);
                Circuitmod.LOGGER.info("[SORTING-PIPE-FILTER] Found route to {} via allowed directions, distance: {}", 
                    destination, path.size());
            }
        }
        
        if (bestRoute != null) {
            // Get the next step in the route
            BlockPos nextStep = bestRoute.getNextPosition(pos);
            if (nextStep != null) {
                Circuitmod.LOGGER.info("[SORTING-PIPE-FILTER] Routing to {} via next step {}", 
                    bestRoute.getDestination(), nextStep);
                
                // Transfer to the next step
                if (nextStep.equals(bestRoute.getDestination())) {
                    // Next step is the destination, transfer directly to inventory
                    return transferToInventoryAt(world, nextStep, blockEntity);
                } else {
                    // Next step is a pipe, transfer to pipe
                    return transferToPipeAt(world, nextStep, blockEntity);
                }
            }
        }
        
        Circuitmod.LOGGER.info("[SORTING-PIPE-FILTER] No valid route found for item {}", currentStack.getItem().getName().getString());
        return false;
    }
    
    /**
     * Checks if a destination is reachable via any of the allowed directions.
     * This uses network pathfinding to verify the path actually goes through allowed directions.
     */
    private static boolean isDestinationReachableViaAllowedDirections(World world, BlockPos sortingPipePos, BlockPos destination, 
                                                                   List<Direction> allowedDirections, ItemNetwork network) {
        // Get the path from sorting pipe to destination
        List<BlockPos> path = network.findPath(sortingPipePos, destination);
        if (path == null || path.size() < 2) {
            return false; // No path or path is too short
        }
        
        // Check if the first step in the path goes in an allowed direction
        BlockPos firstStep = path.get(1); // First step after the sorting pipe
        Direction pathDirection = getDirectionTowards(sortingPipePos, firstStep);
        
        Circuitmod.LOGGER.info("[SORTING-PIPE-PATH] Path to {} starts with direction: {}", destination, pathDirection);
        Circuitmod.LOGGER.info("[SORTING-PIPE-PATH] Allowed directions: {}", allowedDirections);
        
        // Check if the path direction is in our allowed directions
        boolean isAllowed = allowedDirections.contains(pathDirection);
        
        if (isAllowed) {
            Circuitmod.LOGGER.info("[SORTING-PIPE-PATH] Destination {} is reachable via allowed direction {}", destination, pathDirection);
        } else {
            Circuitmod.LOGGER.info("[SORTING-PIPE-PATH] Destination {} is NOT reachable - path direction {} not in allowed directions {}", 
                destination, pathDirection, allowedDirections);
        }
        
        return isAllowed;
    }
    
    /**
     * Transfers item to a specific inventory location.
     */
    private static boolean transferToInventoryAt(World world, BlockPos inventoryPos, SortingPipeBlockEntity blockEntity) {
        if (world.getBlockEntity(inventoryPos) instanceof Inventory inventory) {
            ItemStack currentStack = blockEntity.getStack(0);
            Direction transferDirection = getDirectionTowards(blockEntity.getPos(), inventoryPos);
            
            if (transferDirection != null) {
                ItemStack remaining = transferToInventory(blockEntity, inventory, currentStack.copy(), transferDirection.getOpposite());
                
                if (remaining.isEmpty() || remaining.getCount() < currentStack.getCount()) {
                    // Successfully transferred at least part of the item
                    blockEntity.setStack(0, remaining);
                    blockEntity.markDirty();
                    
                    // Send animation for item leaving the pipe
                    if (world instanceof ServerWorld serverWorld) {
                        blockEntity.sendAnimationIfAllowed(serverWorld, currentStack, blockEntity.getPos(), inventoryPos);
                    }
                    
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Transfers item to a pipe at the specified position.
     */
    private static boolean transferToPipeAt(World world, BlockPos targetPipePos, SortingPipeBlockEntity blockEntity) {
        if (world.getBlockEntity(targetPipePos) instanceof Inventory targetPipe) {
            if (!targetPipe.isEmpty()) {
                return false; // Target pipe is full
            }
            
            ItemStack currentStack = blockEntity.getStack(0);
            Direction transferDirection = getDirectionTowards(blockEntity.getPos(), targetPipePos);
            
            // Transfer the item
            ItemStack transferredItem = blockEntity.removeStack(0);
            targetPipe.setStack(0, transferredItem);
            
            // Set animation and direction info based on target pipe type
            if (targetPipe instanceof ItemPipeBlockEntity itemPipe) {
                itemPipe.lastInputDirection = transferDirection;
                itemPipe.setTransferCooldown(3); // Improved throughput (was 8, now 3)
                // sourceExclusion removed - network routing handles loop prevention properly
            } else if (targetPipe instanceof SortingPipeBlockEntity sortingPipe) {
                sortingPipe.setLastInputDirection(transferDirection);
                sortingPipe.setTransferCooldown(3); // Improved throughput (was 8, now 3)
                // sourceExclusion removed - network routing handles loop prevention properly
            }
            
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
        return false;
    }
    
    /**
     * Get all directions that are allowed for a specific item based on filters.
     */
    private static List<Direction> getAllowedDirectionsForItem(SortingPipeBlockEntity blockEntity, ItemStack item) {
        List<Direction> allowedDirections = new ArrayList<>();
        
        Circuitmod.LOGGER.info("[SORTING-PIPE-FILTER-LOGIC] Checking filters for item: {}", item.getItem().getName().getString());
        
        // First, check if this item has a specific filter set anywhere
        boolean hasSpecificFilter = false;
        for (int i = 0; i < 6; i++) {
            ItemStack filterStack = blockEntity.getFilterStack(i);
            if (!filterStack.isEmpty() && ItemStack.areItemsAndComponentsEqual(item, filterStack)) {
                hasSpecificFilter = true;
                Direction direction = DIRECTION_ORDER[i];
                allowedDirections.add(direction);
                Circuitmod.LOGGER.info("[SORTING-PIPE-FILTER-LOGIC] Found specific filter for {} in direction {} (slot {})", 
                    item.getItem().getName().getString(), direction, i);
            }
        }
        
        // If the item has a specific filter, only use those directions
        if (hasSpecificFilter) {
            Circuitmod.LOGGER.info("[SORTING-PIPE-FILTER-LOGIC] Item has specific filters - allowed directions: {}", allowedDirections);
            return allowedDirections;
        }
        
        // If no specific filter, item can go in any direction that doesn't have a filter
        for (int i = 0; i < 6; i++) {
            ItemStack filterStack = blockEntity.getFilterStack(i);
            if (filterStack.isEmpty()) {
                Direction direction = DIRECTION_ORDER[i];
                allowedDirections.add(direction);
                Circuitmod.LOGGER.info("[SORTING-PIPE-FILTER-LOGIC] No filter in slot {} (direction {}) - allowing item", i, direction);
            } else {
                Direction direction = DIRECTION_ORDER[i];
                Circuitmod.LOGGER.info("[SORTING-PIPE-FILTER-LOGIC] Filter in slot {} (direction {}) for {} - blocking item", 
                    i, direction, filterStack.getItem().getName().getString());
            }
        }
        
        // If no directions are available (all directions have filters for other items), 
        // allow all directions as fallback (shouldn't normally happen)
        if (allowedDirections.isEmpty()) {
            allowedDirections.addAll(Arrays.asList(Direction.values()));
            Circuitmod.LOGGER.info("[SORTING-PIPE-FILTER-LOGIC] No directions available, allowing all directions as fallback");
        }
        
        Circuitmod.LOGGER.info("[SORTING-PIPE-FILTER-LOGIC] Final allowed directions for {}: {}", 
            item.getItem().getName().getString(), allowedDirections);
        return allowedDirections;
    }
    
    /**
     * Check if any filters are configured in this sorting pipe.
     */
    private static boolean hasNoFiltersSet(SortingPipeBlockEntity blockEntity) {
        for (int i = 0; i < 6; i++) {
            if (!blockEntity.getFilterStack(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Check if an item is allowed to go in a specific direction based on filters.
     */
    private static boolean isDirectionAllowedByFilter(SortingPipeBlockEntity blockEntity, ItemStack item, Direction direction) {
        // Find the filter slot for this direction
        int slotIndex = -1;
        for (int i = 0; i < 6; i++) {
            if (DIRECTION_ORDER[i] == direction) {
                slotIndex = i;
                break;
            }
        }
        
        if (slotIndex == -1) {
            return true; // Invalid direction, allow by default
        }
        
        ItemStack filterStack = blockEntity.getFilterStack(slotIndex);
        
        if (filterStack.isEmpty()) {
            // No filter set - check if any other direction has a filter for this item
            for (int i = 0; i < 6; i++) {
                if (i != slotIndex) {
                    ItemStack otherFilter = blockEntity.getFilterStack(i);
                    if (!otherFilter.isEmpty() && ItemStack.areItemsAndComponentsEqual(item, otherFilter)) {
                        return false; // This item should go in a different direction
                    }
                }
            }
            return true; // No conflicting filters, allow
        } else {
            // Filter is set - only allow if item matches
            return ItemStack.areItemsAndComponentsEqual(item, filterStack);
        }
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
     * Transfer item to an adjacent block (pipe or inventory).
     */
    private static boolean transferToAdjacentBlock(World world, BlockPos fromPos, BlockPos toPos, 
                                                   SortingPipeBlockEntity fromPipe, ItemStack stack) {
        // Try to transfer to another pipe first
        if (world.getBlockState(toPos).getBlock() instanceof BasePipeBlock) {
            if (world.getBlockEntity(toPos) instanceof Inventory targetPipe) {
                if (targetPipe.isEmpty()) {
                    // Transfer the stack to the target pipe
                    targetPipe.setStack(0, stack.copy());
                    
                    // Update target pipe's input direction
                    if (targetPipe instanceof ItemPipeBlockEntity itemPipe) {
                        Direction direction = getDirectionTowards(fromPos, toPos);
                        if (direction != null) {
                            itemPipe.lastInputDirection = direction.getOpposite();
                            itemPipe.setTransferCooldown(3); // Improved throughput (was 8, now 3)
                        }
                    } else if (targetPipe instanceof SortingPipeBlockEntity sortingPipe) {
                        Direction direction = getDirectionTowards(fromPos, toPos);
                        if (direction != null) {
                            sortingPipe.setLastInputDirection(direction.getOpposite());
                            sortingPipe.setTransferCooldown(3); // Improved throughput (was 8, now 3)
                        }
                    }
                    
                    // Send animation
                    if (world instanceof ServerWorld serverWorld) {
                        fromPipe.sendAnimationIfAllowed(serverWorld, stack, fromPos, toPos);
                    }
                    
                    targetPipe.markDirty();
                    return true;
                }
            }
        }
        
        // Try to transfer to inventory
        if (world.getBlockEntity(toPos) instanceof Inventory targetInventory) {
            Direction direction = getDirectionTowards(fromPos, toPos);
            if (direction != null) {
                ItemStack remaining = transferToInventory(fromPipe, targetInventory, stack.copy(), direction.getOpposite());
                
                if (remaining.isEmpty() || remaining.getCount() < stack.getCount()) {
                    // Successfully transferred at least part of the item
                    fromPipe.setStack(0, remaining);
                    
                    // Send animation
                    if (world instanceof ServerWorld serverWorld) {
                        fromPipe.sendAnimationIfAllowed(serverWorld, stack, fromPos, toPos);
                    }
                    
                    fromPipe.markDirty();
                    return true;
                }
            }
        }
        
        return false;
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
     * Send animation to clients if allowed.
     */
    private void sendAnimationIfAllowed(ServerWorld world, ItemStack stack, BlockPos from, BlockPos to) {
        int currentTick = (int) world.getTime();
        
        // Only send animation if at least 3 ticks have passed since the last one
        if (currentTick - this.lastAnimationTick >= 3) {
            // Send animation starting immediately
            PipeNetworkAnimator.sendMoveAnimation(world, stack.copy(), from, to, 5); // Use 5 ticks for animation
            this.lastAnimationTick = currentTick;
        }
    }
    
    private static boolean canExtract(net.minecraft.inventory.Inventory inventory, ItemStack stack, int slot, Direction side) {
        if (inventory instanceof net.minecraft.inventory.SidedInventory sidedInventory) {
            return sidedInventory.canExtract(slot, stack, side);
        }
        return true; // Regular inventories allow extraction from any side
    }
    
    private static ItemStack transferToInventory(net.minecraft.inventory.Inventory from, net.minecraft.inventory.Inventory to, ItemStack stack, Direction side) {
        if (to instanceof net.minecraft.inventory.SidedInventory sidedInventory) {
            int[] slots = sidedInventory.getAvailableSlots(side);
            
            for (int slot : slots) {
                if (!stack.isEmpty()) {
                    stack = tryInsertIntoSlot(from, to, stack, slot, side);
                }
            }
        } else {
            for (int i = 0; i < to.size() && !stack.isEmpty(); i++) {
                stack = tryInsertIntoSlot(from, to, stack, i, side);
            }
        }
        
        return stack;
    }
    
    private static ItemStack tryInsertIntoSlot(net.minecraft.inventory.Inventory from, net.minecraft.inventory.Inventory to, ItemStack stack, int slot, Direction side) {
        ItemStack slotStack = to.getStack(slot);
        
        if (canInsert(to, stack, slot, side)) {
            if (slotStack.isEmpty()) {
                to.setStack(slot, stack);
                return ItemStack.EMPTY;
            } else if (ItemStack.areItemsEqual(slotStack, stack)) {
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
    
    private static boolean canInsert(net.minecraft.inventory.Inventory inventory, ItemStack stack, int slot, Direction side) {
        if (inventory instanceof net.minecraft.inventory.SidedInventory sidedInventory) {
            return sidedInventory.canInsert(slot, stack, side);
        }
        return inventory.isValid(slot, stack);
    }
    
    /**
     * Checks for new unconnected inventories and pipes around the pipe and triggers a network rescan if needed.
     */
    private static void checkForUnconnectedInventories(World world, BlockPos pos, SortingPipeBlockEntity blockEntity) {
        ItemNetwork network = ItemNetworkManager.getNetworkForPipe(pos);
        if (network == null) {
            return;
        }
        
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
                    Circuitmod.LOGGER.info("[SORTING-PIPE-DISCOVERY] Found new pipe connection at {} with different network {}", neighborPos, neighborNetwork.getNetworkId());
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
                Circuitmod.LOGGER.info("[SORTING-PIPE-DISCOVERY] Found new inventory at {} next to sorting pipe at {}", neighborPos, pos);
                foundNewConnection = true;
                break; // Only need to find one to trigger rescan
            }
        }
        
        // If we found a new connection, trigger a network rescan
        if (foundNewConnection) {
            Circuitmod.LOGGER.info("[SORTING-PIPE-DISCOVERY] Triggering network rescan due to new connection discovery");
            network.forceRescanAllInventories();
        }
    }
}