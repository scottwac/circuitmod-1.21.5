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
    
    // Performance optimizations
    private final Map<BlockPos, BlockPos> closestPipeCache = new HashMap<>();
    private long lastClosestPipeCacheClear = 0;
    private static final long CLOSEST_PIPE_CACHE_TIMEOUT = 30000; // 30 seconds
    
    // Debug logging control - set to true only when debugging
    private static final boolean DEBUG_LOGGING = false;
    
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
        
        // Only log if debug logging is enabled
        if (DEBUG_LOGGING && world != null && world.getTime() % 200 == 0) {
            Circuitmod.LOGGER.info("[NETWORK-ADD] Adding pipe at {} to network {}", pos, networkId);
        }
        
        scanForConnectedInventories(pos);
        
        invalidateRouteCache();
        
        // Only log if debug logging is enabled
        if (DEBUG_LOGGING) {
            Circuitmod.LOGGER.info("[ITEM-NETWORK] Added pipe at {} to network {}", pos, networkId);
        }
    }
    
    /**
     * Removes a pipe from this network.
     */
    public void removePipe(BlockPos pos) {
        if (pipes.remove(pos)) {
            ItemNetworkManager.pipeToNetwork.remove(pos);
            
            // Only log if debug logging is enabled
            if (DEBUG_LOGGING && world != null && world.getTime() % 200 == 0) {
                Circuitmod.LOGGER.info("[ITEM-NETWORK] Removed pipe at {} from network {}", pos, networkId);
            }
        }
        // Remove any inventories that were only connected through this pipe
        rescanAllInventories();
        invalidateRouteCache();
        
        // Only log if debug logging is enabled
        if (DEBUG_LOGGING) {
            Circuitmod.LOGGER.info("[ITEM-NETWORK] Removed pipe at {} from network {}", pos, networkId);
        }
    }
    
    /**
     * Scans around a pipe position for connected inventories.
     */
    private void scanForConnectedInventories(BlockPos pipePos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pipePos.offset(direction);
            BlockState neighborState = world.getBlockState(neighborPos);
            Inventory inventory = getInventoryAt(world, neighborPos, neighborState);
            
            if (inventory != null) {
                connectedInventories.put(neighborPos, inventory);
                inventoryConnections.computeIfAbsent(neighborPos, k -> new HashSet<>()).add(direction.getOpposite());
                
                // Determine if this is a source or destination based on pipe type
                BlockState pipeState = world.getBlockState(pipePos);
                if (pipeState.getBlock() instanceof starduster.circuitmod.block.OutputPipeBlock) {
                    sourceInventories.put(neighborPos, inventory);
                } else {
                    destinationInventories.put(neighborPos, inventory);
                }
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
    public void rescanAllInventories() {
        // Clear existing inventory mappings
        connectedInventories.clear();
        sourceInventories.clear();
        destinationInventories.clear();
        inventoryConnections.clear();
        
        // Rescan all pipes
        for (BlockPos pipePos : pipes) {
            scanForConnectedInventories(pipePos);
        }
        
        // Only log if debug logging is enabled
        if (DEBUG_LOGGING && world != null && world.getTime() % 200 == 0) {
            Circuitmod.LOGGER.info("[NETWORK-RESCAN] Rescan complete. Sources: {}, Destinations: {}", 
                sourceInventories.size(), destinationInventories.size());
        }
        
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
        
        // Sort destinations by distance (closest first) and find the best route
        List<RouteCandidate> candidates = new ArrayList<>();
        
        for (BlockPos destination : destinations) {
            List<BlockPos> path = findPath(sourcePos, destination);
            if (path != null) {
                // Calculate available space at this destination
                Inventory destInventory = destinationInventories.get(destination);
                int availableSpace = calculateAvailableSpace(destInventory, itemStack);
                
                // Only log if debug logging is enabled
                if (DEBUG_LOGGING && world != null && world.getTime() % 200 == 0) {
                    Circuitmod.LOGGER.info("[ROUTE-CREATE] Destination {} has {} available space, path length: {}", 
                        destination, availableSpace, path.size());
                }
                
                candidates.add(new RouteCandidate(destination, path, availableSpace, path.size()));
            }
        }
        
        // Sort by distance (closest first), then by available space
        candidates.sort((a, b) -> {
            if (a.distance != b.distance) {
                return Integer.compare(a.distance, b.distance); // Closest first
            }
            return Integer.compare(b.availableSpace, a.availableSpace); // More space first for same distance
        });
        
        // Find the best route: closest with space, or closest with any space
        ItemRoute bestRoute = null;
        for (RouteCandidate candidate : candidates) {
            if (candidate.availableSpace > 0) {
                bestRoute = new ItemRoute(sourcePos, candidate.destination, candidate.path);
                // Only log if debug logging is enabled
                if (DEBUG_LOGGING && world != null && world.getTime() % 200 == 0) {
                    Circuitmod.LOGGER.info("[ROUTE-CREATE] Selected route: {} -> {} (distance: {}, space: {})", 
                        sourcePos, candidate.destination, candidate.distance, candidate.availableSpace);
                }
                break;
            }
        }
        
        // Cache the result
        if (bestRoute != null) {
            routeCache.put(cacheKey, bestRoute);
            lastRouteUpdate = System.currentTimeMillis();
            // Only log if debug logging is enabled
            if (DEBUG_LOGGING && world != null && world.getTime() % 200 == 0) {
                Circuitmod.LOGGER.info("[ROUTE-CREATE] Cached route: {}", bestRoute);
            }
        } else {
            // Only log if debug logging is enabled
            if (DEBUG_LOGGING && world != null && world.getTime() % 200 == 0) {
                Circuitmod.LOGGER.info("[ROUTE-CREATE] No route found from {} to any destination", sourcePos);
            }
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
        
        // Only log if debug logging is enabled
        if (DEBUG_LOGGING && world != null && world.getTime() % 200 == 0) {
            Circuitmod.LOGGER.info("[NETWORK-SOURCES] Finding sources with {} from {} source inventories", 
                itemStack.getItem().getName().getString(), sourceInventories.size());
        }
        
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
                        // Only log if debug logging is enabled
                        if (DEBUG_LOGGING && world != null && world.getTime() % 200 == 0) {
                            Circuitmod.LOGGER.info("[NETWORK-SOURCES] Found source with {}: {}", 
                                itemStack.getItem().getName().getString(), inventoryPos);
                        }
                        break; // Found the item, no need to check more slots
                    }
                }
            }
        }
        
        // Only log if debug logging is enabled
        if (DEBUG_LOGGING && world != null && world.getTime() % 200 == 0) {
            Circuitmod.LOGGER.info("[NETWORK-SOURCES] Found {} sources with {}", 
                sources.size(), itemStack.getItem().getName().getString());
        }
        return sources;
    }
    
    /**
     * Finds all destinations that can accept a specific item (excluding specified position).
     */
    public List<BlockPos> findDestinationsForItem(ItemStack itemStack, BlockPos excludeDestination) {
        // Only log if debug logging is enabled
        if (DEBUG_LOGGING && world != null && world.getTime() % 200 == 0) {
            Circuitmod.LOGGER.info("[NETWORK-DEST] Finding destinations for {} (excluding {})", 
                itemStack.getItem().getName().getString(), excludeDestination);
        }
        
        List<BlockPos> destinations = new ArrayList<>();
        
        // Only check DESTINATION inventories (adjacent to regular pipes)
        // Never check source inventories (adjacent to output pipes) as destinations
        for (Map.Entry<BlockPos, Inventory> entry : destinationInventories.entrySet()) {
            BlockPos inventoryPos = entry.getKey();
            Inventory inventory = entry.getValue();
            
            // Skip if this is the excluded destination
            if (excludeDestination != null && inventoryPos.equals(excludeDestination)) {
                continue;
            }
            
            // Check all possible connection directions for this inventory
            Set<Direction> connectionDirs = inventoryConnections.getOrDefault(inventoryPos, new HashSet<>());
            
            for (Direction direction : connectionDirs) {
                if (canInsertItem(inventory, itemStack, direction)) {
                    destinations.add(inventoryPos);
                    // Only log if debug logging is enabled
                    if (DEBUG_LOGGING && world != null && world.getTime() % 200 == 0) {
                        Circuitmod.LOGGER.info("[NETWORK-DEST] Can insert {} into {} from direction {}", 
                            itemStack.getItem().getName().getString(), inventoryPos, direction);
                    }
                    break; // Found one valid direction, that's enough
                }
            }
        }
        
        // Only log if debug logging is enabled
        if (DEBUG_LOGGING && world != null && world.getTime() % 200 == 0) {
            Circuitmod.LOGGER.info("[NETWORK-DEST] Found {} destinations for {}", 
                destinations.size(), itemStack.getItem().getName().getString());
        }
        return destinations;
    }
    
    /**
     * Checks if an inventory has extractable items of the specified type.
     */
    private boolean hasExtractableItem(Inventory inventory, ItemStack targetStack, Direction side) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack slotStack = inventory.getStack(slot);
            if (!slotStack.isEmpty() && ItemStack.areItemsEqual(slotStack, targetStack)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Checks if an inventory can accept the specified item from the specified direction.
     */
    private boolean canInsertItem(Inventory inventory, ItemStack itemStack, Direction side) {
        // Check if the inventory can accept items from this side
        if (inventory instanceof net.minecraft.inventory.SidedInventory sidedInventory) {
            int[] availableSlots = sidedInventory.getAvailableSlots(side);
            if (availableSlots.length == 0) {
                return false;
            }
            
            // Check if any slot can accept the item
            for (int slot : availableSlots) {
                ItemStack slotStack = sidedInventory.getStack(slot);
                if (slotStack.isEmpty() || (ItemStack.areItemsEqual(slotStack, itemStack) && slotStack.getCount() < slotStack.getMaxCount())) {
                    return true;
                }
            }
            return false;
        } else {
            // For regular inventories, check if there's any empty slot or matching slot
            for (int slot = 0; slot < inventory.size(); slot++) {
                ItemStack slotStack = inventory.getStack(slot);
                if (slotStack.isEmpty() || (ItemStack.areItemsEqual(slotStack, itemStack) && slotStack.getCount() < slotStack.getMaxCount())) {
                    return true;
                }
            }
            return false;
        }
    }
    
    /**
     * Finds a path between two positions through the pipe network.
     */
    public List<BlockPos> findPath(BlockPos start, BlockPos end) {
        // Only log if debug logging is enabled
        if (DEBUG_LOGGING && world != null && world.getTime() % 200 == 0) {
            Circuitmod.LOGGER.info("[PATHFIND] Finding path from {} to {}", start, end);
        }
        
        // Find closest pipe to start
        BlockPos startPipe = findClosestPipe(start);
        BlockPos endPipe = findClosestPipe(end);
        
        if (startPipe == null || endPipe == null) {
            // Only log if debug logging is enabled
            if (DEBUG_LOGGING && world != null && world.getTime() % 200 == 0) {
                Circuitmod.LOGGER.info("[PATHFIND] No path found - missing start or end pipe");
            }
            return null;
        }
        
        // Use BFS to find shortest path through pipes
        Queue<BlockPos> queue = new LinkedList<>();
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Set<BlockPos> visited = new HashSet<>();
        
        queue.offer(startPipe);
        visited.add(startPipe);
        
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
                
                // Only log if debug logging is enabled
                if (DEBUG_LOGGING && world != null && world.getTime() % 200 == 0) {
                    Circuitmod.LOGGER.info("[PATHFIND] Found path with {} steps: {}", path.size(), path);
                }
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
        
        // Only log if debug logging is enabled
        if (DEBUG_LOGGING && world != null && world.getTime() % 200 == 0) {
            Circuitmod.LOGGER.info("[PATHFIND] No path found through pipe network");
        }
        return null; // No path found
    }
    
    /**
     * Finds the closest pipe to a given position.
     */
    private BlockPos findClosestPipe(BlockPos pos) {
        // Check cache first
        if (closestPipeCache.containsKey(pos)) {
            return closestPipeCache.get(pos);
        }
        
        // Clear cache periodically
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastClosestPipeCacheClear > CLOSEST_PIPE_CACHE_TIMEOUT) {
            closestPipeCache.clear();
            lastClosestPipeCacheClear = currentTime;
        }
        
        BlockPos closest = null;
        double minDistance = Double.MAX_VALUE;
        
        for (BlockPos pipe : pipes) {
            double distance = pos.getSquaredDistance(pipe);
            if (distance < minDistance) {
                minDistance = distance;
                closest = pipe;
            }
        }
        
        // Cache the result
        if (closest != null) {
            closestPipeCache.put(pos, closest);
        }
        
        return closest;
    }
    
    /**
     * Debug method to print network topology.
     */
    public void debugNetworkTopology() {
        if (!DEBUG_LOGGING) return;
        
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
     * Invalidates the route cache to force recalculation of routes.
     * This should be called when inventory contents change.
     */
    public void invalidateRouteCache() {
        routeCache.clear();
        lastRouteUpdate = System.currentTimeMillis();
        // Only log if debug logging is enabled
        if (DEBUG_LOGGING && world != null && world.getTime() % 200 == 0) {
            Circuitmod.LOGGER.info("[NETWORK-CACHE] Invalidated route cache for network {}", networkId);
        }
    }
    
    /**
     * Invalidates routes for a specific item type when inventory contents change.
     */
    public void invalidateRoutesForItem(ItemStack itemStack) {
        // Remove all cached routes for this item type
        routeCache.entrySet().removeIf(entry -> entry.getKey().contains(itemStack.getItem().toString()));
        // Only log if debug logging is enabled
        if (DEBUG_LOGGING && world != null && world.getTime() % 200 == 0) {
            Circuitmod.LOGGER.info("[NETWORK-CACHE] Invalidated routes for {} in network {}", 
                itemStack.getItem().getName().getString(), networkId);
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
        sourceInventories.putAll(other.sourceInventories);
        destinationInventories.putAll(other.destinationInventories);
        inventoryConnections.putAll(other.inventoryConnections);
        
        // Update pipe-to-network mapping
        for (BlockPos pipe : other.pipes) {
            ItemNetworkManager.pipeToNetwork.put(pipe, this);
        }
        
        invalidateRouteCache();
    }
    
    /**
     * Finds alternative destinations when the primary destination fails.
     */
    public List<BlockPos> findAlternativeDestinations(ItemStack itemStack, BlockPos currentPos, BlockPos excludeDestination, BlockPos failedDestination) {
        List<BlockPos> allDestinations = findDestinationsForItem(itemStack, excludeDestination);
        List<BlockPos> alternatives = new ArrayList<>();
        
        for (BlockPos destination : allDestinations) {
            if (!destination.equals(failedDestination)) {
                alternatives.add(destination);
            }
        }
        
        return alternatives;
    }
    
    /**
     * Finds a route with fallback support for when primary routes fail.
     */
    public ItemRoute findRouteWithFallback(ItemStack itemStack, BlockPos currentPos, BlockPos excludeDestination) {
        // First try to find a normal route
        ItemRoute route = findRoute(itemStack, currentPos, excludeDestination);
        if (route != null) {
            return route;
        }
        
        // If no route found, try to find any destination that can accept this item
        List<BlockPos> destinations = findDestinationsForItem(itemStack, excludeDestination);
        if (!destinations.isEmpty()) {
            // Use the first available destination
            BlockPos destination = destinations.get(0);
            List<BlockPos> path = findPath(currentPos, destination);
            if (path != null) {
                return new ItemRoute(currentPos, destination, path);
            }
        }
        
        return null;
    }
    
    /**
     * Finds a route excluding a specific failed destination.
     */
    public ItemRoute findRouteExcludingFailed(ItemStack itemStack, BlockPos currentPos, BlockPos excludeDestination, BlockPos failedDestination) {
        // Find alternative destinations
        List<BlockPos> alternatives = findAlternativeDestinations(itemStack, currentPos, excludeDestination, failedDestination);
        
        // Try to find a route to any alternative destination
        for (BlockPos destination : alternatives) {
            List<BlockPos> path = findPath(currentPos, destination);
            if (path != null) {
                // Check if destination has space
                Inventory destInventory = destinationInventories.get(destination);
                if (destInventory != null && calculateAvailableSpace(destInventory, itemStack) > 0) {
                    return new ItemRoute(currentPos, destination, path);
                }
            }
        }
        
        return null;
    }
    
    /**
     * Forces a complete rescan of all inventories in the network.
     */
    public void forceRescanAllInventories() {
        // Clear existing mappings
        connectedInventories.clear();
        sourceInventories.clear();
        destinationInventories.clear();
        inventoryConnections.clear();
        
        // Rescan all pipes
        for (BlockPos pipePos : pipes) {
            scanForConnectedInventories(pipePos);
        }
        
        // Clear caches
        invalidateRouteCache();
        closestPipeCache.clear();
        
        // Only log if debug logging is enabled
        if (DEBUG_LOGGING && world != null && world.getTime() % 200 == 0) {
            Circuitmod.LOGGER.info("[NETWORK-FORCE-RESCAN] Force rescan complete. Sources: {}, Destinations: {}", 
                sourceInventories.size(), destinationInventories.size());
        }
    }
    
    /**
     * Gets all source inventories in this network.
     */
    public Map<BlockPos, Inventory> getSourceInventories() {
        return new HashMap<>(sourceInventories);
    }
    
    /**
     * Gets all destination inventories in this network.
     */
    public Map<BlockPos, Inventory> getDestinationInventories() {
        return new HashMap<>(destinationInventories);
    }
    
    /**
     * Calculates the available space for an item in an inventory.
     */
    private int calculateAvailableSpace(Inventory inventory, ItemStack itemStack) {
        if (inventory == null) {
            return 0;
        }
        
        int availableSpace = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack slotStack = inventory.getStack(slot);
            if (slotStack.isEmpty()) {
                availableSpace += itemStack.getMaxCount();
            } else if (ItemStack.areItemsEqual(slotStack, itemStack)) {
                availableSpace += itemStack.getMaxCount() - slotStack.getCount();
            }
        }
        return availableSpace;
    }
    
    /**
     * Monitors inventory changes to update route cache when needed.
     */
    public void monitorInventoryChanges() {
        // This method is called periodically to check for inventory changes
        // For now, we'll just invalidate the cache periodically to ensure fresh routes
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRouteUpdate > ROUTE_CACHE_TIMEOUT) {
            invalidateRouteCache();
        }
    }
    
    /**
     * Checks if a destination has space for the specified item.
     */
    public boolean destinationHasSpace(BlockPos destination, ItemStack itemStack) {
        Inventory inventory = destinationInventories.get(destination);
        if (inventory == null) {
            return false;
        }
        return calculateAvailableSpace(inventory, itemStack) > 0;
    }
    
    /**
     * Helper class for route candidates.
     */
    private static class RouteCandidate {
        final BlockPos destination;
        final List<BlockPos> path;
        final int availableSpace;
        final int distance;
        
        RouteCandidate(BlockPos destination, List<BlockPos> path, int availableSpace, int distance) {
            this.destination = destination;
            this.path = path;
            this.availableSpace = availableSpace;
            this.distance = distance;
        }
    }
} 