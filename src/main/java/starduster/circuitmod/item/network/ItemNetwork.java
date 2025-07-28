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
 * Works with any vanilla Inventory (chests, furnaces, etc.).
 */
public class ItemNetwork {
    private final String networkId;
    private final Set<BlockPos> pipes = new HashSet<>();
    private final Map<BlockPos, Inventory> connectedInventories = new HashMap<>();
    private final Map<BlockPos, Inventory> sourceInventories = new HashMap<>(); // Adjacent to output pipes
    private final Map<BlockPos, Inventory> destinationInventories = new HashMap<>(); // Adjacent to regular pipes only
    private final Map<BlockPos, Set<Direction>> inventoryConnections = new HashMap<>();
    
    // Route caching for performance
    private final Map<String, ItemRoute> routeCache = new HashMap<>();
    private long lastRouteUpdate = 0;
    private static final long ROUTE_CACHE_TIMEOUT = 60000; // 1 minute
    
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
        Circuitmod.LOGGER.info("[NETWORK-ADD] Adding pipe at {} to network {}", pos, networkId);
        
        pipes.add(pos);
        Circuitmod.LOGGER.info("[NETWORK-ADD] Network now has {} pipes", pipes.size());
        
        scanForConnectedInventories(pos);
        Circuitmod.LOGGER.info("[NETWORK-ADD] After scanning, network has {} connected inventories", connectedInventories.size());
        
        invalidateRouteCache();
        
