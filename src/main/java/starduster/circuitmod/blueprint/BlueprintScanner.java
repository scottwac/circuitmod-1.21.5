package starduster.circuitmod.blueprint;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import starduster.circuitmod.Circuitmod;

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
        
        // Calculate the actual scan bounds
        int minX = Math.min(startPos.getX(), endPos.getX());
        int maxX = Math.max(startPos.getX(), endPos.getX());
        int minY = Math.min(startPos.getY(), endPos.getY());
        int maxY = Math.max(startPos.getY(), endPos.getY());
        int minZ = Math.min(startPos.getZ(), endPos.getZ());
        int maxZ = Math.max(startPos.getZ(), endPos.getZ());
        
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int length = maxZ - minZ + 1;
        
        // Validate dimensions
        if (width > MAX_DIMENSION || height > MAX_DIMENSION || length > MAX_DIMENSION) {
            Circuitmod.LOGGER.error("[BLUEPRINT-SCANNER] Area too large: {}x{}x{} (max: {})", 
                width, height, length, MAX_DIMENSION);
            return;
        }
        
        int totalBlocks = width * height * length;
        if (totalBlocks > MAX_BLUEPRINT_SIZE) {
            Circuitmod.LOGGER.error("[BLUEPRINT-SCANNER] Area too large: {} blocks (max: {})", 
                totalBlocks, MAX_BLUEPRINT_SIZE);
            return;
        }
        
        Circuitmod.LOGGER.info("[BLUEPRINT-SCANNER] Starting async scan of {}x{}x{} area ({} blocks)", 
            width, height, length, totalBlocks);
        
        // Create the blueprint
        BlockPos dimensions = new BlockPos(width, height, length);
        BlockPos origin = new BlockPos(0, 0, 0); // Origin is relative to the blueprint
        Blueprint blueprint = new Blueprint(blueprintName, dimensions, origin);
        
        // Create the scanning task
        ScanningTask task = new ScanningTask(blueprint, minX, maxX, minY, maxY, minZ, maxZ, 
            world, onComplete, onProgress);
        
        // Start the task - it will run over multiple ticks
        task.start();
    }
    
    /**
     * Internal class that handles the actual scanning process
     */
    private static class ScanningTask {
        private final Blueprint blueprint;
        private final int minX, maxX, minY, maxY, minZ, maxZ;
        private final ServerWorld world;
        private final Consumer<Blueprint> onComplete;
        private final Consumer<Integer> onProgress;
        
        private int currentX, currentY, currentZ;
        private int totalBlocks;
        private int scannedBlocks = 0;
        private boolean isComplete = false;
        
        public ScanningTask(Blueprint blueprint, int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
                          ServerWorld world, Consumer<Blueprint> onComplete, Consumer<Integer> onProgress) {
            this.blueprint = blueprint;
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.world = world;
            this.onComplete = onComplete;
            this.onProgress = onProgress;
            
            this.currentX = minX;
            this.currentY = minY;
            this.currentZ = minZ;
            this.totalBlocks = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
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
            while (blocksThisTick < BLOCKS_PER_TICK && !isComplete) {
                BlockPos worldPos = new BlockPos(currentX, currentY, currentZ);
                
                // Ensure the chunk is loaded
                if (!world.isChunkLoaded(worldPos)) {
                    // Skip this block if chunk isn't loaded
                    advancePosition();
                    scannedBlocks++;
                    blocksThisTick++;
                    continue;
                }
                
                // Get the block state
                BlockState blockState = world.getBlockState(worldPos);
                
                // Only store non-air blocks
                if (!blockState.isAir()) {
                    // Calculate relative position in the blueprint
                    BlockPos relativePos = new BlockPos(
                        currentX - minX,
                        currentY - minY, 
                        currentZ - minZ
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
                advancePosition();
                scannedBlocks++;
                blocksThisTick++;
                
                // Update progress every 10 blocks
                if (scannedBlocks % 10 == 0) {
                    int progress = (scannedBlocks * 100) / totalBlocks;
                    onProgress.accept(Math.min(progress, 99)); // Reserve 100 for completion
                }
                
                // Check if we're done
                if (scannedBlocks >= totalBlocks) {
                    complete();
                    return;
                }
            }
            
            // Schedule next tick if not complete
            if (!isComplete) {
                scheduleNextTick();
            }
        }
        
        private void advancePosition() {
            currentX++;
            if (currentX > maxX) {
                currentX = minX;
                currentZ++;
                if (currentZ > maxZ) {
                    currentZ = minZ;
                    currentY++;
                    if (currentY > maxY) {
                        // We've scanned everything
                        isComplete = true;
                    }
                }
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