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
import starduster.circuitmod.block.BasePipeBlock;
import starduster.circuitmod.block.ItemPipeBlock;
import starduster.circuitmod.item.network.ItemNetwork;
import starduster.circuitmod.item.network.ItemNetworkManager;
import starduster.circuitmod.item.network.ItemRoute;
import starduster.circuitmod.network.PipeNetworkAnimator;
import starduster.circuitmod.screen.SortingPipeScreenHandler;
import starduster.circuitmod.util.ImplementedInventory;

import java.util.List;

public class SortingPipeBlockEntity extends BlockEntity implements NamedScreenHandlerFactory, ImplementedInventory {
    // Main item storage (1 slot for current item being processed)
    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(1, ItemStack.EMPTY);
    
    // Filter inventory (6 slots for directional filters: N, E, S, W, Up, Down)
    private final DefaultedList<ItemStack> filterInventory = DefaultedList.ofSize(6, ItemStack.EMPTY);
    
    private int transferCooldown = 0;
    private Direction lastInputDirection = null;
    private int lastAnimationTick = -1; // Track when we last sent an animation to prevent duplicates

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
    
    /**
     * Called when the pipe is placed to connect to the item network.
     */
    public void onPlaced() {
        if (world != null && !world.isClient) {
            ItemNetworkManager.connectPipe(world, pos);
        }
    }
    
