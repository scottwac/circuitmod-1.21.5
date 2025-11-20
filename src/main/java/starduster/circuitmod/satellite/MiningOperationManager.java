package starduster.circuitmod.satellite;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import starduster.circuitmod.Circuitmod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages active mining operations that need to be executed over time
 */
public class MiningOperationManager {
    private static final List<ActiveMiningOperation> activeOperations = new ArrayList<>();
    
    /**
     * Start a new mining operation
     */
    public static void startMiningOperation(ServerWorld world, List<BlockPos> blocks, int batchSize, MiningCallback callback, MiningProgressListener progressListener) {
        if (blocks.isEmpty()) {
            return;
        }
        
        ActiveMiningOperation operation = new ActiveMiningOperation(world, blocks, batchSize, callback, progressListener);
        activeOperations.add(operation);
        
        Circuitmod.LOGGER.info("[MINING-MANAGER] Started mining operation with {} blocks", blocks.size());
    }
    
    /**
     * Tick all active mining operations
     * Should be called every server tick
     */
    public static void tick(ServerWorld world) {
        if (activeOperations.isEmpty()) {
            return;
        }
        
        Iterator<ActiveMiningOperation> iterator = activeOperations.iterator();
        while (iterator.hasNext()) {
            ActiveMiningOperation operation = iterator.next();
            
            // Only tick operations in this world
            if (operation.world == world) {
                operation.tick();
                
                // Remove completed operations
                if (operation.isComplete()) {
                    iterator.remove();
                }
            }
        }
    }
    
    /**
     * Callback interface for mining events
     */
    public interface MiningCallback {
        void onBlockMined(BlockPos pos);
    }
    
    public interface MiningProgressListener {
        void onStart(int totalBlocks);
        void onBlockMined(BlockPos pos, int completedBlocks, int totalBlocks);
        void onComplete(int totalBlocks);
    }
    
    /**
     * Represents an active mining operation
     */
    private static class ActiveMiningOperation {
        private final ServerWorld world;
        private final List<BlockPos> blocks;
        private final MiningCallback miningCallback;
        private final MiningProgressListener progressListener;
        private final int batchSize;
        private int currentIndex = 0;
        private int ticksUntilNext = 0;
        private boolean started = false;
        private boolean completed = false;
        private static final int TICKS_PER_BLOCK = 20; // 1 second per block
        
        public ActiveMiningOperation(ServerWorld world, List<BlockPos> blocks, int batchSize, MiningCallback callback, MiningProgressListener progressListener) {
            this.world = world;
            this.blocks = blocks;
            this.miningCallback = callback;
            this.progressListener = progressListener;
            this.batchSize = Math.max(1, batchSize);
        }
        
        public void tick() {
            if (completed) {
                return;
            }
            
            if (!started) {
                started = true;
                ticksUntilNext = TICKS_PER_BLOCK;
                if (progressListener != null) {
                    progressListener.onStart(blocks.size());
                }
                return;
            }
            
            if (ticksUntilNext > 0) {
                ticksUntilNext--;
                return;
            }
            
            if (currentIndex >= blocks.size()) {
                complete();
                return;
            }
            
            int processed = 0;
            while (processed < batchSize && currentIndex < blocks.size()) {
                BlockPos pos = blocks.get(currentIndex);
                miningCallback.onBlockMined(pos);
                currentIndex++;
                processed++;
                
                if (progressListener != null) {
                    progressListener.onBlockMined(pos, currentIndex, blocks.size());
                }
            }
            
            if (currentIndex >= blocks.size()) {
                complete();
            } else {
                ticksUntilNext = TICKS_PER_BLOCK;
            }
        }
        
        public boolean isComplete() {
            return completed;
        }
        
        private void complete() {
            if (!completed) {
                completed = true;
                if (progressListener != null) {
                    progressListener.onComplete(blocks.size());
                }
            }
        }
    }
}

