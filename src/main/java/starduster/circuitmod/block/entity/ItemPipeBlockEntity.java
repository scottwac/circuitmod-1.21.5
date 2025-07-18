package starduster.circuitmod.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.world.World;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.ItemPipeBlock;
import starduster.circuitmod.network.PipeNetworkAnimator;

import org.jetbrains.annotations.Nullable;

/**
 * Block entity for item pipes, which can transfer items between inventories
 * similar to hoppers but forming networks.
 */
public class ItemPipeBlockEntity extends BlockEntity implements Inventory {
    private static final int INVENTORY_SIZE = 1; 
    private static final int COOLDOWN_TICKS = 5; 
    public static final int ANIMATION_TICKS = 5; 
    
    private DefaultedList<ItemStack> inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private int transferCooldown = -1;
    private long lastTickTime;
    private @Nullable Direction lastInputDirection = null; // Track where the item came from
    private int lastAnimationTick = -1; // Track when we last sent an animation to prevent duplicates
    
    public ItemPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ITEM_PIPE, pos, state);
    }

    /**
     * Called every tick to update the pipe's state.
     */
    public static void tick(World world, BlockPos pos, BlockState state, ItemPipeBlockEntity blockEntity) {
        if (world.isClient()) {
            return;
        }
        
        blockEntity.transferCooldown--;
        blockEntity.lastTickTime = world.getTime();
        
        if (!blockEntity.needsCooldown()) {
            // Debug logging for tick processing (reduced frequency)
            if (world.getTime() % 20 == 0) { // Only log every second
                // Circuitmod.LOGGER.info("[PIPE-TICK] Processing tick at " + pos + 
                //                       ", lastInputDir: " + blockEntity.lastInputDirection);
            }
            
            // Try to extract from inventory above first
            boolean didWork = extractFromAbove(world, pos, blockEntity);
            
            // If we have an item, try to push it in valid directions
            if (!blockEntity.isEmpty()) {
                didWork = transferItem(world, pos, state, blockEntity) || didWork;
            }
            
            // Always set cooldown to prevent constant ticking
            blockEntity.setTransferCooldown(COOLDOWN_TICKS);
            
            if (didWork) {
                // Only log when work is actually done
                // Circuitmod.LOGGER.info("[PIPE-TICK-WORK] Work done at " + pos + ", calling markDirty");
                blockEntity.markDirty();
            }
        }
    }
    
    /**
     * Extract an item from the inventory above this pipe.
     */
    private static boolean extractFromAbove(World world, BlockPos pos, ItemPipeBlockEntity blockEntity) {
        if (!blockEntity.isEmpty()) {
            return false; // Don't extract if we're already holding an item
        }
        
        // Get the inventory above us
        BlockPos abovePos = pos.up();
        BlockState aboveState = world.getBlockState(abovePos);
        
        // Don't extract from other pipes
        if (aboveState.getBlock() instanceof ItemPipeBlock) {
            return false;
        }
        
        // Check if there's an inventory to extract from
        Inventory aboveInventory = getInventoryAt(world, abovePos);
        if (aboveInventory != null) {
            Direction extractDirection = Direction.DOWN;
            
            // First, try to find a full stack to extract
            int fullStackSlot = findFullStackSlot(aboveInventory, extractDirection);
            if (fullStackSlot >= 0) {
                if (extract(blockEntity, aboveInventory, fullStackSlot, extractDirection)) {
                    // Set input direction to UP, but allow immediate downward flow
                    blockEntity.lastInputDirection = Direction.UP;
                    blockEntity.markDirty();
                    
                    // Send animation for item entering the pipe
                    if (world instanceof ServerWorld serverWorld) {
                        BlockPos animationFromPos = pos.up();
                        ItemStack extractedStack = blockEntity.getStack(0);
                        blockEntity.sendAnimationIfAllowed(serverWorld, extractedStack, animationFromPos, pos);
                    }
                    
                    Circuitmod.LOGGER.info("[PIPE-EXTRACT] Extracted full stack from slot " + fullStackSlot + " at " + pos);
                    return true;
                }
            }
            
            // If no full stack found, try to find the largest available stack
            int largestStackSlot = findLargestStackSlot(aboveInventory, extractDirection);
            if (largestStackSlot >= 0) {
                if (extract(blockEntity, aboveInventory, largestStackSlot, extractDirection)) {
                    // Set input direction to UP, but allow immediate downward flow
                    blockEntity.lastInputDirection = Direction.UP;
                    blockEntity.markDirty();
                    
                    // Send animation for item entering the pipe
                    if (world instanceof ServerWorld serverWorld) {
                        BlockPos animationFromPos = pos.up();
                        ItemStack extractedStack = blockEntity.getStack(0);
                        blockEntity.sendAnimationIfAllowed(serverWorld, extractedStack, animationFromPos, pos);
                    }
                    
                    Circuitmod.LOGGER.info("[PIPE-EXTRACT] Extracted largest stack from slot " + largestStackSlot + " at " + pos);
                    return true;
                }
            }
        }
        
        // Check for items on top of the pipe
        if (!blockEntity.isEmpty()) {
            return false;
        }
        
        Box box = new Box(pos.getX(), pos.getY() + 1, pos.getZ(), 
                          pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1);
        for (ItemEntity itemEntity : world.getEntitiesByClass(ItemEntity.class, box, EntityPredicates.VALID_ENTITY)) {
            if (extract(blockEntity, itemEntity)) {
                blockEntity.lastInputDirection = Direction.UP;
                blockEntity.markDirty();
                
                // Send animation for item entering the pipe
                if (world instanceof ServerWorld serverWorld) {
                    BlockPos animationFromPos = pos.up();
                    ItemStack extractedStack = blockEntity.getStack(0);
                    blockEntity.sendAnimationIfAllowed(serverWorld, extractedStack, animationFromPos, pos);
                }
                
                Circuitmod.LOGGER.info("[PIPE-EXTRACT] Extracted item from entity at " + pos);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Transfer an item to a connected inventory or the next pipe in the network.
     */
    private static boolean transferItem(World world, BlockPos pos, BlockState state, ItemPipeBlockEntity blockEntity) {
        if (blockEntity.isEmpty()) {
            return false;
        }
        
        // Priority 1: Try to output to the inventory below (highest priority)
        BlockPos belowPos = pos.down();
        Inventory belowInventory = getInventoryAt(world, belowPos);
        
        if (belowInventory != null && blockEntity.lastInputDirection != Direction.DOWN) {
            // Try to insert the item
            ItemStack currentStack = blockEntity.getStack(0);
            ItemStack remaining = transfer(blockEntity, belowInventory, currentStack.copy(), Direction.UP);
            
            if (remaining.isEmpty() || remaining.getCount() < currentStack.getCount()) {
                // Successfully transferred at least part of the stack
                blockEntity.setStack(0, remaining);
                blockEntity.markDirty();
                
                // Send animation for item leaving the pipe
                if (world instanceof ServerWorld serverWorld) {
                    blockEntity.sendAnimationIfAllowed(serverWorld, currentStack, pos, belowPos);
                }
                
                return true;
            }
        }
        
        // Priority 2: Try to transfer to connected pipes (avoid creating loops)
        // Define priority order for pipe transfers to encourage flow in one direction
        Direction[] priorityDirections = getPriorityDirections(blockEntity.lastInputDirection);
        
        for (Direction direction : priorityDirections) {
            // Skip the direction we got the item from to prevent sending back
            if (direction == blockEntity.lastInputDirection) {
                continue;
            }
            
            // Check if we're connected in this direction
            BooleanProperty directionProperty = getDirectionProperty(direction);
            
            if (!state.get(directionProperty)) {
                if (world.getTime() % 100 == 0) { // Debug log occasionally
                    Circuitmod.LOGGER.info("[PIPE-CONNECTION] Pipe at {} not connected in direction {}", pos, direction);
                }
                continue;
            }
            
            BlockPos neighborPos = pos.offset(direction);
            BlockState neighborState = world.getBlockState(neighborPos);
            
            // If it's another pipe, try to transfer to it
            if (neighborState.getBlock() instanceof ItemPipeBlock && 
                world.getBlockEntity(neighborPos) instanceof ItemPipeBlockEntity neighborPipe) {
                
                if (!neighborPipe.isEmpty()) {
                    continue; // Skip if the neighbor pipe already has an item
                }
                
                // Check if transferring to this pipe would create a loop
                if (wouldCreateLoop(pos, neighborPos, direction, blockEntity.lastInputDirection)) {
                    Circuitmod.LOGGER.info("[PIPE-LOOP-PREVENTION] Prevented loop: {} -> {} (came from {})", 
                        pos, direction, blockEntity.lastInputDirection);
                    continue;
                }
                
                // Transfer the entire stack to the neighbor pipe
                ItemStack currentStack = blockEntity.getStack(0);
                neighborPipe.setStack(0, currentStack.copy());
                neighborPipe.lastInputDirection = direction.getOpposite();
                neighborPipe.setTransferCooldown(COOLDOWN_TICKS);
                neighborPipe.markDirty();
                
                // Send animation to clients
                if (world instanceof ServerWorld serverWorld) {
                    blockEntity.sendAnimationIfAllowed(serverWorld, currentStack, pos, neighborPos);
                }
                
                blockEntity.setStack(0, ItemStack.EMPTY);
                blockEntity.markDirty();
                // Circuitmod.LOGGER.info("[PIPE-TRANSFER] SUCCESS: Transferred {} from {} to {} (direction: {}, lastInput: {})", 
                //     currentStack.getItem().getName().getString(), pos, neighborPos, direction, blockEntity.lastInputDirection);
                return true;
            }
            
            // Priority 3: Try to insert into side inventories (lowest priority)
            Inventory neighborInventory = getInventoryAt(world, neighborPos);
            if (neighborInventory != null) {
                ItemStack currentStack = blockEntity.getStack(0);
                ItemStack remaining = transfer(blockEntity, neighborInventory, currentStack.copy(), direction.getOpposite());
                
                if (remaining.isEmpty() || remaining.getCount() < currentStack.getCount()) {
                    // Successfully transferred at least part of the stack
                    blockEntity.setStack(0, remaining);
                    blockEntity.markDirty();
                    
                    // Send animation for item leaving the pipe
                    if (world instanceof ServerWorld serverWorld) {
                        blockEntity.sendAnimationIfAllowed(serverWorld, currentStack, pos, neighborPos);
                    }
                    
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Get priority directions for item transfer to prevent loops.
     * Prioritizes downward flow and avoids going back to source.
     */
    private static Direction[] getPriorityDirections(@Nullable Direction lastInputDirection) {
        // Always prioritize DOWN first (gravity-like behavior)
        if (lastInputDirection == null) {
            // No input direction, try all directions with DOWN first
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
     * Check if transferring an item would create a loop.
     * This is a simple heuristic to prevent immediate bounce-backs.
     */
    private static boolean wouldCreateLoop(BlockPos fromPos, BlockPos toPos, Direction transferDirection, @Nullable Direction lastInputDirection) {
        // Only prevent immediate bounce-back to the exact same direction we received from
        // This allows legitimate horizontal flow in pipe networks
        if (lastInputDirection != null && transferDirection == lastInputDirection) {
            return true;
        }
        
        // Allow all other transfers including opposite direction transfers
        // The original logic was too restrictive and prevented legitimate pipe chains
        return false;
    }
    
    /**
     * Get the BooleanProperty for a direction.
     */
    private static BooleanProperty getDirectionProperty(Direction direction) {
        return switch (direction) {
            case NORTH -> ItemPipeBlock.NORTH;
            case EAST -> ItemPipeBlock.EAST;
            case SOUTH -> ItemPipeBlock.SOUTH;
            case WEST -> ItemPipeBlock.WEST;
            case UP -> ItemPipeBlock.UP;
            case DOWN -> ItemPipeBlock.DOWN;
        };
    }
    
    /**
     * Find the best slot containing a full stack that can be extracted.
     * Prioritizes stacks with higher max stack sizes (more efficient transport).
     * Returns the slot index if found, -1 if no full stack is available.
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
     * Returns the slot index if found, -1 if no stack is available.
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
    private static boolean extract(ItemPipeBlockEntity pipe, Inventory inventory, int slot, Direction side) {
        ItemStack stack = inventory.getStack(slot);
        if (!stack.isEmpty() && canExtract(inventory, stack, slot, side)) {
            // Only extract if pipe is empty (we want to move full stacks at once)
            if (pipe.isEmpty()) {
                ItemStack extracted = stack.copy();
                // Extract the entire stack from the inventory slot
                pipe.setStack(slot, extracted);
                inventory.removeStack(slot, extracted.getCount());
                inventory.markDirty();
                return true;
            }
        }
        return false;
    }
    
    /**
     * Extract an item from an ItemEntity into the pipe.
     */
    private static boolean extract(ItemPipeBlockEntity pipe, ItemEntity itemEntity) {
        // Only extract if pipe is empty (we want to move full stacks at once)
        if (pipe.isEmpty()) {
            ItemStack entityStack = itemEntity.getStack().copy();
            // Take the entire stack from the item entity
            pipe.setStack(0, entityStack);
            itemEntity.discard();
            return true;
        }
        return false;
    }
    
    /**
     * Transfer an item from the pipe to a target inventory.
     */
    private static ItemStack transfer(Inventory from, Inventory to, ItemStack stack, @Nullable Direction side) {
        if (to instanceof SidedInventory sidedInventory && side != null) {
            int[] slots = sidedInventory.getAvailableSlots(side);
            
            for (int i = 0; i < slots.length && !stack.isEmpty(); i++) {
                stack = tryInsertIntoSlot(from, to, stack, slots[i], side);
            }
        } else {
            int size = to.size();
            
            for (int i = 0; i < size && !stack.isEmpty(); i++) {
                stack = tryInsertIntoSlot(from, to, stack, i, side);
            }
        }
        
        return stack;
    }
    
    /**
     * Try to insert an item into a specific slot of an inventory.
     */
    private static ItemStack tryInsertIntoSlot(Inventory from, Inventory to, ItemStack stack, int slot, @Nullable Direction side) {
        ItemStack slotStack = to.getStack(slot);
        
        if (canInsert(to, stack, slot, side)) {
            if (slotStack.isEmpty()) {
                // Slot is empty, insert the whole stack
                to.setStack(slot, stack);
                return ItemStack.EMPTY;
            } else if (canMergeItems(slotStack, stack)) {
                // Items can be merged
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
    
    /**
     * Check if an item can be inserted into a specific slot of an inventory.
     */
    private static boolean canInsert(Inventory inventory, ItemStack stack, int slot, @Nullable Direction side) {
        if (!inventory.isValid(slot, stack)) {
            return false;
        }
        
        if (inventory instanceof SidedInventory sidedInventory) {
            return sidedInventory.canInsert(slot, stack, side);
        }
        
        return true;
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
     * Check if two items can be merged.
     */
    private static boolean canMergeItems(ItemStack first, ItemStack second) {
        return first.getCount() < first.getMaxCount() && 
               ItemStack.areItemsEqual(first, second);
    }
    
    /**
     * Send an animation if enough time has passed since the last one to prevent duplicates
     */
    private void sendAnimationIfAllowed(ServerWorld world, ItemStack stack, BlockPos from, BlockPos to) {
        int currentTick = (int) world.getTime();
        
        // Only send animation if at least 3 ticks have passed since the last one
        if (currentTick - this.lastAnimationTick >= 3) {
            // Send animation starting immediately
            PipeNetworkAnimator.sendMoveAnimation(world, stack.copy(), from, to, ANIMATION_TICKS);
            this.lastAnimationTick = currentTick;
        } else {
            Circuitmod.LOGGER.info("[PIPE-ANIMATION-SKIP] Skipped duplicate animation at {} (last: {}, current: {})", 
                this.getPos(), this.lastAnimationTick, currentTick);
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
    
    /**
     * Check if the pipe is full (holding a full stack).
     */
    public boolean isFull() {
        ItemStack stack = this.inventory.get(0);
        if (stack.isEmpty()) {
            return false;
        }
        // Check if the stack is at its maximum capacity (item's max stack size)
        return stack.getCount() >= stack.getMaxCount();
    }

    @Override
    public ItemStack getStack(int slot) {
        ItemStack stack = this.inventory.get(0);
        // Only log occasionally to avoid spam
        if (world != null && world.isClient() && !stack.isEmpty() && world.getTime() % 20 == 0) {
            Circuitmod.LOGGER.info("[PIPE-GET-STACK] getStack called at " + this.getPos() + 
                                  ", slot: " + slot + 
                                  ", item: " + stack.getItem().getName().getString() + 
                                  ", count: " + stack.getCount());
        }
        return stack;
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack stack = !this.inventory.get(0).isEmpty() ? this.inventory.get(0).split(amount) : ItemStack.EMPTY;
        if (!stack.isEmpty()) {
            markDirty();
        }
        return stack;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack stack = this.inventory.get(0);
        this.inventory.set(0, ItemStack.EMPTY);
        markDirty();
        return stack;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        // Only log when actually setting a non-empty stack or clearing a stack
        if (!stack.isEmpty() || !this.inventory.get(0).isEmpty()) {
            Circuitmod.LOGGER.info("[PIPE-SET-STACK] setStack called at " + this.getPos() + 
                                  ", slot: " + slot + 
                                  ", item: " + (stack.isEmpty() ? "empty" : stack.getItem().getName().getString()) +
                                  ", count: " + stack.getCount() +
                                  ", isClient: " + (world != null && world.isClient()));
        }
        
        // Ensure the stack doesn't exceed the item's maximum stack size
        if (!stack.isEmpty() && stack.getCount() > stack.getMaxCount()) {
            stack.setCount(stack.getMaxCount());
        }
        
        this.inventory.set(0, stack);
        
        // Reset input direction when pipe becomes empty to prevent stale data
        if (stack.isEmpty()) {
            this.lastInputDirection = null;
        }
        
        markDirty();
    }

    @Override
    public boolean canPlayerUse(net.minecraft.entity.player.PlayerEntity player) {
        return false; // Players don't use pipe inventory directly
    }

    @Override
    public void clear() {
        this.inventory.clear();
        markDirty();
    }
    

    
    /**
     * Get the direction from which the last item entered this pipe.
     */
    public @Nullable Direction getLastInputDirection() {
        return this.lastInputDirection;
    }
    
    /**
     * Debug method to log the current state of the pipe.
     */
    public void debugLogState(String context) {
        Circuitmod.LOGGER.info("[PIPE-DEBUG-" + context + "] Pipe at " + this.getPos() + 
                              ", hasItem: " + !getStack(0).isEmpty() + 
                              ", lastInputDir: " + lastInputDirection + 
                              ", isClient: " + (world != null && world.isClient()));
    }
    
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        
        // Save the inventory
        Inventories.writeNbt(nbt, this.inventory, registries);
        
        // Save the last input direction if we have one
        if (this.lastInputDirection != null) {
            nbt.putInt("LastInputDir", this.lastInputDirection.ordinal());
        }
        
        // Save the last animation tick
        nbt.putInt("LastAnimationTick", this.lastAnimationTick);
        
        // Debug logging for NBT writing (reduced frequency)
        if (world != null && world.getTime() % 20 == 0) { // Only log every second
            Circuitmod.LOGGER.info("[PIPE-NBT-WRITE] Writing NBT at " + this.getPos() + 
                                  ", hasItem: " + !getStack(0).isEmpty() + 
                                  ", lastInputDir: " + lastInputDirection + 
                                  ", isClient: " + (world != null && world.isClient()));
        }
    }
    
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        
        // Load the inventory
        Inventories.readNbt(nbt, this.inventory, registries);
        
        // Load the last input direction if saved
        if (nbt.contains("LastInputDir")) {
            int dirOrdinal = nbt.getInt("LastInputDir", 0);
            this.lastInputDirection = Direction.values()[dirOrdinal];
        } else {
            this.lastInputDirection = null;
        }
        
        // Load the last animation tick
        this.lastAnimationTick = nbt.getInt("LastAnimationTick", -1);
        
        // Debug logging for NBT reading (reduced frequency)
        if (world != null && world.getTime() % 20 == 0) { // Only log every second
            Circuitmod.LOGGER.info("[PIPE-NBT-READ] Reading NBT at " + this.getPos() + 
                                  ", hasItem: " + !getStack(0).isEmpty() + 
                                  ", lastInputDir: " + lastInputDirection + 
                                  ", isClient: " + (world != null && world.isClient()) +
                                  ", nbtKeys: " + nbt.getKeys());
        }
    }
    
    //
    // CLIENT SYNC
    //
    @Override
    public @Nullable BlockEntityUpdateS2CPacket toUpdatePacket() {
        // Called by Minecraft when it needs to send a block‑entity update to the client
        Circuitmod.LOGGER.info("[PIPE-UPDATE-PACKET] Creating update packet at " + this.getPos() + 
                              ", hasItem: " + !getStack(0).isEmpty() + 
                              ", lastInputDir: " + lastInputDirection);
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        // send exactly the same data as writeNbt(...)
        Circuitmod.LOGGER.info("[PIPE-CHUNK-DATA] Creating initial chunk data at " + this.getPos() + 
                              ", hasItem: " + !getStack(0).isEmpty() + 
                              ", lastInputDir: " + lastInputDirection);
        NbtCompound tag = new NbtCompound();
        writeNbt(tag, registries);
        return tag;
    }

    @Override
    public void markDirty() {
        super.markDirty();
        
        // Only log markDirty calls occasionally to prevent spam
        if (world != null && world.getTime() % 20 == 0) { // Only log every second
            Circuitmod.LOGGER.info("[PIPE-MARK-DIRTY] markDirty called at " + this.getPos() + 
                                  ", hasItem: " + !getStack(0).isEmpty() + 
                                  ", lastInputDir: " + lastInputDirection + 
                                  ", isClient: " + (world != null && world.isClient()));
        }
        
        if (world != null && !world.isClient()) {
            // Build the vanilla packet containing our NBT:
            BlockEntityUpdateS2CPacket pkt = BlockEntityUpdateS2CPacket.create(this);
            // Send to every player within 64 blocks in this dimension:
            ServerWorld sw = (ServerWorld)world;
            int playerCount = sw.getServer().getPlayerManager().getPlayerList().size();
            
            // Only log broadcast occasionally to prevent spam
            if (world.getTime() % 20 == 0) {
                Circuitmod.LOGGER.info("[PIPE-BROADCAST] Broadcasting to " + playerCount + " players at " + this.getPos());
            }
            
            sw.getServer().getPlayerManager()
                .sendToAround(
                    /* except= */ null,
                    this.pos.getX(), this.pos.getY(), this.pos.getZ(),
                    /* radius= */ 64.0,
                    sw.getRegistryKey(),
                    pkt
                );
        }
    }
} 