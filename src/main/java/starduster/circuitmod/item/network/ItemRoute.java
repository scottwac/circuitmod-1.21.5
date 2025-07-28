package starduster.circuitmod.item.network;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a route for items to travel through the network.
 * Contains the path and metadata about the route.
 */
public class ItemRoute {
    private final BlockPos source;
    private final BlockPos destination;
    private final List<BlockPos> path;
    private final int distance;
    private final long createdTime;
    private int usageCount = 0;
    
    /**
     * Creates a new item route.
     * 
     * @param source The starting position
     * @param destination The ending position  
     * @param path The list of positions to travel through
     */
    public ItemRoute(BlockPos source, BlockPos destination, List<BlockPos> path) {
        this.source = source;
        this.destination = destination;
        this.path = new ArrayList<>(path);
        this.distance = path.size();
        this.createdTime = System.currentTimeMillis();
        
        // Validate path to prevent routing bugs
        validatePath();
    }
    
    /**
     * Validates the path to ensure it doesn't contain consecutive duplicate positions
     * that would cause routing loops.
     */
    private void validatePath() {
        if (path.size() < 2) {
            return; // Single position paths are always valid
        }
        
        for (int i = 0; i < path.size() - 1; i++) {
            BlockPos current = path.get(i);
            BlockPos next = path.get(i + 1);
            
            if (current.equals(next)) {
                starduster.circuitmod.Circuitmod.LOGGER.error("[ROUTE-VALIDATE] Invalid path with consecutive duplicate positions at index {} and {}: {}", i, i + 1, current);
                starduster.circuitmod.Circuitmod.LOGGER.error("[ROUTE-VALIDATE] Full path: {}", path);
                throw new IllegalArgumentException("Invalid route path contains consecutive duplicate positions: " + current);
            }
        }
        
        starduster.circuitmod.Circuitmod.LOGGER.info("[ROUTE-VALIDATE] Path validation passed for route {} -> {}", source, destination);
    }
    
    /**
     * Gets the source position of this route.
     */
    public BlockPos getSource() {
        return source;
    }
    
    /**
     * Gets the destination position of this route.
     */
    public BlockPos getDestination() {
        return destination;
    }
    
    /**
     * Gets the complete path as a list of positions.
     */
    public List<BlockPos> getPath() {
        return new ArrayList<>(path);
    }
    
    /**
     * Gets the next position in the path from the given current position.
     * 
     * @param currentPos The current position
     * @return The next position, or null if at destination or invalid path
     */
    public BlockPos getNextPosition(BlockPos currentPos) {
        starduster.circuitmod.Circuitmod.LOGGER.info("[ROUTE-NEXT] Getting next position from {} in route {}", currentPos, this);
        starduster.circuitmod.Circuitmod.LOGGER.info("[ROUTE-NEXT] Path contains {} positions: {}", path.size(), path);
        
        int currentIndex = path.indexOf(currentPos);
        starduster.circuitmod.Circuitmod.LOGGER.info("[ROUTE-NEXT] Current position {} found at index {} in path", currentPos, currentIndex);
        
        if (currentIndex >= 0 && currentIndex < path.size() - 1) {
            BlockPos nextPos = path.get(currentIndex + 1);
            starduster.circuitmod.Circuitmod.LOGGER.info("[ROUTE-NEXT] Next position: {}", nextPos);
            return nextPos;
        }
        
        starduster.circuitmod.Circuitmod.LOGGER.info("[ROUTE-NEXT] No next position found - at destination or invalid path");
        return null;
    }
    
    /**
     * Gets the direction to move from current position to the next.
     * 
     * @param currentPos The current position
     * @return The direction to move, or null if invalid
     */
    public Direction getNextDirection(BlockPos currentPos) {
        BlockPos nextPos = getNextPosition(currentPos);
        if (nextPos == null) {
            return null;
        }
        
        // Calculate direction from current to next
        BlockPos delta = nextPos.subtract(currentPos);
        for (Direction dir : Direction.values()) {
            if (dir.getVector().equals(delta)) {
                return dir;
            }
        }
        return null;
    }
    
    /**
     * Gets the distance of this route (number of hops).
     */
    public int getDistance() {
        return distance;
    }
    
    /**
     * Gets when this route was created.
     */
    public long getCreatedTime() {
        return createdTime;
    }
    
    /**
     * Increments the usage counter for this route.
     */
    public void incrementUsage() {
        usageCount++;
    }
    
    /**
     * Gets how many times this route has been used.
     */
    public int getUsageCount() {
        return usageCount;
    }
    
    /**
     * Checks if this route is still valid (not too old).
     * Routes expire after 30 seconds to handle network changes.
     */
    public boolean isValid() {
        return System.currentTimeMillis() - createdTime < 30000; // 30 seconds
    }
    
    /**
     * Checks if the given position is the final destination.
     */
    public boolean isDestination(BlockPos pos) {
        return destination.equals(pos);
    }
    
    @Override
    public String toString() {
        return String.format("ItemRoute{%s -> %s, distance=%d, usage=%d}", 
            source, destination, distance, usageCount);
    }
} 