        Circuitmod.LOGGER.info("[ITEM-NETWORK] Added pipe at {} to network {}", pos, networkId);
    }
    
    /**
     * Removes a pipe from this network.
     */
    public void removePipe(BlockPos pos) {
        pipes.remove(pos);
        // Remove any inventories that were only connected through this pipe
        rescanAllInventories();
        invalidateRouteCache();
        
        Circuitmod.LOGGER.info("[ITEM-NETWORK] Removed pipe at {} from network {}", pos, networkId);
    }
    
    /**
     * Scans around a pipe position for connected inventories.
     */
    private void scanForConnectedInventories(BlockPos pipePos) {
        Circuitmod.LOGGER.info("[NETWORK-SCAN] Scanning for inventories around pipe at {}", pipePos);
        
        // Determine what type of pipe this is
        BlockState pipeState = world.getBlockState(pipePos);
        boolean isOutputPipe = pipeState.getBlock() instanceof starduster.circuitmod.block.OutputPipeBlock;
        
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pipePos.offset(direction);
            BlockState blockState = world.getBlockState(neighborPos);
            
            Circuitmod.LOGGER.info("[NETWORK-SCAN] Checking {} direction from {} to {}", direction, pipePos, neighborPos);
            Circuitmod.LOGGER.info("[NETWORK-SCAN] Block state: {}", blockState.getBlock().getName().getString());
            
            // Skip pipes - we only want inventories
            if (blockState.getBlock() instanceof starduster.circuitmod.block.BasePipeBlock) {
                Circuitmod.LOGGER.info("[NETWORK-SCAN] Block at {} is a pipe, skipping", neighborPos);
                continue;
            }
            
            // Use hopper-style inventory detection
            Inventory inventory = getInventoryAt(world, neighborPos, blockState);
            
            if (inventory != null) {
                Circuitmod.LOGGER.info("[NETWORK-SCAN] Found inventory at {}: {}", neighborPos, inventory.getClass().getSimpleName());
                
                // Categorize based on pipe type
                if (isOutputPipe) {
                    // Output pipes create SOURCE inventories (can extract from)
                    sourceInventories.put(neighborPos, inventory);
                    Circuitmod.LOGGER.info("[NETWORK-SCAN] Added {} as SOURCE inventory (adjacent to output pipe)", neighborPos);
                } else {
                    // Regular pipes create DESTINATION inventories (can insert to) 
                    destinationInventories.put(neighborPos, inventory);
                    Circuitmod.LOGGER.info("[NETWORK-SCAN] Added {} as DESTINATION inventory (adjacent to regular pipe)", neighborPos);
                }
                
                // Also add to the general connected inventories for compatibility
                connectedInventories.put(neighborPos, inventory);
                
                // Track connection direction
                inventoryConnections.computeIfAbsent(neighborPos, k -> new HashSet<>()).add(direction.getOpposite());
                
            } else {
                Circuitmod.LOGGER.info("[NETWORK-SCAN] No inventory found at {}", neighborPos);
            }
        }
        
        Circuitmod.LOGGER.info("[NETWORK-SCAN] Finished scanning around {}: {} sources, {} destinations", 
            pipePos, sourceInventories.size(), destinationInventories.size());
    }
    
    /**
     * Gets an inventory at the specified position using the same logic as Minecraft hoppers.
     * This ensures compatibility with all types of inventories (chests, furnaces, etc.)
     */
    @Nullable
    private static Inventory getInventoryAt(World world, BlockPos pos, BlockState state) {
        net.minecraft.block.Block block = state.getBlock();
        
        // 1. Check if block implements InventoryProvider interface (like chests, shulker boxes)
        if (block instanceof net.minecraft.block.InventoryProvider) {
            Circuitmod.LOGGER.info("[NETWORK-SCAN] Block {} implements InventoryProvider", block.getName().getString());
            return ((net.minecraft.block.InventoryProvider)block).getInventory(state, world, pos);
        } 
        // 2. Check if block has entity AND entity implements Inventory
        else if (state.hasBlockEntity()) {
            net.minecraft.block.entity.BlockEntity blockEntity = world.getBlockEntity(pos);
            Circuitmod.LOGGER.info("[NETWORK-SCAN] Block has entity: {}", 
                blockEntity != null ? blockEntity.getClass().getSimpleName() : "null");
            
            if (blockEntity instanceof Inventory inventory) {
                // 3. Special case: Handle double chests properly
                if (inventory instanceof net.minecraft.block.entity.ChestBlockEntity && 
                    block instanceof net.minecraft.block.ChestBlock) {
                    Circuitmod.LOGGER.info("[NETWORK-SCAN] Handling chest block with special logic");
                    return net.minecraft.block.ChestBlock.getInventory((net.minecraft.block.ChestBlock)block, state, world, pos, true);
                }
                
                Circuitmod.LOGGER.info("[NETWORK-SCAN] Block entity is an inventory: {}", inventory.getClass().getSimpleName());
                return inventory;
            } else {
                Circuitmod.LOGGER.info("[NETWORK-SCAN] Block entity is not an inventory");
            }
        } else {
            Circuitmod.LOGGER.info("[NETWORK-SCAN] Block has no block entity");
        }
        
        return null;
    }
    
    /**
     * Rescans all pipes in the network for connected inventories.
     */
    public void rescanAllInventories() {
        Circuitmod.LOGGER.info("[NETWORK-RESCAN] Rescanning all inventories for network {}", networkId);
        
        // Clear existing inventory data
        connectedInventories.clear();
        sourceInventories.clear();
        destinationInventories.clear();
        inventoryConnections.clear();
        
        // Rescan each pipe
        for (BlockPos pipePos : pipes) {
            Circuitmod.LOGGER.info("[NETWORK-RESCAN] Rescanning around pipe at {}", pipePos);
            scanForConnectedInventories(pipePos);
        }
        
        Circuitmod.LOGGER.info("[NETWORK-RESCAN] Rescan complete. Sources: {}, Destinations: {}", 
            sourceInventories.size(), destinationInventories.size());
        
        // Clear route cache since network topology changed
        invalidateRouteCache();
    }
    
    /**
     * Finds a route for the specified item stack from a specific source position.
     * @param itemStack the item to route
     * @param sourcePos the current position of the item
     * @param excludeDestination position to exclude from destinations (e.g., where item was extracted from)
     * @return an ItemRoute or null if no route is available
     */
    public ItemRoute findRoute(ItemStack itemStack, BlockPos sourcePos, BlockPos excludeDestination) {
        String cacheKey = itemStack.getItem().toString() + "_" + itemStack.getCount() + "_from_" + sourcePos;
        if (excludeDestination != null) {
            cacheKey += "_exclude_" + excludeDestination;
        }
        
        // Check cache first
        if (routeCache.containsKey(cacheKey)) {
            ItemRoute cachedRoute = routeCache.get(cacheKey);
            if (cachedRoute.isValid()) {
                return cachedRoute;
            } else {
                routeCache.remove(cacheKey);
            }
        }
        
        // Find available destinations for the item
        List<BlockPos> destinations = findDestinationsForItem(itemStack, excludeDestination);
        if (destinations.isEmpty()) {
            return null;
        }
        
        // Calculate best route using shortest path from the specified source
        ItemRoute bestRoute = null;
        int shortestDistance = Integer.MAX_VALUE;
        
        for (BlockPos destination : destinations) {
            List<BlockPos> path = findPath(sourcePos, destination);
            if (path != null && path.size() < shortestDistance) {
                shortestDistance = path.size();
                bestRoute = new ItemRoute(sourcePos, destination, path);
                Circuitmod.LOGGER.info("[ROUTE-CREATE] Created route: {} -> {} with path: {}", sourcePos, destination, path);
            }
        }
        
        // Cache the result
        if (bestRoute != null) {
            routeCache.put(cacheKey, bestRoute);
            lastRouteUpdate = System.currentTimeMillis();
            Circuitmod.LOGGER.info("[ROUTE-CREATE] Cached route: {}", bestRoute);
        } else {
            Circuitmod.LOGGER.info("[ROUTE-CREATE] No route found from {} to any destination", sourcePos);
        }
        
        return bestRoute;
    }
    
    /**
     * Legacy method - finds a route by searching for sources (kept for compatibility)
     */
    public ItemRoute findRoute(ItemStack itemStack) {
        return findRoute(itemStack, null);
    }

    /**
     * Legacy method - finds a route by searching for sources (kept for compatibility)
     */
    public ItemRoute findRoute(ItemStack itemStack, BlockPos excludeDestination) {
        // Find sources that have this item (legacy behavior)
        List<BlockPos> sources = findSourcesWithItem(itemStack);
        if (sources.isEmpty()) {
            return null;
        }
        
        // Use the first available source
        return findRoute(itemStack, sources.get(0), excludeDestination);
    }
    
    /**
     * Finds all inventories that have the specified item and have an output pipe adjacent to them.
     * Only inventories with output pipes can be sources for item extraction.
     */
    private List<BlockPos> findSourcesWithItem(ItemStack itemStack) {
        List<BlockPos> sources = new ArrayList<>();
        
        Circuitmod.LOGGER.info("[NETWORK-SOURCES] Finding sources with {} from {} source inventories", 
            itemStack.getItem().getName().getString(), sourceInventories.size());
        
        // Only check SOURCE inventories (adjacent to output pipes)
        for (Map.Entry<BlockPos, Inventory> entry : sourceInventories.entrySet()) {
            BlockPos inventoryPos = entry.getKey();
            Inventory inventory = entry.getValue();
            
            // Check if this inventory has an output pipe adjacent to it
            boolean hasOutputPipe = false;
            Set<Direction> connectionDirs = inventoryConnections.getOrDefault(inventoryPos, new HashSet<>());
            
            for (Direction dir : connectionDirs) {
                BlockPos pipePos = inventoryPos.offset(dir);
                BlockState pipeState = world.getBlockState(pipePos);
                if (pipeState.getBlock() instanceof starduster.circuitmod.block.OutputPipeBlock) {
                    hasOutputPipe = true;
                    break;
                }
            }
            
            if (hasOutputPipe) {
                // Check if this inventory contains the item
                for (int slot = 0; slot < inventory.size(); slot++) {
                    ItemStack slotStack = inventory.getStack(slot);
                    if (!slotStack.isEmpty() && ItemStack.areItemsEqual(slotStack, itemStack)) {
                        sources.add(inventoryPos);
                        Circuitmod.LOGGER.info("[NETWORK-SOURCES] Found source with {}: {}", 
                            itemStack.getItem().getName().getString(), inventoryPos);
                        break; // Found the item, no need to check more slots
                    }
                }
            }
        }
        
        Circuitmod.LOGGER.info("[NETWORK-SOURCES] Found {} sources with {}", 
            sources.size(), itemStack.getItem().getName().getString());
        return sources;
    }
    
    /**
     * Finds all destinations that can accept a specific item (excluding specified position).
     */
    private List<BlockPos> findDestinationsForItem(ItemStack itemStack, BlockPos excludeDestination) {
        Circuitmod.LOGGER.info("[NETWORK-DEST] Finding destinations for {} (excluding {})", 
            itemStack.getItem().getName().getString(), excludeDestination);
        
        List<BlockPos> destinations = new ArrayList<>();
        
        Circuitmod.LOGGER.info("[NETWORK-DEST] Checking destination inventories: {}", destinationInventories.size());
        
        // Only check DESTINATION inventories (adjacent to regular pipes)
        // Never check source inventories (adjacent to output pipes) as destinations
        for (Map.Entry<BlockPos, Inventory> entry : destinationInventories.entrySet()) {
            BlockPos inventoryPos = entry.getKey();
            Inventory inventory = entry.getValue();
            
            Circuitmod.LOGGER.info("[NETWORK-DEST] Checking destination inventory at {}", inventoryPos);
            
            // Skip if this is the excluded destination
            if (excludeDestination != null && inventoryPos.equals(excludeDestination)) {
                Circuitmod.LOGGER.info("[NETWORK-DEST] Skipping excluded destination: {}", inventoryPos);
                continue;
            }
            
            // Check all possible connection directions for this inventory
            Set<Direction> connectionDirs = inventoryConnections.getOrDefault(inventoryPos, new HashSet<>());
            for (Direction direction : connectionDirs) {
                Circuitmod.LOGGER.info("[NETWORK-DEST] Testing direction {} for inventory {}", direction, inventoryPos);
                
                if (canInsertItem(inventory, itemStack, direction)) {
                    destinations.add(inventoryPos);
                    Circuitmod.LOGGER.info("[NETWORK-DEST] Can insert {} into {} from direction {}", 
                        itemStack.getItem().getName().getString(), inventoryPos, direction);
                    break; // Found one valid direction, that's enough
                } else {
                    Circuitmod.LOGGER.info("[NETWORK-DEST] Cannot insert {} into {} from direction {}", 
                        itemStack.getItem().getName().getString(), inventoryPos, direction);
                }
            }
        }
        
        Circuitmod.LOGGER.info("[NETWORK-DEST] Found {} destinations for {}", 
            destinations.size(), itemStack.getItem().getName().getString());
        return destinations;
    }
    
    /**
     * Checks if an inventory has an extractable item of the specified type.
     */
    private boolean hasExtractableItem(Inventory inventory, ItemStack targetStack, Direction side) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stackInSlot = inventory.getStack(slot);
            if (ItemStack.areItemsAndComponentsEqual(stackInSlot, targetStack) && !stackInSlot.isEmpty()) {
                // Check if we can extract from this side (basic implementation)
                return true;
            }
        }
        return false;
    }
    
    /**
     * Checks if an inventory can accept the specified item on the given side.
     * This checks if there's space for AT LEAST ONE item, not necessarily the full stack.
     */
    private boolean canInsertItem(Inventory inventory, ItemStack itemStack, Direction side) {
        // Handle SidedInventory (furnaces, hoppers, etc.)
        if (inventory instanceof net.minecraft.inventory.SidedInventory sidedInventory) {
            int[] availableSlots = sidedInventory.getAvailableSlots(side);
            if (availableSlots.length == 0) {
                return false; // No slots available on this side
            }
            
            for (int slot : availableSlots) {
                if (sidedInventory.canInsert(slot, itemStack, side)) {
                    ItemStack stackInSlot = sidedInventory.getStack(slot);
                    
                    if (stackInSlot.isEmpty()) {
                        return true; // Empty slot available
                    }
                    
                    // Check if same item type and has space for at least one more item
                    if (ItemStack.areItemsAndComponentsEqual(stackInSlot, itemStack)) {
                        int maxCount = Math.min(stackInSlot.getMaxCount(), inventory.getMaxCountPerStack());
                        if (stackInSlot.getCount() < maxCount) {
                            return true; // Can add at least one more item
                        }
                    }
                }
            }
            return false;
        }
        
        // Handle regular Inventory
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stackInSlot = inventory.getStack(slot);
            
            if (stackInSlot.isEmpty()) {
                return true; // Empty slot available
            }
            
            // Check if same item type and has space for at least one more item
            if (ItemStack.areItemsAndComponentsEqual(stackInSlot, itemStack)) {
                int maxCount = Math.min(stackInSlot.getMaxCount(), inventory.getMaxCountPerStack());
                if (stackInSlot.getCount() < maxCount) {
                    return true; // Can add at least one more item
                }
            }
        }
        return false;
    }
    
    /**
     * Finds a path between two positions using the pipe network.
     */
    public List<BlockPos> findPath(BlockPos start, BlockPos end) {
        Circuitmod.LOGGER.info("[PATHFIND] Finding path from {} to {}", start, end);
        
        // Find closest pipe to start
        BlockPos startPipe = findClosestPipe(start);
        BlockPos endPipe = findClosestPipe(end);
        
        Circuitmod.LOGGER.info("[PATHFIND] Closest pipes: start={}, end={}", startPipe, endPipe);
        
        if (startPipe == null || endPipe == null) {
            Circuitmod.LOGGER.info("[PATHFIND] No path found - missing start or end pipe");
            return null;
        }
        
        // Use BFS to find shortest path through pipes
        Queue<BlockPos> queue = new LinkedList<>();
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Set<BlockPos> visited = new HashSet<>();
        
        queue.offer(startPipe);
        visited.add(startPipe);
        
        Circuitmod.LOGGER.info("[PATHFIND] Starting BFS from {} to {}", startPipe, endPipe);
        
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            
            if (current.equals(endPipe)) {
                // Reconstruct path
                List<BlockPos> path = new ArrayList<>();
                path.add(end); // Add final destination
                
                BlockPos step = current;
                while (step != null) {
                    path.add(0, step);
                    step = cameFrom.get(step);
                }
                
                // Only add start if it's different from the first pipe position
                if (!start.equals(startPipe)) {
                    path.add(0, start); // Add initial source
                }
                
                Circuitmod.LOGGER.info("[PATHFIND] Found path with {} steps: {}", path.size(), path);
                return path;
            }
            
            // Check adjacent pipes
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = current.offset(direction);
                if (pipes.contains(neighbor) && !visited.contains(neighbor)) {
                    queue.offer(neighbor);
                    visited.add(neighbor);
                    cameFrom.put(neighbor, current);
                }
            }
        }
        
        Circuitmod.LOGGER.info("[PATHFIND] No path found through pipe network");
        return null; // No path found
    }
    
    /**
     * Finds the closest pipe to a given position.
     */
    private BlockPos findClosestPipe(BlockPos pos) {
        Circuitmod.LOGGER.info("[PATHFIND-CLOSEST] Finding closest pipe to {} from {} pipes", pos, pipes.size());
        
        BlockPos closest = null;
        double minDistance = Double.MAX_VALUE;
        
        for (BlockPos pipe : pipes) {
            double distance = pos.getSquaredDistance(pipe);
            if (distance < minDistance) {
                minDistance = distance;
                closest = pipe;
            }
        }
        
        Circuitmod.LOGGER.info("[PATHFIND-CLOSEST] Closest pipe to {} is {} (distance: {})", pos, closest, minDistance);
        return closest;
    }
    
    /**
     * Debug method to print network topology.
     */
    public void debugNetworkTopology() {
        Circuitmod.LOGGER.info("[NETWORK-TOPOLOGY] Network {} contains:", networkId);
        Circuitmod.LOGGER.info("[NETWORK-TOPOLOGY] - Pipes ({}): {}", pipes.size(), pipes);
        Circuitmod.LOGGER.info("[NETWORK-TOPOLOGY] - Source inventories ({}): {}", sourceInventories.size(), sourceInventories.keySet());
        Circuitmod.LOGGER.info("[NETWORK-TOPOLOGY] - Destination inventories ({}): {}", destinationInventories.size(), destinationInventories.keySet());
        Circuitmod.LOGGER.info("[NETWORK-TOPOLOGY] - Inventory connections: {}", inventoryConnections);
    }
    
    /**
     * Checks if a route is still valid.
     */
    private boolean isRouteValid(ItemRoute route) {
        // Check if source and destination still exist and are connected
        return connectedInventories.containsKey(route.getSource()) &&
               connectedInventories.containsKey(route.getDestination()) &&
               route.getPath().stream().allMatch(pipes::contains);
    }
    
    /**
     * Invalidates the route cache.
     */
    private void invalidateRouteCache() {
        routeCache.clear();
        lastRouteUpdate = System.currentTimeMillis();
    }
    
    /**
     * Gets all pipes in this network.
     */
    public Set<BlockPos> getPipes() {
        return new HashSet<>(pipes);
    }
    
    /**
     * Gets all connected inventories.
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
        this.pipes.addAll(other.pipes);
        this.connectedInventories.putAll(other.connectedInventories);
        this.inventoryConnections.putAll(other.inventoryConnections);
        invalidateRouteCache();
        
        Circuitmod.LOGGER.info("[ITEM-NETWORK] Merged network {} into {}", other.networkId, this.networkId);
    }

    /**
     * Finds alternative destinations for an item when the primary destination fails.
     * Returns destinations sorted by distance (closest first).
     */
    public List<BlockPos> findAlternativeDestinations(ItemStack itemStack, BlockPos currentPos, BlockPos excludeDestination, BlockPos failedDestination) {
        List<BlockPos> allDestinations = findDestinationsForItem(itemStack, excludeDestination);
        List<BlockPos> alternatives = new ArrayList<>();
        
        for (BlockPos dest : allDestinations) {
            if (!dest.equals(failedDestination)) {
                alternatives.add(dest);
            }
        }
        
        // Sort by distance from current position (closest first)
        alternatives.sort((dest1, dest2) -> {
            double dist1 = Math.sqrt(currentPos.getSquaredDistance(dest1));
            double dist2 = Math.sqrt(currentPos.getSquaredDistance(dest2));
            return Double.compare(dist1, dist2);
        });
        
        return alternatives;
    }
    
    /**
     * Attempts to find a route to any available destination, trying alternatives if the primary fails.
     */
    public ItemRoute findRouteWithFallback(ItemStack itemStack, BlockPos currentPos, BlockPos excludeDestination) {
        Circuitmod.LOGGER.info("[NETWORK-ROUTE] Finding route for {} from {} (excluding {})", 
            itemStack.getItem().getName().getString(), currentPos, excludeDestination);
            
        // Try the primary route first
        ItemRoute primaryRoute = findRoute(itemStack, currentPos, excludeDestination);
        if (primaryRoute != null) {
            Circuitmod.LOGGER.info("[NETWORK-ROUTE] Found primary route: {} -> {}", 
                primaryRoute.getSource(), primaryRoute.getDestination());
            return primaryRoute;
        }
        
        Circuitmod.LOGGER.info("[NETWORK-ROUTE] No primary route found, trying alternatives");
        
        // If no primary route, try to find any alternative destination
        List<BlockPos> alternatives = findAlternativeDestinations(itemStack, currentPos, excludeDestination, null);
        Circuitmod.LOGGER.info("[NETWORK-ROUTE] Found {} alternative destinations", alternatives.size());
        
        for (BlockPos altDestination : alternatives) {
            Circuitmod.LOGGER.info("[NETWORK-ROUTE] Trying alternative destination: {}", altDestination);
            List<BlockPos> path = findPath(currentPos, altDestination);
            if (path != null) {
                Circuitmod.LOGGER.info("[NETWORK-ROUTE] Found path to alternative: {} steps", path.size());
                return new ItemRoute(currentPos, altDestination, path);
            } else {
                Circuitmod.LOGGER.info("[NETWORK-ROUTE] No path to alternative destination: {}", altDestination);
            }
        }
        
        Circuitmod.LOGGER.info("[NETWORK-ROUTE] No route found to any destination");
        return null; // No route found to any destination
    }
    
    /**
     * Finds a route excluding a specific failed destination.
     * Used when a transfer attempt fails and we need to find an alternative.
     */
    public ItemRoute findRouteExcludingFailed(ItemStack itemStack, BlockPos currentPos, BlockPos excludeDestination, BlockPos failedDestination) {
        Circuitmod.LOGGER.info("[NETWORK-FALLBACK] Finding alternative route for {} from {} (excluding {} and failed {})", 
            itemStack.getItem().getName().getString(), currentPos, excludeDestination, failedDestination);
        
        // Find alternative destinations, excluding both the original exclusion and the failed destination
        List<BlockPos> alternatives = findAlternativeDestinations(itemStack, currentPos, excludeDestination, failedDestination);
        Circuitmod.LOGGER.info("[NETWORK-FALLBACK] Found {} alternative destinations", alternatives.size());
        
        for (BlockPos altDestination : alternatives) {
            Circuitmod.LOGGER.info("[NETWORK-FALLBACK] Trying alternative destination: {}", altDestination);
            List<BlockPos> path = findPath(currentPos, altDestination);
            if (path != null) {
                Circuitmod.LOGGER.info("[NETWORK-FALLBACK] Found path to alternative: {} steps", path.size());
                return new ItemRoute(currentPos, altDestination, path);
            } else {
                Circuitmod.LOGGER.info("[NETWORK-FALLBACK] No path to alternative destination: {}", altDestination);
            }
        }
        
        Circuitmod.LOGGER.info("[NETWORK-FALLBACK] No alternative route found");
        return null;
    }

    /**
     * Forces a complete rescan of all inventories in the network.
     * Useful for debugging or when blocks are placed after pipes.
     */
    public void forceRescanAllInventories() {
        Circuitmod.LOGGER.info("[NETWORK-FORCE-RESCAN] Forcing complete rescan of network {}", networkId);
        connectedInventories.clear();
        sourceInventories.clear();
        destinationInventories.clear();
        inventoryConnections.clear();
        
        for (BlockPos pipePos : pipes) {
            Circuitmod.LOGGER.info("[NETWORK-FORCE-RESCAN] Rescanning around pipe at {}", pipePos);
            scanForConnectedInventories(pipePos);
        }
        
        Circuitmod.LOGGER.info("[NETWORK-FORCE-RESCAN] Rescan complete. Sources: {}, Destinations: {}", 
            sourceInventories.size(), destinationInventories.size());
        Circuitmod.LOGGER.info("[NETWORK-FORCE-RESCAN] - Source inventories: {}", sourceInventories.keySet());
        Circuitmod.LOGGER.info("[NETWORK-FORCE-RESCAN] - Destination inventories: {}", destinationInventories.keySet());
    }
    
    /**
     * Gets all source inventories (adjacent to output pipes).
     */
    public Map<BlockPos, Inventory> getSourceInventories() {
        return sourceInventories;
    }
    
    /**
     * Gets all destination inventories (adjacent to regular pipes only).
     */
    public Map<BlockPos, Inventory> getDestinationInventories() {
        return destinationInventories;
    }
} 