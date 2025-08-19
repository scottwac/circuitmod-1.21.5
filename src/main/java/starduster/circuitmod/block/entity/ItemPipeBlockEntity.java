package starduster.circuitmod.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
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

import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

/**
 * ItemPipe - Transports items hop-by-hop towards inventories.
 * Uses smart pathfinding to avoid getting stuck and prefers shorter paths to inventories.
 */
public class ItemPipeBlockEntity extends BlockEntity implements Inventory {
    
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
    private static final int INVENTORY_SIZE = 1;
    private static final int COOLDOWN_TICKS = 2; // Move every 2 ticks (much faster movement)
    private static final int MAX_PATHFIND_DEPTH = 12; // Increased depth for better pathfinding
    private static final int STUCK_TIMEOUT = 100; // Ticks before considering an item stuck
    private static final int DIRECTION_CHANGE_THRESHOLD = 20; // Ticks before allowing direction change
    
    private DefaultedList<ItemStack> inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private int transferCooldown = 0;
    
    // Movement state
    @Nullable private Direction movementDirection = null; // Which way this item is traveling
    @Nullable private BlockPos sourcePosition = null; // Where this item came from (prevent backflow)
    private int stuckTimer = 0; // How long the item has been unable to move
    private int directionChangeTimer = 0; // Timer for allowing direction changes
    
