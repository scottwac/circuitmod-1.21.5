package starduster.circuitmod.item.network;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.entity.ItemPipeBlockEntity;

import java.util.*;
import net.minecraft.block.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Manages a network of item pipes and connected inventories.
 * Works like the electrical network - simple flood-fill discovery and direct communication.
 */
public class ItemNetwork {
    private final String networkId;
    private final Set<BlockPos> pipes = new HashSet<>();
    private final Map<BlockPos, Inventory> connectedInventories = new HashMap<>();
    private final Map<BlockPos, Set<Direction>> inventoryConnections = new HashMap<>();
    
    // Simple source/destination tracking
    private final List<BlockPos> sources = new ArrayList<>(); // Output pipes
    private final List<BlockPos> destinations = new ArrayList<>(); // Regular pipes
    
    // Debug logging control - set to true only when debugging
    private static final boolean DEBUG_LOGGING = true;
    
    private final World world;
    
    public ItemNetwork(World world) {
        this.networkId = UUID.randomUUID().toString();
        this.world = world;
    }
    
    public String getNetworkId() {
        return networkId;
    }
    
    /**
     * Adds a pipe to this network.
     */
    public void addPipe(BlockPos pos) {
        if (pipes.contains(pos)) {
            return; // Already in network
        }
        
        pipes.add(pos);
        ItemNetworkManager.pipeToNetwork.put(pos, this);
        
        // Determine if this is a source or destination based on pipe type
        BlockState pipeState = world.getBlockState(pos);
        if (pipeState.getBlock() instanceof starduster.circuitmod.block.OutputPipeBlock) {
            sources.add(pos);
            Circuitmod.LOGGER.info("[ITEM-NETWORK] Added source pipe at {} to network {}", pos, networkId);
        } else {
            destinations.add(pos);
            Circuitmod.LOGGER.info("[ITEM-NETWORK] Added destination pipe at {} to network {}", pos, networkId);
        }
        
        // Scan for connected inventories
        scanForConnectedInventories(pos);
        
        // Log network topology
        Circuitmod.LOGGER.info("[ITEM-NETWORK] Network {} now has {} pipes, {} sources, {} destinations, {} connected inventories", 
            networkId, pipes.size(), sources.size(), destinations.size(), connectedInventories.size());
    }
    
    /**
     * Removes a pipe from this network.
     */
    public void removePipe(BlockPos pos) {
        if (pipes.remove(pos)) {
            ItemNetworkManager.pipeToNetwork.remove(pos);
            sources.remove(pos);
            destinations.remove(pos);
            
            // Only log if debug logging is enabled
            if (DEBUG_LOGGING && world != null && world.getTime() % 200 == 0) {
                Circuitmod.LOGGER.info("[ITEM-NETWORK] Removed pipe at {} from network {}", pos, networkId);
            }
        }
        
        // Rescan all inventories
        rescanAllInventories();
    }
    
