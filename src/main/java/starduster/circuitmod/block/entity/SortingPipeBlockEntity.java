package starduster.circuitmod.block.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.screen.SortingPipeScreenHandler;
import starduster.circuitmod.util.ImplementedInventory;

public class SortingPipeBlockEntity extends BlockEntity implements NamedScreenHandlerFactory, ImplementedInventory {
    // Main item storage (1 slot for current item being processed)
    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(1, ItemStack.EMPTY);
    
    // Filter inventory (6 slots for directional filters: N, E, S, W, Up, Down)
    private final DefaultedList<ItemStack> filterInventory = DefaultedList.ofSize(6, ItemStack.EMPTY);
    
    private int transferCooldown = 0;
    private Direction lastInputDirection = null;

    // Direction to slot mapping for filters
    public static final Direction[] DIRECTION_ORDER = {
        Direction.NORTH,  // Slot 0
        Direction.EAST,   // Slot 1  
        Direction.SOUTH,  // Slot 2
        Direction.WEST,   // Slot 3
        Direction.UP,     // Slot 4
        Direction.DOWN    // Slot 5
    };

    public SortingPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SORTING_PIPE, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, SortingPipeBlockEntity blockEntity) {
        if (world.isClient) {
            return;
        }

        // Decrease transfer cooldown
        if (blockEntity.transferCooldown > 0) {
            blockEntity.transferCooldown--;
            return;
        }

        // Try to extract items from adjacent inventories into this pipe
        if (blockEntity.isEmpty()) {
            extractFromAdjacentInventories(world, pos, blockEntity);
        }
        
        // Process items in the pipe
        if (!blockEntity.isEmpty()) {
            if (sortAndTransferItem(world, pos, state, blockEntity)) {
                blockEntity.transferCooldown = 8; // Set cooldown after successful transfer
            }
        }
    }

    // Filter inventory access methods
    public DefaultedList<ItemStack> getFilterInventory() {
        return filterInventory;
    }

    public ItemStack getFilterStack(int slot) {
        return filterInventory.get(slot);
    }

    public void setFilterStack(int slot, ItemStack stack) {
        filterInventory.set(slot, stack);
        markDirty();
    }

    // Main inventory implementation (ImplementedInventory)
    @Override
    public DefaultedList<ItemStack> getItems() {
        return inventory;
    }

    // Screen handling
    @Override
    public Text getDisplayName() {
        return Text.translatable("block.circuitmod.sorting_pipe");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new SortingPipeScreenHandler(syncId, playerInventory, this);
    }

    // NBT serialization
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        
        // Save main inventory
        Inventories.writeNbt(nbt, inventory, registries);
        
        // Save filter inventory
        NbtCompound filtersNbt = new NbtCompound();
        Inventories.writeNbt(filtersNbt, filterInventory, registries);
        nbt.put("Filters", filtersNbt);
        
        nbt.putInt("TransferCooldown", this.transferCooldown);
        
        if (this.lastInputDirection != null) {
            nbt.putInt("LastInputDir", this.lastInputDirection.ordinal());
        }
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        
        // Load main inventory
        Inventories.readNbt(nbt, inventory, registries);
        
        // Load filter inventory
        if (nbt.contains("Filters")) {
            NbtCompound filtersNbt = nbt.getCompound("Filters").orElse(new NbtCompound());
            Inventories.readNbt(filtersNbt, filterInventory, registries);
        }
        
        this.transferCooldown = nbt.getInt("TransferCooldown", -1);
        
        if (nbt.contains("LastInputDir")) {
            int dirOrdinal = nbt.getInt("LastInputDir", 0);
            if (dirOrdinal >= 0 && dirOrdinal < Direction.values().length) {
                this.lastInputDirection = Direction.values()[dirOrdinal];
            } else {
                this.lastInputDirection = null;
            }
        } else {
            this.lastInputDirection = null;
        }
    }

    // Networking
    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        var nbt = new NbtCompound();
        writeNbt(nbt, registries);
        return nbt;
    }

    // Getters and setters for pipe logic compatibility
    public void setLastInputDirection(Direction direction) {
        this.lastInputDirection = direction;
    }

    public Direction getLastInputDirection() {
        return this.lastInputDirection;
    }
    
    public void setTransferCooldown(int cooldown) {
        this.transferCooldown = cooldown;
    }
    
    // Sorting pipe logic methods
    private static void extractFromAdjacentInventories(World world, BlockPos pos, SortingPipeBlockEntity blockEntity) {
        // Try to extract from inventories in all directions except where we last came from
        for (Direction direction : Direction.values()) {
            if (direction == blockEntity.lastInputDirection) {
                continue; // Don't extract from where we just came from
            }
            
            BlockPos adjacentPos = pos.offset(direction);
            if (world.getBlockEntity(adjacentPos) instanceof net.minecraft.inventory.Inventory inventory) {
                // Skip if it's another pipe to avoid conflicts
                if (world.getBlockState(adjacentPos).getBlock() instanceof starduster.circuitmod.block.BasePipeBlock) {
                    continue;
                }
                
                // Try to extract an item from this inventory
                for (int i = 0; i < inventory.size(); i++) {
                    ItemStack stack = inventory.getStack(i);
                    if (!stack.isEmpty() && canExtract(inventory, stack, i, direction.getOpposite())) {
                        // Extract one item
                        ItemStack extracted = stack.copy();
                        extracted.setCount(1);
                        
                        blockEntity.setStack(0, extracted);
                        stack.decrement(1);
                        inventory.markDirty();
                        blockEntity.lastInputDirection = direction;
                        blockEntity.markDirty();
                        return; // Only extract one item at a time
                    }
                }
            }
        }
    }
    
    private static boolean sortAndTransferItem(World world, BlockPos pos, BlockState state, SortingPipeBlockEntity blockEntity) {
        ItemStack currentItem = blockEntity.getStack(0);
        if (currentItem.isEmpty()) {
            return false;
        }
        
        // Find which direction this item should go based on filters
        Direction targetDirection = getTargetDirection(blockEntity, currentItem);
        
        if (targetDirection == null) {
            // No filter matches, try to output to any available direction (except input and filtered directions)
            targetDirection = findAvailableOutputDirection(world, pos, blockEntity);
        }
        
        if (targetDirection != null) {
            BlockPos targetPos = pos.offset(targetDirection);
            BlockState targetState = world.getBlockState(targetPos);
            Block targetBlock = targetState.getBlock();
            
            // Try to transfer to another pipe first (like item pipes do)
            if (targetBlock instanceof starduster.circuitmod.block.ItemPipeBlock && 
                world.getBlockEntity(targetPos) instanceof starduster.circuitmod.block.entity.ItemPipeBlockEntity targetPipe) {
                
                if (!targetPipe.isEmpty()) {
                    return false; // Skip if the target pipe already has an item
                }
                
                // Transfer the entire stack to the neighbor pipe
                targetPipe.setStack(0, currentItem.copy());
                targetPipe.lastInputDirection = targetDirection.getOpposite();
                targetPipe.transferCooldown = 8; // Set cooldown like other pipes
                targetPipe.markDirty();
                
                blockEntity.setStack(0, ItemStack.EMPTY);
                blockEntity.markDirty();
                return true;
            }
            
            // Try to transfer to another sorting pipe
            if (targetBlock instanceof starduster.circuitmod.block.SortingPipeBlock && 
                world.getBlockEntity(targetPos) instanceof SortingPipeBlockEntity targetSortingPipe) {
                
                if (!targetSortingPipe.isEmpty()) {
                    return false; // Skip if the target pipe already has an item
                }
                
                // Transfer the entire stack to the neighbor pipe
                targetSortingPipe.setStack(0, currentItem.copy());
                targetSortingPipe.setLastInputDirection(targetDirection.getOpposite());
                targetSortingPipe.setTransferCooldown(8); // Set cooldown
                targetSortingPipe.markDirty();
                
                blockEntity.setStack(0, ItemStack.EMPTY);
                blockEntity.markDirty();
                return true;
            }
            
            // Try to transfer to output pipe (which accepts everything)
            if (targetBlock instanceof starduster.circuitmod.block.OutputPipeBlock && 
                world.getBlockEntity(targetPos) instanceof starduster.circuitmod.block.entity.OutputPipeBlockEntity targetOutputPipe) {
                
                if (!targetOutputPipe.isEmpty()) {
                    return false; // Skip if the target pipe already has an item
                }
                
                // Transfer the entire stack to the neighbor pipe
                targetOutputPipe.setStack(0, currentItem.copy());
                targetOutputPipe.lastInputDirection = targetDirection.getOpposite();
                targetOutputPipe.markDirty();
                
                blockEntity.setStack(0, ItemStack.EMPTY);
                blockEntity.markDirty();
                return true;
            }
            
            // Try to transfer to other pipe types or inventories
            if (world.getBlockEntity(targetPos) instanceof net.minecraft.inventory.Inventory targetInventory) {
                ItemStack remaining = transferToInventory(blockEntity, targetInventory, currentItem.copy(), targetDirection.getOpposite());
                
                if (remaining.isEmpty() || remaining.getCount() < currentItem.getCount()) {
                    // Successfully transferred at least part of the item
                    blockEntity.setStack(0, remaining);
                    blockEntity.markDirty();
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private static Direction getTargetDirection(SortingPipeBlockEntity blockEntity, ItemStack item) {
        // Check each filter slot to see if this item matches
        for (int i = 0; i < 6; i++) {
            ItemStack filterStack = blockEntity.getFilterStack(i);
            if (!filterStack.isEmpty() && areItemsEqual(item, filterStack)) {
                return DIRECTION_ORDER[i];
            }
        }
        return null; // No matching filter found
    }
    
    private static Direction findAvailableOutputDirection(World world, BlockPos pos, SortingPipeBlockEntity blockEntity) {
        // Try all directions except where we came from and directions with existing filters
        for (Direction direction : Direction.values()) {
            if (direction == blockEntity.lastInputDirection) {
                continue; // Don't send back where we came from
            }
            
            // Skip directions that have existing filters for different items
            if (hasFilterForDirection(blockEntity, direction)) {
                continue; // This direction is reserved for specific filtered items
            }
            
            BlockPos targetPos = pos.offset(direction);
            if (world.getBlockEntity(targetPos) instanceof net.minecraft.inventory.Inventory) {
                return direction;
            }
        }
        return null;
    }
    
    private static boolean hasFilterForDirection(SortingPipeBlockEntity blockEntity, Direction direction) {
        // Find the slot index for this direction
        for (int i = 0; i < 6; i++) {
            if (DIRECTION_ORDER[i] == direction) {
                // Check if this direction has a filter assigned
                ItemStack filterStack = blockEntity.getFilterStack(i);
                return !filterStack.isEmpty();
            }
        }
        return false;
    }
    
    private static boolean areItemsEqual(ItemStack stack1, ItemStack stack2) {
        return ItemStack.areItemsEqual(stack1, stack2);
    }
    
    private static boolean canExtract(net.minecraft.inventory.Inventory inventory, ItemStack stack, int slot, Direction side) {
        if (inventory instanceof net.minecraft.inventory.SidedInventory sidedInventory) {
            return sidedInventory.canExtract(slot, stack, side);
        }
        return true; // Regular inventories allow extraction from any side
    }
    
    private static ItemStack transferToInventory(net.minecraft.inventory.Inventory from, net.minecraft.inventory.Inventory to, ItemStack stack, Direction side) {
        if (to instanceof net.minecraft.inventory.SidedInventory sidedInventory) {
            int[] slots = sidedInventory.getAvailableSlots(side);
            
            for (int slot : slots) {
                if (!stack.isEmpty()) {
                    stack = tryInsertIntoSlot(from, to, stack, slot, side);
                }
            }
        } else {
            for (int i = 0; i < to.size() && !stack.isEmpty(); i++) {
                stack = tryInsertIntoSlot(from, to, stack, i, side);
            }
        }
        
        return stack;
    }
    
    private static ItemStack tryInsertIntoSlot(net.minecraft.inventory.Inventory from, net.minecraft.inventory.Inventory to, ItemStack stack, int slot, Direction side) {
        ItemStack slotStack = to.getStack(slot);
        
        if (canInsert(to, stack, slot, side)) {
            if (slotStack.isEmpty()) {
                to.setStack(slot, stack);
                return ItemStack.EMPTY;
            } else if (ItemStack.areItemsEqual(slotStack, stack)) {
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
    
    private static boolean canInsert(net.minecraft.inventory.Inventory inventory, ItemStack stack, int slot, Direction side) {
        if (inventory instanceof net.minecraft.inventory.SidedInventory sidedInventory) {
            return sidedInventory.canInsert(slot, stack, side);
        }
        return inventory.isValid(slot, stack);
    }
} 