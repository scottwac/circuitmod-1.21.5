package starduster.circuitmod.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.BasePipeBlock;
import starduster.circuitmod.network.PipeNetworkAnimator;

import java.util.List;

public class OutputPipeBlockEntity extends BlockEntity implements Inventory {
    private static final int INVENTORY_SIZE = 1; 
    private static final int COOLDOWN_TICKS = 5; 
    public static final int ANIMATION_TICKS = 5; 
    
    private DefaultedList<ItemStack> inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private int transferCooldown = -1;
    private long lastTickTime;
    @Nullable Direction lastInputDirection = null; // Package-private for access from other pipe classes 
    private int lastAnimationTick = -1; 
    
    public OutputPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OUTPUT_PIPE, pos, state);
    }

    /**
     * Called every tick to update the output pipe's state.
     */
    public static void tick(World world, BlockPos pos, BlockState state, OutputPipeBlockEntity blockEntity) {
        if (world.isClient()) {
            return;
        }
        
        blockEntity.transferCooldown--;
        blockEntity.lastTickTime = world.getTime();
        
        if (!blockEntity.needsCooldown()) {
            boolean didWork = false;
            
            // Try to extract from connected inventories (always active)
            didWork = extractFromConnectedInventories(world, pos, state, blockEntity);
            
            // If we have an item, try to push it to connected pipes
            if (!blockEntity.isEmpty()) {
                didWork = transferItemToPipes(world, pos, state, blockEntity) || didWork;
            }
            
            // Always set cooldown to prevent constant ticking
            blockEntity.setTransferCooldown(COOLDOWN_TICKS);
            
            if (didWork) {
                blockEntity.markDirty();
            }
        }
    }
    
    /**
     * Extract items from all connected inventories (not pipes).
     */
    private static boolean extractFromConnectedInventories(World world, BlockPos pos, BlockState state, OutputPipeBlockEntity blockEntity) {
        if (!blockEntity.isEmpty()) {
            return false; // Don't extract if we're already holding an item
        }
        
        // Check all six directions for inventories to extract from (regardless of connections)
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.offset(direction);
            
            // Don't extract from other pipes
            if (world.getBlockState(neighborPos).getBlock() instanceof BasePipeBlock) {
                continue;
            }
            
            // Check if there's an inventory to extract from
            Inventory neighborInventory = getInventoryAt(world, neighborPos);
            if (neighborInventory != null) {
                Direction extractDirection = direction.getOpposite();
                
                // First, try to find a full stack to extract
                int fullStackSlot = findFullStackSlot(neighborInventory, extractDirection);
                if (fullStackSlot >= 0) {
                    if (extract(blockEntity, neighborInventory, fullStackSlot, extractDirection)) {
                        blockEntity.lastInputDirection = direction;
                        blockEntity.markDirty();
                        
                        // Send animation for item entering the pipe
                        if (world instanceof ServerWorld serverWorld) {
                            ItemStack extractedStack = blockEntity.getStack(0);
                            blockEntity.sendAnimationIfAllowed(serverWorld, extractedStack, neighborPos, pos);
                        }
                        
                        Circuitmod.LOGGER.info("[OUTPUT-PIPE-EXTRACT] Extracted full stack from {} at {}", direction, pos);
                        return true;
                    }
                }
                
                // If no full stack found, try to find the largest available stack
                int largestStackSlot = findLargestStackSlot(neighborInventory, extractDirection);
                if (largestStackSlot >= 0) {
                    if (extract(blockEntity, neighborInventory, largestStackSlot, extractDirection)) {
                        blockEntity.lastInputDirection = direction;
                        blockEntity.markDirty();
                        
                        // Send animation for item entering the pipe
                        if (world instanceof ServerWorld serverWorld) {
                            ItemStack extractedStack = blockEntity.getStack(0);
                            blockEntity.sendAnimationIfAllowed(serverWorld, extractedStack, neighborPos, pos);
                        }
                        
                        Circuitmod.LOGGER.info("[OUTPUT-PIPE-EXTRACT] Extracted largest stack from {} at {}", direction, pos);
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Transfer item to connected pipes only (output pipe doesn't output to inventories).
     */
    private static boolean transferItemToPipes(World world, BlockPos pos, BlockState state, OutputPipeBlockEntity blockEntity) {
        if (blockEntity.isEmpty()) {
            return false;
        }
        
        // Try all directions (prioritize down first, but try all)
        Direction[] allDirections = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};
        
        for (Direction direction : allDirections) {
            // Skip the direction we got the item from to prevent sending back
            if (direction == blockEntity.lastInputDirection) {
                continue;
            }
            
            BlockPos neighborPos = pos.offset(direction);
            BlockState neighborState = world.getBlockState(neighborPos);
            
            // Only transfer to other pipes
            if (neighborState.getBlock() instanceof BasePipeBlock && 
                world.getBlockEntity(neighborPos) instanceof Inventory neighborPipe) {
                
                if (neighborPipe.isEmpty()) {
                    // Transfer the entire stack to the neighbor pipe
                    ItemStack currentStack = blockEntity.getStack(0);
                    neighborPipe.setStack(0, currentStack.copy());
                    neighborPipe.markDirty();
                    
                    // Send animation to clients
                    if (world instanceof ServerWorld serverWorld) {
                        blockEntity.sendAnimationIfAllowed(serverWorld, currentStack, pos, neighborPos);
                    }
                    
                    blockEntity.setStack(0, ItemStack.EMPTY);
                    blockEntity.markDirty();
                    
                    Circuitmod.LOGGER.info("[OUTPUT-PIPE-TRANSFER] Transferred to pipe at {} (direction: {})", neighborPos, direction);
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Get priority directions for item transfer.
     */
    private static Direction[] getPriorityDirections(@Nullable Direction lastInputDirection) {
        // Always prioritize DOWN first (gravity-like behavior)
        if (lastInputDirection == null) {
            return new Direction[]{Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP};
        }
        
        // Create priority list avoiding the input direction
        java.util.List<Direction> priorities = new java.util.ArrayList<>();
        
        // Always try DOWN first (unless that's where we came from)
        if (lastInputDirection != Direction.DOWN) {
            priorities.add(Direction.DOWN);
        }
        
        // Add horizontal directions
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            if (dir != lastInputDirection) {
                priorities.add(dir);
            }
        }
        
        // Add UP last (unless that's where we came from)
        if (lastInputDirection != Direction.UP) {
            priorities.add(Direction.UP);
        }
        
        return priorities.toArray(new Direction[0]);
    }
    
    /**
     * Find the slot containing a full stack that can be extracted.
     */
    private static int findFullStackSlot(Inventory inventory, Direction side) {
        int bestSlot = -1;
        int bestMaxStackSize = 0;
        
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && 
                stack.getCount() >= stack.getMaxCount() && 
                canExtract(inventory, stack, i, side)) {
                
                // Prioritize items with higher max stack sizes for more efficient transport
                int maxStackSize = stack.getMaxCount();
                if (maxStackSize > bestMaxStackSize) {
                    bestSlot = i;
                    bestMaxStackSize = maxStackSize;
                }
            }
        }
        
        return bestSlot;
    }
    
    /**
     * Find the slot containing the largest stack that can be extracted.
     */
    private static int findLargestStackSlot(Inventory inventory, Direction side) {
        int largestSlot = -1;
        int largestCount = 0;
        
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && 
                stack.getCount() > largestCount && 
                canExtract(inventory, stack, i, side)) {
                largestSlot = i;
                largestCount = stack.getCount();
            }
        }
        
        return largestSlot;
    }
    
    /**
     * Extract an item from an inventory into the pipe.
     */
    private static boolean extract(OutputPipeBlockEntity pipe, Inventory inventory, int slot, Direction side) {
        ItemStack stack = inventory.getStack(slot);
        if (!stack.isEmpty() && canExtract(inventory, stack, slot, side)) {
            // Only extract if pipe is empty
            if (pipe.isEmpty()) {
                ItemStack extracted = stack.copy();
                pipe.setStack(0, extracted);
                inventory.removeStack(slot, extracted.getCount());
                inventory.markDirty();
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if an item can be extracted from a specific slot of an inventory.
     */
    private static boolean canExtract(Inventory inventory, ItemStack stack, int slot, Direction side) {
        if (inventory instanceof SidedInventory sidedInventory) {
            return sidedInventory.canExtract(slot, stack, side);
        }
        
        return true;
    }
    
    /**
     * Get the inventory at a specific position in the world.
     */
    @Nullable
    private static Inventory getInventoryAt(World world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        
        if (blockEntity instanceof Inventory) {
            return (Inventory) blockEntity;
        }
        
        return null;
    }
    
    /**
     * Send an animation if enough time has passed since the last one to prevent duplicates
     */
    private void sendAnimationIfAllowed(ServerWorld world, ItemStack stack, BlockPos from, BlockPos to) {
        int currentTick = (int) world.getTime();
        
        // Only send animation if at least 3 ticks have passed since the last one
        if (currentTick - this.lastAnimationTick >= 3) {
            PipeNetworkAnimator.sendMoveAnimation(world, stack.copy(), from, to, ANIMATION_TICKS);
            this.lastAnimationTick = currentTick;
        }
    }
    
    private void setTransferCooldown(int cooldown) {
        this.transferCooldown = cooldown;
    }
    
    private boolean needsCooldown() {
        return this.transferCooldown > 0;
    }
    
    // Inventory implementation
    
    @Override
    public int size() {
        return this.inventory.size();
    }

    @Override
    public boolean isEmpty() {
        return this.inventory.get(0).isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        return this.inventory.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        return slot >= 0 && slot < this.inventory.size() && !this.inventory.get(slot).isEmpty() && amount > 0 ? 
            this.inventory.get(slot).split(amount) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot) {
        return slot >= 0 && slot < this.inventory.size() ? 
            this.inventory.set(slot, ItemStack.EMPTY) : ItemStack.EMPTY;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot >= 0 && slot < this.inventory.size()) {
            this.inventory.set(slot, stack);
            
            if (stack.getCount() > this.getMaxCountPerStack()) {
                stack.setCount(this.getMaxCountPerStack());
            }
        }
    }

    @Override
    public boolean canPlayerUse(net.minecraft.entity.player.PlayerEntity player) {
        return false; // Output pipes are not directly accessible to players
    }

    @Override
    public void clear() {
        this.inventory.clear();
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        
        // Save the inventory
        Inventories.writeNbt(nbt, this.inventory, registries);
        
        nbt.putInt("TransferCooldown", this.transferCooldown);
        
        // Save the last input direction if we have one
        if (this.lastInputDirection != null) {
            nbt.putInt("LastInputDir", this.lastInputDirection.ordinal());
        }
        
        // Save the last animation tick
        nbt.putInt("LastAnimationTick", this.lastAnimationTick);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        
        // Load the inventory
        Inventories.readNbt(nbt, this.inventory, registries);
        
        this.transferCooldown = nbt.getInt("TransferCooldown", -1);
        
        // Load the last input direction if saved
        if (nbt.contains("LastInputDir")) {
            int dirOrdinal = nbt.getInt("LastInputDir", 0);
            this.lastInputDirection = Direction.values()[dirOrdinal];
        } else {
            this.lastInputDirection = null;
        }
        
        // Load the last animation tick
        this.lastAnimationTick = nbt.getInt("LastAnimationTick", -1);
    }
} 