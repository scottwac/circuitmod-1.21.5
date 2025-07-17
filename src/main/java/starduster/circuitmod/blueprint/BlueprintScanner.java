package starduster.circuitmod.blueprint;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import starduster.circuitmod.Circuitmod;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Handles async blueprint scanning to prevent server lag.
 * Scans areas in chunks over multiple ticks with rate limiting.
 */
public class BlueprintScanner {
    
    private static final int BLOCKS_PER_TICK = 50; // Maximum blocks to scan per tick
    private static final int MAX_BLUEPRINT_SIZE = 64 * 64 * 64; // Maximum total blocks in a blueprint
    private static final int MAX_DIMENSION = 100; // Maximum size in any single dimension
    
    /**
     * Iterate over every BlockPos in the axis-aligned cube defined by corner1 and corner2,
     * excluding the corner pieces (blueprint desks) themselves.
     */
    public static void scanCube(BlockPos corner1, BlockPos corner2, Consumer<BlockPos> action) {
        int minX = Math.min(corner1.getX(), corner2.getX());
        int maxX = Math.max(corner1.getX(), corner2.getX());
        int minY = Math.min(corner1.getY(), corner2.getY());
        int maxY = Math.max(corner1.getY(), corner2.getY());
        int minZ = Math.min(corner1.getZ(), corner2.getZ());
        int maxZ = Math.max(corner1.getZ(), corner2.getZ());
        
        // Exclude the corner pieces (blueprint desks) from the scan area
        // Only exclude if the desks are at different positions in each dimension
        if (corner1.getX() != corner2.getX()) {
            minX += 1; // Exclude first desk
            maxX -= 1; // Exclude second desk
        }
        if (corner1.getY() != corner2.getY()) {
            minY += 1; // Exclude first desk
            maxY -= 1; // Exclude second desk
        }
        if (corner1.getZ() != corner2.getZ()) {
            minZ += 1; // Exclude first desk
            maxZ -= 1; // Exclude second desk
        }
        
        // Check for invalid bounds (can happen if desks are too close together)
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            Circuitmod.LOGGER.error("[BLUEPRINT-SCANNER] Invalid scan area - desks may be too close together");
            return;
        }

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    action.accept(new BlockPos(x, y, z));
                }
            }
        }
    }
    
    /**
     * Scans an area asynchronously and creates a blueprint
     * 
     * @param startPos Starting corner position
     * @param endPos Ending corner position  
     * @param world The server world
     * @param blueprintName Name for the blueprint
     * @param onComplete Callback when scanning is complete
     * @param onProgress Callback for progress updates
     */
    public static void scanAreaAsync(BlockPos startPos, BlockPos endPos, ServerWorld world, String blueprintName,
                                   Consumer<Blueprint> onComplete, Consumer<Integer> onProgress) {
        
        // Collect all positions in the cube (excluding corner pieces)
        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        
        scanCube(startPos, endPos, pos -> {
            positions.add(pos);
            states.add(world.getBlockState(pos));
        });
        
        // Check if we have any positions to scan
        if (positions.isEmpty()) {
            Circuitmod.LOGGER.error("[BLUEPRINT-SCANNER] No valid positions to scan - desks may be too close together");
            return;
        }
        
        int totalBlocks = positions.size();
        if (totalBlocks > MAX_BLUEPRINT_SIZE) {
            Circuitmod.LOGGER.error("[BLUEPRINT-SCANNER] Area too large: {} blocks (max: {})", 
                totalBlocks, MAX_BLUEPRINT_SIZE);
            return;
        }
        
        // Calculate dimensions for the blueprint
        int minX = positions.stream().mapToInt(BlockPos::getX).min().orElse(0);
        int maxX = positions.stream().mapToInt(BlockPos::getX).max().orElse(0);
        int minY = positions.stream().mapToInt(BlockPos::getY).min().orElse(0);
        int maxY = positions.stream().mapToInt(BlockPos::getY).max().orElse(0);
        int minZ = positions.stream().mapToInt(BlockPos::getZ).min().orElse(0);
        int maxZ = positions.stream().mapToInt(BlockPos::getZ).max().orElse(0);
        
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int length = maxZ - minZ + 1;
        
        // Validate dimensions
        if (width > MAX_DIMENSION || height > MAX_DIMENSION || length > MAX_DIMENSION) {
            Circuitmod.LOGGER.error("[BLUEPRINT-SCANNER] Area too large: {}x{}x{} (max: {})", 
                width, height, length, MAX_DIMENSION);
            return;
        }
        
        Circuitmod.LOGGER.info("[BLUEPRINT-SCANNER] Starting async scan of {}x{}x{} area ({} blocks)", 
            width, height, length, totalBlocks);
        
        // Create the blueprint
        BlockPos dimensions = new BlockPos(width, height, length);
        BlockPos origin = new BlockPos(0, 0, 0); // Origin is relative to the blueprint
        Blueprint blueprint = new Blueprint(blueprintName, dimensions, origin);
        
        // Create the scanning task with the collected data
        ScanningTask task = new ScanningTask(blueprint, positions, states, minX, minY, minZ,
            world, onComplete, onProgress);
        
        // Start the task - it will run over multiple ticks
        task.start();
    }
    
    /**
     * Internal class that handles the actual scanning process
     */
    private static class ScanningTask {
        private final Blueprint blueprint;
        private final List<BlockPos> positions;
        private final List<BlockState> states;
        private final int minX, minY, minZ;
        private final ServerWorld world;
        private final Consumer<Blueprint> onComplete;
        private final Consumer<Integer> onProgress;
        
        private int currentIndex = 0;
        private int totalBlocks;
        private int scannedBlocks = 0;
        private boolean isComplete = false;
        
        public ScanningTask(Blueprint blueprint, List<BlockPos> positions, List<BlockState> states,
                          int minX, int minY, int minZ, ServerWorld world, 
                          Consumer<Blueprint> onComplete, Consumer<Integer> onProgress) {
            this.blueprint = blueprint;
            this.positions = positions;
            this.states = states;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.world = world;
            this.onComplete = onComplete;
            this.onProgress = onProgress;
            
            this.totalBlocks = positions.size();
        }
        
        public void start() {
            // Schedule the first tick
            scheduleNextTick();
        }
        
        private void scheduleNextTick() {
            if (isComplete) {
                return;
            }
            
            // Schedule for next tick using the world's task scheduler
            world.getServer().execute(this::processTick);
        }
        
        private void processTick() {
            if (isComplete) {
                return;
            }
            
            int blocksThisTick = 0;
            
            // Process blocks for this tick
            while (blocksThisTick < BLOCKS_PER_TICK && currentIndex < totalBlocks) {
                BlockPos worldPos = positions.get(currentIndex);
                BlockState blockState = states.get(currentIndex);
                
                // Ensure the chunk is loaded
                if (!world.isChunkLoaded(worldPos)) {
                    // Skip this block if chunk isn't loaded
                    currentIndex++;
                    scannedBlocks++;
                    blocksThisTick++;
                    continue;
                }
                
                // Only store non-air blocks
                if (!blockState.isAir()) {
                    // Calculate relative position in the blueprint
                    BlockPos relativePos = new BlockPos(
                        worldPos.getX() - minX,
                        worldPos.getY() - minY, 
                        worldPos.getZ() - minZ
                    );
                    
                    // Get block entity data if present
                    NbtCompound blockEntityData = null;
                    BlockEntity blockEntity = world.getBlockEntity(worldPos);
                    if (blockEntity != null) {
                        try {
                            blockEntityData = blockEntity.createNbt(world.getRegistryManager());
                        } catch (Exception e) {
                            Circuitmod.LOGGER.warn("[BLUEPRINT-SCANNER] Failed to serialize block entity at {}: {}", 
                                worldPos, e.getMessage());
                            blockEntityData = null;
                        }
                    }
                    
                    // Add to blueprint
                    blueprint.addBlock(relativePos, blockState, blockEntityData);
                }
                
                // Advance to next position
                currentIndex++;
                scannedBlocks++;
                blocksThisTick++;
                
                // Update progress every 10 blocks
                if (scannedBlocks % 10 == 0) {
                    int progress = (scannedBlocks * 100) / totalBlocks;
                    onProgress.accept(Math.min(progress, 99)); // Reserve 100 for completion
                }
            }
            
            // Check if we're done
            if (currentIndex >= totalBlocks) {
                complete();
                return;
            }
            
            // Schedule next tick if not complete
            if (!isComplete) {
                scheduleNextTick();
            }
        }
        

        
        private void complete() {
            isComplete = true;
            onProgress.accept(100);
            onComplete.accept(blueprint);
            
            Circuitmod.LOGGER.info("[BLUEPRINT-SCANNER] Scan complete! Blueprint '{}' has {} blocks", 
                blueprint.getName(), blueprint.getTotalBlocks());
        }
    }
} 