    public ItemPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ITEM_PIPE, pos, state);
    }
    
    public void onPlaced() {
        if (world != null && !world.isClient) {
            ItemNetworkManager.connectPipe(world, pos);
        }
    }
    
    public void onRemoved() {
        if (world != null && !world.isClient) {
            ItemNetworkManager.disconnectPipe(world, pos);
        }
    }

    public static void tick(World world, BlockPos pos, BlockState state, ItemPipeBlockEntity blockEntity) {
        if (world.isClient()) return;
        
        if (blockEntity.isEmpty()) {
            blockEntity.resetMovementState();
            return;
        }
        
        blockEntity.transferCooldown--;
        if (blockEntity.transferCooldown > 0) return;
        
        // Increment direction change timer
        blockEntity.directionChangeTimer++;
        
        ItemStack currentItem = blockEntity.getStack(0);
        
        // Try to move the item to its destination
        if (blockEntity.tryMoveItem(world, pos, currentItem)) {
            blockEntity.transferCooldown = COOLDOWN_TICKS;
            blockEntity.stuckTimer = 0; // Reset stuck timer on successful move
            blockEntity.markDirty();
        } else {
            // Item couldn't move - increment stuck timer
            blockEntity.stuckTimer++;
            
            // If item is stuck for too long, try emergency unsticking
            if (blockEntity.stuckTimer >= STUCK_TIMEOUT) {
                Circuitmod.LOGGER.warn("[ITEM-PIPE] Item {} stuck at {} for {} ticks, attempting emergency unstuck", 
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
     * Try to move the current item towards a destination.
     * Priority order:
     * 1. Continue in movement direction if it leads to an inventory (if direction change timer allows)
     * 2. Try adjacent inventories for direct delivery
     * 3. Find best direction using improved pathfinding
     * 4. Try any available direction as fallback
     */
    private boolean tryMoveItem(World world, BlockPos pos, ItemStack item) {
        // Step 1: If we have a movement direction and haven't been stuck too long, try to continue
        if (movementDirection != null && directionChangeTimer < DIRECTION_CHANGE_THRESHOLD) {
            BlockPos nextPos = pos.offset(movementDirection);
            
            // Try to deliver directly to inventory in movement direction
            if (tryInsertIntoInventory(world, nextPos, item)) {
                // Start animation AFTER successful delivery
                if (world instanceof ServerWorld serverWorld) {
                    PipeNetworkAnimator.sendPipeToPipeAnimation(serverWorld, item, pos, nextPos);
                }
                removeStack(0); // Item successfully delivered
                resetMovementState();
                return true;
            }
            
            // Try to pass to next pipe in movement direction
            if (tryPassToPipe(world, nextPos, item, movementDirection)) {
                // Start animation AFTER successful transfer
                if (world instanceof ServerWorld serverWorld) {
                    PipeNetworkAnimator.sendPipeToPipeAnimation(serverWorld, item, pos, nextPos);
                }
                removeStack(0); // Item moved to next pipe
                resetMovementState();
                return true;
            }
        }
        
        // Step 2: Try all adjacent inventories for direct delivery
        for (Direction direction : Direction.values()) {
            if (direction == getOppositeDirection(movementDirection)) continue; // Don't go backwards
            if (isSourceDirection(pos, direction)) continue; // Don't return to source
            
            BlockPos targetPos = pos.offset(direction);
            if (tryInsertIntoInventory(world, targetPos, item)) {
                // Start animation AFTER successful delivery
                if (world instanceof ServerWorld serverWorld) {
                    PipeNetworkAnimator.sendPipeToPipeAnimation(serverWorld, item, pos, targetPos);
                }
                removeStack(0); // Item successfully delivered
                resetMovementState();
                return true;
            }
        }
        
        // Step 3: Use improved pathfinding to find the best direction - NO ANIMATION HERE
        Direction bestDirection = findBestDirectionWithPathfinding(world, pos, item);
        if (bestDirection != null) {
            BlockPos nextPos = pos.offset(bestDirection);
            if (tryPassToPipe(world, nextPos, item, bestDirection)) {
                // Start animation AFTER successful transfer
                if (world instanceof ServerWorld serverWorld) {
                    PipeNetworkAnimator.sendPipeToPipeAnimation(serverWorld, item, pos, nextPos);
                }
                removeStack(0); // Item moved to next pipe
                resetMovementState();
                return true;
            }
        }
        
        // Step 4: If stuck for a while, allow more aggressive movement including backwards
        if (stuckTimer > 10) {
            for (Direction direction : Direction.values()) {
                BlockPos nextPos = pos.offset(direction);
                if (tryPassToPipe(world, nextPos, item, direction)) {
                    // Start animation AFTER successful transfer
                    if (world instanceof ServerWorld serverWorld) {
                        PipeNetworkAnimator.sendPipeToPipeAnimation(serverWorld, item, pos, nextPos);
                    }
                    removeStack(0);
                    resetMovementState();
                    return true;
                }
            }
        }
        
        return false; // Item couldn't move anywhere
    }
    
    /**
     * Emergency method to unstick items that have been stuck too long.
     * Tries more aggressive methods like ignoring source exclusion and forcing movement.
     */
    private boolean emergencyUnstuck(World world, BlockPos pos, ItemStack item) {
        Circuitmod.LOGGER.info("[EMERGENCY-UNSTUCK] Attempting to unstick item {} at {}", 
            item.getItem().getName().getString(), pos);
        
        // First, try to move to any adjacent pipe, ignoring all restrictions
        for (Direction direction : Direction.values()) {
            BlockPos nextPos = pos.offset(direction);
            if (tryPassToPipe(world, nextPos, item, direction)) {
                // Start animation AFTER successful transfer
                if (world instanceof ServerWorld serverWorld) {
                    PipeNetworkAnimator.sendPipeToPipeAnimation(serverWorld, item, pos, nextPos);
                }
                removeStack(0);
                resetMovementState();
                Circuitmod.LOGGER.info("[EMERGENCY-UNSTUCK] Successfully unstuck item to {}", nextPos);
                return true;
            }
        }
        
        // Try to dump item into any adjacent inventory, even if full
        for (Direction direction : Direction.values()) {
            BlockPos targetPos = pos.offset(direction);
            if (!(world.getBlockState(targetPos).getBlock() instanceof BasePipeBlock)) {
                Inventory inventory = getInventoryAt(world, targetPos);
                if (inventory != null) {
                    // Try to insert even a partial amount
                    ItemStack remaining = insertIntoInventory(inventory, item.copy());
                    if (remaining.getCount() < item.getCount()) {
                        // Animate the portion that was delivered
                        if (world instanceof ServerWorld serverWorld) {
                            ItemStack deliveredPortion = item.copy();
                            deliveredPortion.setCount(item.getCount() - remaining.getCount());
                            PipeNetworkAnimator.sendPipeToPipeAnimation(serverWorld, deliveredPortion, pos, targetPos);
                        }
                        
                        setStack(0, remaining);
                        inventory.markDirty();
                        Circuitmod.LOGGER.info("[EMERGENCY-UNSTUCK] Partially delivered item to inventory at {}", targetPos);
                        return true;
                    }
                }
            }
        }
        
        // Last resort: force movement to any adjacent pipe, even if it's full
        for (Direction direction : Direction.values()) {
            BlockPos nextPos = pos.offset(direction);
            BlockEntity targetEntity = world.getBlockEntity(nextPos);
            if (targetEntity instanceof Inventory targetPipe) {
                // Start animation before forcing since we know we're moving it
                if (world instanceof ServerWorld serverWorld) {
                    PipeNetworkAnimator.sendPipeToPipeAnimation(serverWorld, item, pos, nextPos);
                }
                
                // Force the item into the target pipe, even if it's full
                targetPipe.setStack(0, item.copy());
                removeStack(0);
                resetMovementState();
                targetPipe.markDirty();
                Circuitmod.LOGGER.info("[EMERGENCY-UNSTUCK] Forced item into pipe at {}", nextPos);
                return true;
            }
        }
        
        Circuitmod.LOGGER.error("[EMERGENCY-UNSTUCK] Failed to unstick item {} at {}", 
            item.getItem().getName().getString(), pos);
        return false;
    }
    
    /**
     * Try to insert item into an inventory at the given position.
     */
    private boolean tryInsertIntoInventory(World world, BlockPos pos, ItemStack item) {
        // Skip pipes
        if (world.getBlockState(pos).getBlock() instanceof BasePipeBlock) {
            return false;
        }
        
        Inventory inventory = getInventoryAt(world, pos);
        if (inventory == null) return false;
        
        // Check if inventory has space
        if (!hasSpaceForItem(inventory, item)) return false;
        
        // Try to insert the item
        ItemStack remaining = insertIntoInventory(inventory, item.copy());
        if (remaining.isEmpty()) {
            inventory.markDirty();
            
            Circuitmod.LOGGER.debug("[ITEM-PIPE] Delivered {} to inventory at {}", 
                item.getItem().getName().getString(), pos);
            return true;
        }
        
        return false;
    }
    
    /**
     * Try to pass item to another pipe.
     */
    private boolean tryPassToPipe(World world, BlockPos pos, ItemStack item, Direction direction) {
        if (!(world.getBlockState(pos).getBlock() instanceof BasePipeBlock)) {
            return false;
        }
        
        BlockEntity targetEntity = world.getBlockEntity(pos);
        if (!(targetEntity instanceof Inventory targetPipe) || !targetPipe.isEmpty()) {
            return false;
        }
        
        // Transfer item to target pipe
        targetPipe.setStack(0, item.copy());
        
        // Set movement state for target pipe
        if (targetEntity instanceof ItemPipeBlockEntity targetItemPipe) {
            targetItemPipe.setMovementDirection(direction);
            targetItemPipe.setSourcePosition(this.pos);
            targetItemPipe.setTransferCooldown(1);
        } else if (targetEntity instanceof SortingPipeBlockEntity targetSortingPipe) {
            targetSortingPipe.setLastInputDirection(direction.getOpposite());
            targetSortingPipe.setTransferCooldown(1);
        }
        
        targetPipe.markDirty();
        
        Circuitmod.LOGGER.debug("[ITEM-PIPE] Passed {} to pipe at {}", 
            item.getItem().getName().getString(), pos);
        return true;
    }
    
    /**
     * Find the best direction using improved pathfinding - NO ANIMATIONS HERE, just pathfinding.
     */
    private Direction findBestDirectionWithPathfinding(World world, BlockPos pos, ItemStack item) {
        Direction bestDirection = null;
        int bestScore = -1;
        
        for (Direction direction : Direction.values()) {
            if (isSourceDirection(pos, direction)) continue; // Don't go backwards
            
            BlockPos nextPos = pos.offset(direction);
            PathResult result = evaluatePathScoreImproved(world, nextPos, item, direction, 0, new HashSet<>(), new ArrayList<>());
            
            if (result.score > bestScore) {
                bestScore = result.score;
                bestDirection = direction;
            }
        }
        
        // NOTE: Removed continuous path animation - we only animate actual hops now
        
        return bestDirection;
    }
    
    /**
     * Improved pathfinding that explores multiple branches simultaneously.
     * Uses a visited set to prevent infinite loops and explores all possible paths.
     */
    private PathResult evaluatePathScoreImproved(World world, BlockPos pos, ItemStack item, Direction direction, int depth, Set<BlockPos> visited, List<BlockPos> currentPath) {
        if (depth >= MAX_PATHFIND_DEPTH || visited.contains(pos)) {
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
     * Check if inventory has space for an item.
     */
    private boolean hasSpaceForItem(Inventory inventory, ItemStack item) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack slotStack = inventory.getStack(slot);
            
            if (slotStack.isEmpty()) return true;
            
            if (ItemStack.areItemsEqual(slotStack, item)) {
                int maxCount = Math.min(slotStack.getMaxCount(), inventory.getMaxCountPerStack());
                if (slotStack.getCount() < maxCount) return true;
            }
        }
        return false;
    }
    
    /**
     * Insert item into inventory, handling SidedInventory properly.
     */
    private ItemStack insertIntoInventory(Inventory inventory, ItemStack stack) {
        if (inventory instanceof SidedInventory sidedInventory) {
            // Try all sides for SidedInventory
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
            // Regular inventory
            for (int slot = 0; slot < inventory.size(); slot++) {
                stack = insertIntoSlot(inventory, stack, slot);
                if (stack.isEmpty()) break;
            }
        }
        
        return stack;
    }
    
    /**
     * Insert item into a specific slot.
     */
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
    
    // Helper methods
    private Direction getOppositeDirection(Direction direction) {
        return direction != null ? direction.getOpposite() : null;
    }
    
    private boolean isSourceDirection(BlockPos currentPos, Direction direction) {
        return sourcePosition != null && currentPos.offset(direction).equals(sourcePosition);
    }
    
    private void resetMovementState() {
        movementDirection = null;
        sourcePosition = null;
        stuckTimer = 0;
        directionChangeTimer = 0;
    }
    
    // Setters for external control
    public void setMovementDirection(Direction direction) {
        this.movementDirection = direction;
    }
    
    public void setSourcePosition(BlockPos pos) {
        this.sourcePosition = pos;
    }
    
    public void setTransferCooldown(int cooldown) {
        this.transferCooldown = cooldown;
    }
    
    // Legacy compatibility methods for existing code
    
    /**
     * Gets the current network this pipe belongs to.
     */
    public ItemNetwork getNetwork() {
        return ItemNetworkManager.getNetworkForPipe(pos);
    }
    
    /**
     * Gets the transfer cooldown (for debugging/compatibility).
     */  
    public int getTransferCooldown() {
        return this.transferCooldown;
    }
    
    /**
     * Gets the current movement direction (for debugging).
     */
    public Direction getMovementDirection() {
        return this.movementDirection;
    }
    
    // Inventory implementation
    @Override
    public int size() { return inventory.size(); }
    
    @Override
    public boolean isEmpty() { return inventory.get(0).isEmpty(); }
    
    @Override
    public ItemStack getStack(int slot) { return inventory.get(slot); }
    
    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack result = Inventories.splitStack(inventory, slot, amount);
        if (!result.isEmpty()) markDirty();
        return result;
    }
    
    @Override
    public ItemStack removeStack(int slot) {
        ItemStack result = Inventories.removeStack(inventory, slot);
        if (!result.isEmpty()) markDirty();
        return result;
    }
    
    @Override
    public void setStack(int slot, ItemStack stack) {
        inventory.set(slot, stack);
        if (stack.getCount() > getMaxCountPerStack()) {
            stack.setCount(getMaxCountPerStack());
        }
        markDirty();
    }
    
    @Override
    public boolean canPlayerUse(net.minecraft.entity.player.PlayerEntity player) {
        return world.getBlockEntity(pos) == this && 
               player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
    
    @Override
    public void clear() { inventory.clear(); }
    
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        Inventories.writeNbt(nbt, inventory, registries);
        nbt.putInt("transfer_cooldown", transferCooldown);
        nbt.putInt("stuck_timer", stuckTimer);
        nbt.putInt("direction_change_timer", directionChangeTimer);
        
        if (movementDirection != null) {
            nbt.putInt("movement_direction", movementDirection.ordinal());
        }
        
        if (sourcePosition != null) {
            nbt.putInt("source_x", sourcePosition.getX());
            nbt.putInt("source_y", sourcePosition.getY());
            nbt.putInt("source_z", sourcePosition.getZ());
        }
    }
    
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        Inventories.readNbt(nbt, inventory, registries);
        transferCooldown = nbt.getInt("transfer_cooldown").orElse(0);
        stuckTimer = nbt.getInt("stuck_timer").orElse(0);
        directionChangeTimer = nbt.getInt("direction_change_timer").orElse(0);
        
        if (nbt.contains("movement_direction")) {
            int ordinal = nbt.getInt("movement_direction").orElse(-1);
            if (ordinal >= 0 && ordinal < Direction.values().length) {
                movementDirection = Direction.values()[ordinal];
            }
        }
        
        if (nbt.contains("source_x") && nbt.contains("source_y") && nbt.contains("source_z")) {
            sourcePosition = new BlockPos(
                nbt.getInt("source_x").orElse(0),
                nbt.getInt("source_y").orElse(0),
                nbt.getInt("source_z").orElse(0)
            );
        }
    }
}