    /**
     * Called when the pipe is removed to disconnect from the item network.
     */
    public void onRemoved() {
        if (world != null && !world.isClient) {
            ItemNetworkManager.disconnectPipe(world, pos);
        }
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
            if (routeItemThroughNetwork(world, pos, blockEntity)) {
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
    
    /**
     * Route an item through the network while respecting sorting filters.
     */
    private static boolean routeItemThroughNetwork(World world, BlockPos pos, SortingPipeBlockEntity blockEntity) {
        ItemStack currentStack = blockEntity.getStack(0);
        if (currentStack.isEmpty()) {
            return false;
        }
        
        // Get the network for this pipe
        ItemNetwork network = ItemNetworkManager.getNetworkForPipe(pos);
        if (network == null) {
            return false;
        }
        
        // Find a route for this item
        ItemRoute route = network.findRoute(currentStack);
        if (route == null) {
            return false; // No available destination
        }
        
        // Get the next step in the route
        BlockPos nextStep = getNextStepInRoute(route, pos);
        if (nextStep == null) {
            return false;
        }
        
        // Check if this direction is allowed by our filters
        Direction nextDirection = getDirectionTowards(pos, nextStep);
        if (nextDirection != null && !isDirectionAllowedByFilter(blockEntity, currentStack, nextDirection)) {
            return false; // Filter blocks this direction
        }
        
        // If next step is the destination, transfer directly to inventory
        if (nextStep.equals(route.getDestination())) {
            return transferToAdjacentBlock(world, pos, nextStep, blockEntity, currentStack);
        }
        
        // Transfer to the next pipe in the route
        if (transferToAdjacentBlock(world, pos, nextStep, blockEntity, currentStack)) {
            blockEntity.setStack(0, ItemStack.EMPTY);
            blockEntity.markDirty();
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if an item is allowed to go in a specific direction based on filters.
     */
    private static boolean isDirectionAllowedByFilter(SortingPipeBlockEntity blockEntity, ItemStack item, Direction direction) {
        // Find the filter slot for this direction
        int slotIndex = -1;
        for (int i = 0; i < 6; i++) {
            if (DIRECTION_ORDER[i] == direction) {
                slotIndex = i;
                break;
            }
        }
        
        if (slotIndex == -1) {
            return true; // Invalid direction, allow by default
        }
        
        ItemStack filterStack = blockEntity.getFilterStack(slotIndex);
        
        if (filterStack.isEmpty()) {
            // No filter set - check if any other direction has a filter for this item
            for (int i = 0; i < 6; i++) {
                if (i != slotIndex) {
                    ItemStack otherFilter = blockEntity.getFilterStack(i);
                    if (!otherFilter.isEmpty() && ItemStack.areItemsAndComponentsEqual(item, otherFilter)) {
                        return false; // This item should go in a different direction
                    }
                }
            }
            return true; // No conflicting filters, allow
        } else {
            // Filter is set - only allow if item matches
            return ItemStack.areItemsAndComponentsEqual(item, filterStack);
        }
    }
    
    /**
     * Get the next step in a route from the current position.
     */
    private static BlockPos getNextStepInRoute(ItemRoute route, BlockPos currentPos) {
        List<BlockPos> path = route.getPath();
        
        for (int i = 0; i < path.size() - 1; i++) {
            if (path.get(i).equals(currentPos)) {
                return path.get(i + 1);
            }
        }
        
        return null;
    }
    
    /**
     * Transfer item to an adjacent block (pipe or inventory).
     */
    private static boolean transferToAdjacentBlock(World world, BlockPos fromPos, BlockPos toPos, 
                                                   SortingPipeBlockEntity fromPipe, ItemStack stack) {
        // Try to transfer to another pipe first
        if (world.getBlockState(toPos).getBlock() instanceof BasePipeBlock) {
            if (world.getBlockEntity(toPos) instanceof Inventory targetPipe) {
                if (targetPipe.isEmpty()) {
                    // Transfer the stack to the target pipe
                    targetPipe.setStack(0, stack.copy());
                    
                    // Update target pipe's input direction
                    if (targetPipe instanceof ItemPipeBlockEntity itemPipe) {
                        Direction direction = getDirectionTowards(fromPos, toPos);
                        if (direction != null) {
                            itemPipe.lastInputDirection = direction.getOpposite();
                            itemPipe.setTransferCooldown(8);
                        }
                    } else if (targetPipe instanceof SortingPipeBlockEntity sortingPipe) {
                        Direction direction = getDirectionTowards(fromPos, toPos);
                        if (direction != null) {
                            sortingPipe.setLastInputDirection(direction.getOpposite());
                            sortingPipe.setTransferCooldown(8);
                        }
                    }
                    
                    // Send animation
                    if (world instanceof ServerWorld serverWorld) {
                        fromPipe.sendAnimationIfAllowed(serverWorld, stack, fromPos, toPos);
                    }
                    
                    targetPipe.markDirty();
                    return true;
                }
            }
        }
        
        // Try to transfer to inventory
        if (world.getBlockEntity(toPos) instanceof Inventory targetInventory) {
            Direction direction = getDirectionTowards(fromPos, toPos);
            if (direction != null) {
                ItemStack remaining = transferToInventory(fromPipe, targetInventory, stack.copy(), direction.getOpposite());
                
                if (remaining.isEmpty() || remaining.getCount() < stack.getCount()) {
                    // Successfully transferred at least part of the item
                    fromPipe.setStack(0, remaining);
                    
                    // Send animation
                    if (world instanceof ServerWorld serverWorld) {
                        fromPipe.sendAnimationIfAllowed(serverWorld, stack, fromPos, toPos);
                    }
                    
                    fromPipe.markDirty();
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Get the direction from one position to another.
     */
    private static Direction getDirectionTowards(BlockPos from, BlockPos to) {
        BlockPos diff = to.subtract(from);
        
        if (diff.getX() == 1) return Direction.EAST;
        if (diff.getX() == -1) return Direction.WEST;
        if (diff.getY() == 1) return Direction.UP;
        if (diff.getY() == -1) return Direction.DOWN;
        if (diff.getZ() == 1) return Direction.SOUTH;
        if (diff.getZ() == -1) return Direction.NORTH;
        
        return null;
    }
    
    /**
     * Send animation to clients if allowed.
     */
    private void sendAnimationIfAllowed(ServerWorld world, ItemStack stack, BlockPos from, BlockPos to) {
        int currentTick = (int) world.getTime();
        
        // Only send animation if at least 3 ticks have passed since the last one
        if (currentTick - this.lastAnimationTick >= 3) {
            // Send animation starting immediately
            PipeNetworkAnimator.sendMoveAnimation(world, stack.copy(), from, to, 5); // Use 5 ticks for animation
            this.lastAnimationTick = currentTick;
        }
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