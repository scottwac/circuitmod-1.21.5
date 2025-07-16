package starduster.circuitmod.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.registry.RegistryWrapper;
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

import org.jetbrains.annotations.Nullable;

/**
 * Block entity for item pipes, which can transfer items between inventories
 * similar to hoppers but forming networks.
 */
public class ItemPipeBlockEntity extends BlockEntity implements Inventory {
    private static final int COOLDOWN_TIME = 1; // Much faster than vanilla hoppers (was 8)
    private static final int INVENTORY_SIZE = 1; // A pipe only holds one item at a time
    
    private DefaultedList<ItemStack> inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private int transferCooldown = -1;
    private long lastTickTime;
    private Direction lastInputDirection = null; // Track where the item came from
    
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
            blockEntity.setTransferCooldown(0);
            
            // Try to extract from inventory above first
            boolean didWork = extractFromAbove(world, pos, blockEntity);
            
            // If we have an item, try to push it in valid directions
            if (!blockEntity.isEmpty()) {
                didWork = transferItem(world, pos, state, blockEntity) || didWork;
            }
            
            if (didWork) {
                blockEntity.setTransferCooldown(COOLDOWN_TIME);
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
            
            // Try to extract an item from each slot
            for (int i = 0; i < aboveInventory.size(); i++) {
                            if (extract(blockEntity, aboveInventory, i, extractDirection)) {
                blockEntity.lastInputDirection = Direction.UP;
                blockEntity.markDirty();
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
        
        // First try to output to the inventory below
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
                return true;
            }
        }
        
        // If we couldn't output below, try to transfer to connected pipes
        for (Direction direction : Direction.values()) {
            // Skip the direction we got the item from to prevent sending back
            if (direction == blockEntity.lastInputDirection) {
                continue;
            }
            
            // Check if we're connected in this direction
            BooleanProperty directionProperty = getDirectionProperty(direction);
            
            if (!state.get(directionProperty)) {
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
                
                // Transfer the item to the next pipe
                ItemStack currentStack = blockEntity.getStack(0);
                neighborPipe.setStack(0, currentStack.copy());
                neighborPipe.lastInputDirection = direction.getOpposite();
                neighborPipe.setTransferCooldown(COOLDOWN_TIME); // Set cooldown for the receiving pipe
                neighborPipe.markDirty();
                
                blockEntity.setStack(0, ItemStack.EMPTY);
                blockEntity.markDirty();
                return true;
            }
            
            // If it's an inventory (not below), try to insert
            Inventory neighborInventory = getInventoryAt(world, neighborPos);
            if (neighborInventory != null) {
                ItemStack currentStack = blockEntity.getStack(0);
                ItemStack remaining = transfer(blockEntity, neighborInventory, currentStack.copy(), direction.getOpposite());
                
                if (remaining.isEmpty() || remaining.getCount() < currentStack.getCount()) {
                    // Successfully transferred at least part of the stack
                    blockEntity.setStack(0, remaining);
                    blockEntity.markDirty();
                    return true;
                }
            }
        }
        
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
     * Extract an item from an inventory into the pipe.
     */
    private static boolean extract(ItemPipeBlockEntity pipe, Inventory inventory, int slot, Direction side) {
        ItemStack stack = inventory.getStack(slot);
        if (!stack.isEmpty() && canExtract(inventory, stack, slot, side)) {
            ItemStack extracted = stack.copy();
            extracted.setCount(1); // Only extract one item at a time
            
            if (pipe.isEmpty()) {
                pipe.setStack(0, extracted);
                inventory.removeStack(slot, 1);
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
        if (pipe.isEmpty()) {
            ItemStack entityStack = itemEntity.getStack().copy();
            ItemStack extractedStack = entityStack.split(1); // Take 1 item
            
            pipe.setStack(0, extractedStack);
            
            if (entityStack.isEmpty()) {
                itemEntity.discard();
            } else {
                itemEntity.setStack(entityStack);
            }
            
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
        return this.inventory.get(0);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        return !this.inventory.get(0).isEmpty() ? this.inventory.get(0).split(amount) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack stack = this.inventory.get(0);
        this.inventory.set(0, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        this.inventory.set(0, stack);
    }

    @Override
    public boolean canPlayerUse(net.minecraft.entity.player.PlayerEntity player) {
        return false; // Players don't use pipe inventory directly
    }

    @Override
    public void clear() {
        this.inventory.clear();
    }
    
    /**
     * Get the direction from which the last item entered this pipe.
     */
    public Direction getLastInputDirection() {
        return this.lastInputDirection;
    }
    
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        
        // Save the inventory
        Inventories.writeNbt(nbt, this.inventory, registries);
        
        // Save the cooldown
        nbt.putInt("TransferCooldown", this.transferCooldown);
        
        // Save the last input direction if we have one
        if (this.lastInputDirection != null) {
            nbt.putInt("LastInputDir", this.lastInputDirection.ordinal());
        }
    }
    
    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        // Write current state to NBT for client sync
        NbtCompound nbt = new NbtCompound();
        Inventories.writeNbt(nbt, this.inventory, registries);
        if (this.lastInputDirection != null) {
            nbt.putInt("LastInputDir", this.lastInputDirection.ordinal());
        }
        return nbt;
    }
    
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        
        // Load the inventory
        Inventories.readNbt(nbt, this.inventory, registries);
        
        // Load the cooldown
        this.transferCooldown = nbt.getInt("TransferCooldown").orElse(-1);
        
        // Load the last input direction if saved
        if (nbt.contains("LastInputDir")) {
            int dirOrdinal = nbt.getInt("LastInputDir").orElse(0);
            this.lastInputDirection = Direction.values()[dirOrdinal];
        }
    }
} 