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
        // Only log occasionally to prevent spam
        if (world != null && world.getTime() % 200 == 0) {
            Circuitmod.LOGGER.info("[SORTING-PIPE-CONSTRUCTOR] Created sorting pipe block entity at {}", pos);
        }
    }
    
    /**
     * Called when the pipe is placed to connect to the item network.
     */
    public void onPlaced() {
        if (world != null && !world.isClient()) {
            // Only log occasionally to prevent spam
            if (world.getTime() % 200 == 0) {
                Circuitmod.LOGGER.info("[SORTING-PIPE-PLACE] Connecting sorting pipe to network at {}", pos);
            }
            
            // Schedule the connection for the next tick to ensure block entity is fully initialized
            world.getServer().execute(() -> {
                ItemNetworkManager.connectPipe(world, pos);
                
                // Verify connection
                ItemNetwork network = ItemNetworkManager.getNetworkForPipe(pos);
                if (network != null) {
                    // Only log occasionally to prevent spam
                    if (world.getTime() % 200 == 0) {
                        Circuitmod.LOGGER.info("[SORTING-PIPE-PLACE] Successfully connected to network {}", network.getNetworkId());
                    }
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
        if (world.isClient()) {
            return;
        }
        
        // Debug logging control - set to true only when debugging
        final boolean DEBUG_LOGGING = false;
        
        // Only log if debug logging is enabled
        if (DEBUG_LOGGING && world.getTime() % 200 == 0) {
            Circuitmod.LOGGER.info("[SORTING-PIPE-TICK-START] Tick called for sorting pipe at {}", pos);
        }
        
        // Check if we're in a network, if not try to reconnect
        ItemNetwork currentNetwork = ItemNetworkManager.getNetworkForPipe(pos);
        if (currentNetwork == null) {
            // Only log if debug logging is enabled
            if (DEBUG_LOGGING && world.getTime() % 200 == 0) {
                Circuitmod.LOGGER.info("[SORTING-PIPE-TICK] Pipe at {} not in network, attempting to reconnect", pos);
            }
            ItemNetworkManager.connectPipe(world, pos);
            currentNetwork = ItemNetworkManager.getNetworkForPipe(pos);
            if (currentNetwork != null && DEBUG_LOGGING && world.getTime() % 200 == 0) {
                Circuitmod.LOGGER.info("[SORTING-PIPE-TICK] Successfully reconnected to network {}", currentNetwork.getNetworkId());
            }
        }
        
        // Only proceed with item processing if we have an item to process
        if (blockEntity.getStack(0).isEmpty()) {
            return;
        }
        
        // Only log if debug logging is enabled
        if (DEBUG_LOGGING && world.getTime() % 200 == 0) {
            Circuitmod.LOGGER.info("[SORTING-PIPE-TICK] Sorting pipe at {} is ticking, has item: {}, in network: {}",
                pos, blockEntity.getStack(0).getItem().getName().getString(), 
                currentNetwork != null ? currentNetwork.getNetworkId() : "NO NETWORK");
        }

        // Check for new unconnected inventories every 20 ticks (reduced from 5) for better performance
        if (world.getTime() % 20 == 0) {
            checkForUnconnectedInventories(world, pos, blockEntity);
        }
        
        // Force network refresh if item has been stuck for too long
        ItemNetwork network = ItemNetworkManager.getNetworkForPipe(pos);
        if (network != null && blockEntity.transferCooldown <= -60) { // Been stuck for 3+ seconds
            // Only log if debug logging is enabled
            if (DEBUG_LOGGING && world.getTime() % 200 == 0) {
                Circuitmod.LOGGER.info("[SORTING-PIPE-FORCE-REFRESH] Item stuck for too long, forcing network refresh at {}", pos);
            }
            network.forceRescanAllInventories();
            blockEntity.transferCooldown = 0; // Reset cooldown to try again
        }
        
        // TEMPORARY DEBUG: Force refresh every 100 ticks (5 seconds) to catch new chests and space changes - reduced frequency
        if (network != null && world.getTime() % 100 == 0) { // Reduced from 50 to 100 ticks
            // Only log if debug logging is enabled
            if (DEBUG_LOGGING && world.getTime() % 200 == 0) {
                Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] Forcing periodic network refresh at {}", pos);
            }
            network.forceRescanAllInventories();
            network.monitorInventoryChanges(); // Monitor for space changes in closer destinations
        }

        // Decrease transfer cooldown
        if (blockEntity.transferCooldown > 0) {
            blockEntity.transferCooldown--;
            // Only log if debug logging is enabled
            if (DEBUG_LOGGING && world.getTime() % 200 == 0 && !blockEntity.isEmpty()) {
                Circuitmod.LOGGER.info("[SORTING-PIPE-TICK] On cooldown, has item {} at {}", 
                    blockEntity.getStack(0).getItem().getName().getString(), pos);
            }
            return;
        }

        // Log start of tick - only occasionally
        if (!blockEntity.isEmpty() && DEBUG_LOGGING && world.getTime() % 200 == 0) {
            Circuitmod.LOGGER.info("[SORTING-PIPE-TICK] Processing item {} at {}", 
                blockEntity.getStack(0).getItem().getName().getString(), pos);
        }

        // Try to extract items from adjacent inventories into this pipe
        if (blockEntity.isEmpty()) {
            extractFromAdjacentInventories(world, pos, blockEntity);
        }
        
        // Process items in the pipe
        if (!blockEntity.isEmpty()) {
            // Only log if debug logging is enabled
            if (DEBUG_LOGGING && world.getTime() % 200 == 0) {
                Circuitmod.LOGGER.info("[SORTING-PIPE-ROUTING] Attempting to route {} from {}", 
                    blockEntity.getStack(0).getItem().getName().getString(), pos);
            }
            
            // TEMPORARY DEBUG: Force refresh every 200 ticks (10 seconds) to catch new chests - reduced frequency
            if (network != null && world.getTime() % 200 == 0) { // Reduced from 100 to 200 ticks
                // Only log if debug logging is enabled
                if (DEBUG_LOGGING && world.getTime() % 400 == 0) {
                    Circuitmod.LOGGER.info("[SORTING-PIPE-DEBUG] Forcing periodic network refresh at {}", pos);
                }
                network.forceRescanAllInventories();
            }
            
            // Try to route the item through the network
            boolean didWork = routeItemThroughNetwork(world, pos, blockEntity);
            
            if (didWork) {
                // Only log if debug logging is enabled
                if (DEBUG_LOGGING && world.getTime() % 200 == 0) {
                    Circuitmod.LOGGER.info("[SORTING-PIPE-TICK] Successfully routed item from {}", pos);
                }
                blockEntity.setTransferCooldown(3); // Match faster throughput
            } else {
                // Item couldn't be routed, increment stuck counter
                blockEntity.transferCooldown--;
                // Only log if debug logging is enabled
                if (DEBUG_LOGGING && world.getTime() % 200 == 0) {
                    Circuitmod.LOGGER.info("[SORTING-PIPE-TICK] Failed to route item from {}, item remains in pipe", pos);
                }
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
            Circuitmod.LOGGER.info("[SORTING-PIPE-ROUTING] No network found for pipe at {}", pos);
            return false;
        }
        
        // FILTER FIRST: Get allowed directions for this item based on filters
        List<Direction> allowedDirections = getAllowedDirectionsForItem(blockEntity, currentStack);
        
        Circuitmod.LOGGER.info("[SORTING-PIPE-FILTER] Item {} at {} - allowed directions: {}", 
            currentStack.getItem().getName().getString(), pos, allowedDirections);
        
        // Find ALL destinations for this item type
        List<BlockPos> allDestinations = network.findDestinationsForItem(currentStack, null);
        
        Circuitmod.LOGGER.info("[SORTING-PIPE-FILTER] Network found {} total destinations", allDestinations.size());
        
        // For each allowed direction, check if we can reach any destination
        ItemRoute bestRoute = null;
        int shortestDistance = Integer.MAX_VALUE;
        
        // First, try to find routes that go through allowed directions AND have space
        for (Direction allowedDir : allowedDirections) {
            BlockPos firstStep = pos.offset(allowedDir);
            
            Circuitmod.LOGGER.info("[SORTING-PIPE-ROUTING] Checking allowed direction {} -> first step {}", allowedDir, firstStep);
            
            // Check if there's a pipe in this direction
            if (world.getBlockState(firstStep).getBlock() instanceof BasePipeBlock) {
                Circuitmod.LOGGER.info("[SORTING-PIPE-ROUTING] Found pipe at first step {}", firstStep);
                
                // For each destination, see if we can reach it through this direction
                for (BlockPos destination : allDestinations) {
                    List<BlockPos> path = network.findPath(pos, destination);
                    
                    Circuitmod.LOGGER.info("[SORTING-PIPE-ROUTING] Path to {}: {}", destination, path);
                    
                    if (path != null && path.size() >= 2) {
                        // Check if this path goes through our allowed direction
                        BlockPos pathFirstStep = path.get(1);
                        Circuitmod.LOGGER.info("[SORTING-PIPE-ROUTING] Path first step: {}, expected: {}", pathFirstStep, firstStep);
                        
                        if (pathFirstStep.equals(firstStep)) {
                            // This path uses an allowed direction
                            // Check if the destination actually has space
                            if (network.destinationHasSpace(destination, currentStack)) {
                                if (path.size() < shortestDistance) {
                                    shortestDistance = path.size();
                                    bestRoute = new ItemRoute(pos, destination, path);
                                    Circuitmod.LOGGER.info("[SORTING-PIPE-FILTER] Found valid route through {} to {} with space, distance: {}", 
                                        allowedDir, destination, path.size());
                                }
                            } else {
                                Circuitmod.LOGGER.info("[SORTING-PIPE-ROUTING] Destination {} has no space for item", destination);
                            }
                        } else {
                            Circuitmod.LOGGER.info("[SORTING-PIPE-ROUTING] Path does not go through allowed direction");
                        }
                    } else {
                        Circuitmod.LOGGER.info("[SORTING-PIPE-ROUTING] No valid path found to destination {}", destination);
                    }
                }
            } else {
                Circuitmod.LOGGER.info("[SORTING-PIPE-ROUTING] No pipe found at first step {}", firstStep);
            }
        }
        
        // If no route with space was found, try to find ANY route through allowed directions
        // This allows items to move towards destinations even if the immediate destination is full
        if (bestRoute == null) {
            Circuitmod.LOGGER.info("[SORTING-PIPE-FALLBACK] No destinations with space found, looking for ANY route through allowed directions");
            
            for (Direction allowedDir : allowedDirections) {
                BlockPos firstStep = pos.offset(allowedDir);
                
                if (world.getBlockState(firstStep).getBlock() instanceof BasePipeBlock) {
                    // Just try to move in the allowed direction if there's a pipe there
                    List<BlockPos> simplePath = new ArrayList<>();
                    simplePath.add(pos);
                    simplePath.add(firstStep);
                    
                    // Find the closest destination in this direction (even if full)
                    BlockPos closestDest = null;
                    int closestDistance = Integer.MAX_VALUE;
                    
                    for (BlockPos destination : allDestinations) {
                        List<BlockPos> path = network.findPath(pos, destination);
                        if (path != null && path.size() >= 2 && path.get(1).equals(firstStep)) {
                            if (path.size() < closestDistance) {
                                closestDistance = path.size();
                                closestDest = destination;
                            }
                        }
                    }
                    
                    if (closestDest != null) {
                        List<BlockPos> path = network.findPath(pos, closestDest);
                        bestRoute = new ItemRoute(pos, closestDest, path);
                        Circuitmod.LOGGER.info("[SORTING-PIPE-FALLBACK] Found route through {} towards {} (may be full)", 
                            allowedDir, closestDest);
                        break;
                    }
                }
            }
        }
        
        if (bestRoute != null) {
            // Get the next step in the route
            BlockPos nextStep = bestRoute.getNextPosition(pos);
            Circuitmod.LOGGER.info("[SORTING-PIPE-ROUTING] Best route found, next step: {}", nextStep);
            
            if (nextStep != null) {
                Circuitmod.LOGGER.info("[SORTING-PIPE-ROUTING] Routing to {} via next step {}", 
                    bestRoute.getDestination(), nextStep);
                
                // Transfer to the next step
                if (nextStep.equals(bestRoute.getDestination())) {
                    // Next step is the destination, transfer directly to inventory
                    Circuitmod.LOGGER.info("[SORTING-PIPE-ROUTING] Next step is destination, transferring to inventory");
                    return transferToInventoryAt(world, nextStep, blockEntity);
                } else {
                    // Next step is a pipe, transfer to pipe
                    Circuitmod.LOGGER.info("[SORTING-PIPE-ROUTING] Next step is pipe, transferring to pipe");
                    return transferToPipeAt(world, nextStep, blockEntity);
                }
            } else {
                Circuitmod.LOGGER.info("[SORTING-PIPE-ROUTING] Could not get next step from route");
            }
        }
        
        Circuitmod.LOGGER.info("[SORTING-PIPE-FILTER] No valid route found for item {}", currentStack.getItem().getName().getString());
        return false;
    }
    
    /**
     * Transfers item to a specific inventory location.
     */
    private static boolean transferToInventoryAt(World world, BlockPos inventoryPos, SortingPipeBlockEntity blockEntity) {
        Circuitmod.LOGGER.info("[SORTING-PIPE-INVENTORY-TRANSFER] Attempting to transfer to inventory at {}", inventoryPos);
        
        if (world.getBlockEntity(inventoryPos) instanceof Inventory inventory) {
            ItemStack currentStack = blockEntity.getStack(0);
            Direction transferDirection = getDirectionTowards(blockEntity.getPos(), inventoryPos);
            
            Circuitmod.LOGGER.info("[SORTING-PIPE-INVENTORY-TRANSFER] Transfer direction: {}, inventory type: {}", 
                transferDirection, inventory.getClass().getSimpleName());
            
            if (transferDirection != null) {
                ItemStack remaining = transferToInventory(blockEntity, inventory, currentStack.copy(), transferDirection.getOpposite());
                
                Circuitmod.LOGGER.info("[SORTING-PIPE-INVENTORY-TRANSFER] Transfer result: {} remaining from {} original", 
                    remaining.getCount(), currentStack.getCount());
                
                if (remaining.isEmpty() || remaining.getCount() < currentStack.getCount()) {
                    // Successfully transferred at least part of the item
                    blockEntity.setStack(0, remaining);
                    blockEntity.markDirty();
                    
                    // Send animation for item leaving the pipe
                    if (world instanceof ServerWorld serverWorld) {
                        blockEntity.sendAnimationIfAllowed(serverWorld, currentStack, blockEntity.getPos(), inventoryPos);
                    }
                    
                    Circuitmod.LOGGER.info("[SORTING-PIPE-INVENTORY-TRANSFER] Successfully transferred {} items to {}", 
                        currentStack.getCount() - remaining.getCount(), inventoryPos);
                    return true;
                } else {
                    Circuitmod.LOGGER.info("[SORTING-PIPE-INVENTORY-TRANSFER] Failed to transfer any items to {} - inventory appears to be full", inventoryPos);
                }
            } else {
                Circuitmod.LOGGER.info("[SORTING-PIPE-INVENTORY-TRANSFER] Could not determine transfer direction");
            }
        } else {
            Circuitmod.LOGGER.info("[SORTING-PIPE-INVENTORY-TRANSFER] No inventory found at {}", inventoryPos);
        }
        return false;
    }
    
    /**
     * Transfers item to a pipe at the specified position.
     */
    private static boolean transferToPipeAt(World world, BlockPos targetPipePos, SortingPipeBlockEntity blockEntity) {
        Circuitmod.LOGGER.info("[SORTING-PIPE-PIPE-TRANSFER] Attempting to transfer to pipe at {}", targetPipePos);
        
        BlockEntity targetEntity = world.getBlockEntity(targetPipePos);
        
        if (targetEntity instanceof ItemPipeBlockEntity targetPipe) {
            Circuitmod.LOGGER.info("[SORTING-PIPE-PIPE-TRANSFER] Target is ItemPipeBlockEntity, isEmpty: {}", targetPipe.isEmpty());
            
            if (!targetPipe.isEmpty()) {
                Circuitmod.LOGGER.info("[SORTING-PIPE-PIPE-TRANSFER] Target pipe is full, cannot transfer");
                return false; // Target pipe is full
            }
            
            ItemStack currentStack = blockEntity.getStack(0);
            Direction transferDirection = getDirectionTowards(blockEntity.getPos(), targetPipePos);
            
            Circuitmod.LOGGER.info("[SORTING-PIPE-PIPE-TRANSFER] Transfer direction: {}", transferDirection);
            
            // Transfer the item
            ItemStack transferredItem = blockEntity.removeStack(0);
            targetPipe.setStack(0, transferredItem);
            
            // Set the source exclusion to prevent backflow
            targetPipe.sourceExclusion = blockEntity.getPos();
            
            // Set animation and direction info
            targetPipe.lastInputDirection = transferDirection;
            targetPipe.setTransferCooldown(1);
            targetPipe.markDirty();
            
            // Clear source pipe
            blockEntity.markDirty();
            
            // Send animation
            if (world instanceof ServerWorld serverWorld) {
                blockEntity.sendAnimationIfAllowed(serverWorld, currentStack, blockEntity.getPos(), targetPipePos);
            }
            
            Circuitmod.LOGGER.info("[SORTING-PIPE-PIPE-TRANSFER] Successfully transferred {} to pipe at {}", 
                transferredItem.getItem().getName().getString(), targetPipePos);
            
            return true;
        }
        else if (targetEntity instanceof SortingPipeBlockEntity targetSortingPipe) {
            Circuitmod.LOGGER.info("[SORTING-PIPE-PIPE-TRANSFER] Target is SortingPipeBlockEntity, isEmpty: {}", targetSortingPipe.isEmpty());
            
            if (!targetSortingPipe.isEmpty()) {
                Circuitmod.LOGGER.info("[SORTING-PIPE-PIPE-TRANSFER] Target sorting pipe is full, cannot transfer");
                return false; // Target pipe is full
            }
            
            ItemStack currentStack = blockEntity.getStack(0);
            Direction transferDirection = getDirectionTowards(blockEntity.getPos(), targetPipePos);
            
            Circuitmod.LOGGER.info("[SORTING-PIPE-PIPE-TRANSFER] Transfer direction: {}", transferDirection);
            
            // Transfer the item
            ItemStack transferredItem = blockEntity.removeStack(0);
            targetSortingPipe.setStack(0, transferredItem);
            
            // Set animation and direction info
            targetSortingPipe.setLastInputDirection(transferDirection);
            targetSortingPipe.setTransferCooldown(1);
            targetSortingPipe.markDirty();
            
            // Clear source pipe
            blockEntity.markDirty();
            
            // Send animation
            if (world instanceof ServerWorld serverWorld) {
                blockEntity.sendAnimationIfAllowed(serverWorld, currentStack, blockEntity.getPos(), targetPipePos);
            }
            
            Circuitmod.LOGGER.info("[SORTING-PIPE-PIPE-TRANSFER] Successfully transferred {} to sorting pipe at {}", 
                transferredItem.getItem().getName().getString(), targetPipePos);
            
            return true;
        }
        
        Circuitmod.LOGGER.info("[SORTING-PIPE-PIPE-TRANSFER] Target at {} is not a pipe: {}", targetPipePos, 
            targetEntity != null ? targetEntity.getClass().getSimpleName() : "null");
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
                            itemPipe.setTransferCooldown(1); // Faster throughput for better item flow
                        }
                    } else if (targetPipe instanceof SortingPipeBlockEntity sortingPipe) {
                        Direction direction = getDirectionTowards(fromPos, toPos);
                        if (direction != null) {
                            sortingPipe.setLastInputDirection(direction.getOpposite());
                            sortingPipe.setTransferCooldown(1); // Faster throughput for better item flow
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
        
        if (diff.getX() > 0) return Direction.EAST;
        if (diff.getX() < 0) return Direction.WEST;
        if (diff.getY() > 0) return Direction.UP;
        if (diff.getY() < 0) return Direction.DOWN;
        if (diff.getZ() > 0) return Direction.SOUTH;
        if (diff.getZ() < 0) return Direction.NORTH;
        
        return Direction.UP; // Default fallback
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