    /**
     * Scans around a pipe position for connected inventories.
     */
    private void scanForConnectedInventories(BlockPos pipePos) {
        Circuitmod.LOGGER.info("[ITEM-NETWORK] Scanning for connected inventories around pipe at {}", pipePos);
        
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pipePos.offset(direction);
            BlockState neighborState = world.getBlockState(neighborPos);
            Inventory inventory = getInventoryAt(world, neighborPos, neighborState);
            
            if (inventory != null) {
                connectedInventories.put(neighborPos, inventory);
                inventoryConnections.computeIfAbsent(neighborPos, k -> new HashSet<>()).add(direction.getOpposite());
                
                Circuitmod.LOGGER.info("[ITEM-NETWORK] Found connected inventory {} at {} for pipe at {}", 
                    inventory.getClass().getSimpleName(), neighborPos, pipePos);
            } else {
                Circuitmod.LOGGER.info("[ITEM-NETWORK] No inventory found at {} for pipe at {}", neighborPos, pipePos);
            }
        }
    }
    
    /**
     * Gets an inventory at the specified position, or null if none exists.
     */
    @Nullable
    private static Inventory getInventoryAt(World world, BlockPos pos, BlockState state) {
        if (world.isClient()) {
            return null;
        }
        
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof Inventory) {
            return (Inventory) blockEntity;
        }
        
        return null;
    }
    
    /**
     * Rescans all pipes in the network for connected inventories.
     */
    private void rescanAllInventories() {
        connectedInventories.clear();
        inventoryConnections.clear();
        
        for (BlockPos pipePos : pipes) {
            scanForConnectedInventories(pipePos);
        }
    }
    
    /**
     * Finds a destination for an item using simple logic.
     * Like the electrical network, we just find any destination that can accept the item.
     */
    public BlockPos findDestinationForItem(ItemStack itemStack, BlockPos excludePos) {
        // Get all possible destinations: both pipe destinations and connected inventories
        List<BlockPos> allDestinations = new ArrayList<>(destinations);
        allDestinations.addAll(connectedInventories.keySet());
        
        // Remove excluded position
        allDestinations.remove(excludePos);
        
        // Debug logging
        Circuitmod.LOGGER.info("[ITEM-NETWORK] Finding destination for {} (exclude: {}), available destinations: {}", 
            itemStack.getItem().getName().getString(), excludePos, allDestinations.size());
        
        // Log all available destinations
        for (BlockPos destPos : allDestinations) {
            Inventory inventory = connectedInventories.get(destPos);
            Circuitmod.LOGGER.info("[ITEM-NETWORK] Available destination: {} (inventory: {})", 
                destPos, inventory != null ? inventory.getClass().getSimpleName() : "null");
        }
        
        // Try each destination to see if it can accept the item
        for (BlockPos destPos : allDestinations) {
            Inventory inventory = connectedInventories.get(destPos);
            if (inventory != null && canInsertItem(inventory, itemStack)) {
                Circuitmod.LOGGER.info("[ITEM-NETWORK] Found destination {} for item {}", destPos, itemStack.getItem().getName().getString());
                return destPos;
            } else {
                Circuitmod.LOGGER.info("[ITEM-NETWORK] Destination {} cannot accept item {} (inventory: {}, canInsert: {})", 
                    destPos, itemStack.getItem().getName().getString(), 
                    inventory != null ? inventory.getClass().getSimpleName() : "null",
                    inventory != null ? canInsertItem(inventory, itemStack) : false);
            }
        }
        
        Circuitmod.LOGGER.info("[ITEM-NETWORK] No destination found for item {}", itemStack.getItem().getName().getString());
        return null;
    }
    
    /**
     * Finds all destinations for an item (for compatibility with old code).
     */
    public List<BlockPos> findDestinationsForItem(ItemStack itemStack, BlockPos excludePos) {
        List<BlockPos> allDestinations = new ArrayList<>(destinations);
        
        // Remove excluded position
        allDestinations.remove(excludePos);
        
        List<BlockPos> validDestinations = new ArrayList<>();
        
        // Try each destination to see if it can accept the item
        for (BlockPos destPos : allDestinations) {
            Inventory inventory = connectedInventories.get(destPos);
            if (inventory != null && canInsertItem(inventory, itemStack)) {
                validDestinations.add(destPos);
            }
        }
        
        return validDestinations;
    }
    
    /**
     * Gets all source inventories (for compatibility with old code).
     */
    public Map<BlockPos, Inventory> getSourceInventories() {
        Map<BlockPos, Inventory> sourceInventories = new HashMap<>();
        for (BlockPos sourcePos : sources) {
            Inventory inventory = connectedInventories.get(sourcePos);
            if (inventory != null) {
                sourceInventories.put(sourcePos, inventory);
            }
        }
        return sourceInventories;
    }
    
    /**
     * Gets all destination inventories (for compatibility with old code).
     */
    public Map<BlockPos, Inventory> getDestinationInventories() {
        Map<BlockPos, Inventory> destinationInventories = new HashMap<>();
        for (BlockPos destPos : destinations) {
            Inventory inventory = connectedInventories.get(destPos);
            if (inventory != null) {
                destinationInventories.put(destPos, inventory);
            }
        }
        return destinationInventories;
    }
    
    /**
     * Checks if a destination has space for an item (for compatibility with old code).
     */
    public boolean destinationHasSpace(BlockPos destination, ItemStack itemStack) {
        Inventory inventory = connectedInventories.get(destination);
        if (inventory == null) {
            return false;
        }
        return canInsertItem(inventory, itemStack);
    }
    
    /**
     * Finds a path between two positions (for compatibility with old code).
     * In the simplified network, we just return a direct path.
     */
    public List<BlockPos> findPath(BlockPos start, BlockPos end) {
        // In the simplified network, we just return a direct path
        List<BlockPos> path = new ArrayList<>();
        path.add(start);
        path.add(end);
        return path;
    }
    
    /**
     * Forces a complete rescan of all inventories (for compatibility with old code).
     */
    public void forceRescanAllInventories() {
        rescanAllInventories();
    }
    
    /**
     * Monitors inventory changes (for compatibility with old code).
     */
    public void monitorInventoryChanges() {
        // In the simplified network, we don't need complex monitoring
        // The network will automatically find destinations when needed
    }
    
    /**
     * Checks if an inventory can accept an item.
     */
    private boolean canInsertItem(Inventory inventory, ItemStack itemStack) {
        Circuitmod.LOGGER.info("[ITEM-NETWORK] Checking if inventory {} can accept item {}", 
            inventory.getClass().getSimpleName(), itemStack.getItem().getName().getString());
        
        // Check if the inventory can accept items
        if (inventory instanceof net.minecraft.inventory.SidedInventory sidedInventory) {
            // Check all sides for available slots
            for (Direction side : Direction.values()) {
                int[] availableSlots = sidedInventory.getAvailableSlots(side);
                if (availableSlots.length > 0) {
                    // Check if any slot can accept the item
                    for (int slot : availableSlots) {
                        if (sidedInventory.canInsert(slot, itemStack, side)) {
                            Circuitmod.LOGGER.info("[ITEM-NETWORK] Inventory {} can accept item {} via side {}", 
                                inventory.getClass().getSimpleName(), itemStack.getItem().getName().getString(), side);
                            return true;
                        }
                    }
                }
            }
            Circuitmod.LOGGER.info("[ITEM-NETWORK] SidedInventory {} cannot accept item {}", 
                inventory.getClass().getSimpleName(), itemStack.getItem().getName().getString());
            return false;
        } else {
            // For regular inventories, check if there's any empty slot or matching slot
            for (int slot = 0; slot < inventory.size(); slot++) {
                ItemStack slotStack = inventory.getStack(slot);
                if (slotStack.isEmpty() || (ItemStack.areItemsEqual(slotStack, itemStack) && slotStack.getCount() < slotStack.getMaxCount())) {
                    Circuitmod.LOGGER.info("[ITEM-NETWORK] Regular inventory {} can accept item {} in slot {}", 
                        inventory.getClass().getSimpleName(), itemStack.getItem().getName().getString(), slot);
                    return true;
                }
            }
            Circuitmod.LOGGER.info("[ITEM-NETWORK] Regular inventory {} cannot accept item {}", 
                inventory.getClass().getSimpleName(), itemStack.getItem().getName().getString());
            return false;
        }
    }
    
    /**
     * Gets all pipes in this network.
     */
    public Set<BlockPos> getPipes() {
        return new HashSet<>(pipes);
    }
    
    /**
     * Gets all connected inventories in this network.
     */
    public Map<BlockPos, Inventory> getConnectedInventories() {
        return new HashMap<>(connectedInventories);
    }
    
    /**
     * Gets the number of pipes in this network.
     */
    public int getSize() {
        return pipes.size();
    }
    
    /**
     * Checks if this network is empty.
     */
    public boolean isEmpty() {
        return pipes.isEmpty();
    }
    
    /**
     * Merges another network into this one.
     */
    public void merge(ItemNetwork other) {
        pipes.addAll(other.pipes);
        connectedInventories.putAll(other.connectedInventories);
        inventoryConnections.putAll(other.inventoryConnections);
        sources.addAll(other.sources);
        destinations.addAll(other.destinations);
        
        // Update pipe-to-network mapping
        for (BlockPos pipe : other.pipes) {
            ItemNetworkManager.pipeToNetwork.put(pipe, this);
        }
    }
    
    /**
     * Debug method to print network topology.
     */
    public void debugNetworkTopology() {
        if (!DEBUG_LOGGING) return;
        
        Circuitmod.LOGGER.info("[NETWORK-TOPOLOGY] Network {} contains:", networkId);
        Circuitmod.LOGGER.info("[NETWORK-TOPOLOGY] - Pipes ({}): {}", pipes.size(), pipes);
        Circuitmod.LOGGER.info("[NETWORK-TOPOLOGY] - Sources ({}): {}", sources.size(), sources);
        Circuitmod.LOGGER.info("[NETWORK-TOPOLOGY] - Destinations ({}): {}", destinations.size(), destinations);
        Circuitmod.LOGGER.info("[NETWORK-TOPOLOGY] - Connected inventories ({}): {}", connectedInventories.size(), connectedInventories.keySet());
    }
} 