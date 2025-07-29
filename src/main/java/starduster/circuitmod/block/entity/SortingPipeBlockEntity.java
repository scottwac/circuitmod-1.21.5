package starduster.circuitmod.block.entity;

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
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.BasePipeBlock;
import starduster.circuitmod.item.network.ItemNetworkManager;
import starduster.circuitmod.item.network.ItemNetwork;
import starduster.circuitmod.network.PipeNetworkAnimator;
import starduster.circuitmod.screen.SortingPipeScreenHandler;
import starduster.circuitmod.util.ImplementedInventory;

import java.util.ArrayList;
import java.util.List;

/**
 * SortingPipe - Routes items based on directional filters.
 * Items matching a filter go in that direction, others go to any unfiltered direction.
 * Uses the same hop-by-hop logic as ItemPipe but with filtering.
 */
public class SortingPipeBlockEntity extends BlockEntity implements NamedScreenHandlerFactory, ImplementedInventory {
    // Main item storage (1 slot for current item being processed)
    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(1, ItemStack.EMPTY);
    
    // Filter inventory (6 slots for directional filters: N, E, S, W, Up, Down)
    private final DefaultedList<ItemStack> filterInventory = DefaultedList.ofSize(6, ItemStack.EMPTY);
    
    private static final int COOLDOWN_TICKS = 8; // Same speed as regular pipes
    private static final int STUCK_TIMEOUT = 100; // Same as ItemPipe
    
    private int transferCooldown = 0;
    private Direction lastInputDirection = null;
    private int stuckTimer = 0;

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
    
    public void onPlaced() {
        if (world != null && !world.isClient()) {
            ItemNetworkManager.connectPipe(world, pos);
        }
    }
    
    public void onRemoved() {
        if (world != null && !world.isClient) {
            ItemNetworkManager.disconnectPipe(world, pos);
        }
    }

    public static void tick(World world, BlockPos pos, BlockState state, SortingPipeBlockEntity blockEntity) {
        if (world.isClient()) return;
        
        if (blockEntity.isEmpty()) return;
        
        blockEntity.transferCooldown--;
        if (blockEntity.transferCooldown > 0) return;
        
        ItemStack currentItem = blockEntity.getStack(0);
        
        // Try to move the item using filtering logic
        if (blockEntity.tryMoveItemWithFiltering(world, pos, currentItem)) {
            blockEntity.transferCooldown = COOLDOWN_TICKS;
            blockEntity.stuckTimer = 0;
            blockEntity.markDirty();
        } else {
            // Item couldn't move - increment stuck timer
            blockEntity.stuckTimer++;
            
            if (blockEntity.stuckTimer >= STUCK_TIMEOUT) {
                Circuitmod.LOGGER.warn("[SORTING-PIPE] Item {} stuck at {} for {} ticks, attempting emergency unstuck", 
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
     * Try to move item with filtering logic applied.
     * Priority:
     * 1. Try directions allowed by filters (if item has specific filter)
     * 2. Try unfiltered directions (if item has no specific filter)
     * 3. Try any direction as emergency fallback
     */
    private boolean tryMoveItemWithFiltering(World world, BlockPos pos, ItemStack item) {
        List<Direction> allowedDirections = getAllowedDirectionsForItem(item);
        
        Circuitmod.LOGGER.debug("[SORTING-PIPE] Item {} at {} - allowed directions: {}", 
            item.getItem().getName().getString(), pos, allowedDirections);
        
        // Step 1: Try allowed directions first (try inventories, then pipes)
        for (Direction direction : allowedDirections) {
            if (direction == getOppositeDirection(lastInputDirection)) continue; // Don't go backwards
            
            BlockPos targetPos = pos.offset(direction);
            
            // Try to deliver to inventory first
            if (tryInsertIntoInventory(world, targetPos, item)) {
                removeStack(0);
                return true;
            }
            
            // Then try to pass to pipe
            if (tryPassToPipe(world, targetPos, item, direction)) {
                removeStack(0);
                return true;
            }
        }
        
        // Step 2: If no allowed directions worked, this might be an emergency case
        // Try any direction that isn't backwards as absolute fallback
        for (Direction direction : Direction.values()) {
            if (direction == getOppositeDirection(lastInputDirection)) continue;
            if (allowedDirections.contains(direction)) continue; // Already tried these
            
            BlockPos targetPos = pos.offset(direction);
            
            if (tryInsertIntoInventory(world, targetPos, item)) {
                removeStack(0);
                Circuitmod.LOGGER.debug("[SORTING-PIPE] Used fallback direction {} for item {}", 
                    direction, item.getItem().getName().getString());
                return true;
            }
            
            if (tryPassToPipe(world, targetPos, item, direction)) {
                removeStack(0);
                Circuitmod.LOGGER.debug("[SORTING-PIPE] Used fallback direction {} for item {}", 
                    direction, item.getItem().getName().getString());
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Emergency unstuck method - similar to ItemPipe but considers filters
     */
    private boolean emergencyUnstuck(World world, BlockPos pos, ItemStack item) {
        Circuitmod.LOGGER.info("[SORTING-EMERGENCY] Attempting to unstuck item {} at {}", 
            item.getItem().getName().getString(), pos);
        
        // Try any direction, ignoring filters and input direction
        for (Direction direction : Direction.values()) {
            BlockPos nextPos = pos.offset(direction);
            
            if (tryPassToPipe(world, nextPos, item, direction)) {
                removeStack(0);
                Circuitmod.LOGGER.info("[SORTING-EMERGENCY] Successfully unstuck item to {}", nextPos);
                return true;
            }
        }
        
        // Last resort: try to dump into any adjacent inventory
        for (Direction direction : Direction.values()) {
            BlockPos targetPos = pos.offset(direction);
            if (!(world.getBlockState(targetPos).getBlock() instanceof BasePipeBlock)) {
                Inventory inventory = getInventoryAt(world, targetPos);
                if (inventory != null) {
                    ItemStack remaining = insertIntoInventory(inventory, item.copy());
                    if (remaining.getCount() < item.getCount()) {
                        setStack(0, remaining);
                        inventory.markDirty();
                        Circuitmod.LOGGER.info("[SORTING-EMERGENCY] Partially delivered item to inventory at {}", targetPos);
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Get all directions that are allowed for a specific item based on filters.
     */
    private List<Direction> getAllowedDirectionsForItem(ItemStack item) {
        List<Direction> allowedDirections = new ArrayList<>();
        
        // First check if this item has a specific filter set
        boolean hasSpecificFilter = false;
        for (int i = 0; i < 6; i++) {
            ItemStack filterStack = getFilterStack(i);
            if (!filterStack.isEmpty() && ItemStack.areItemsAndComponentsEqual(item, filterStack)) {
                hasSpecificFilter = true;
                Direction direction = DIRECTION_ORDER[i];
                allowedDirections.add(direction);
            }
        }
        
        // If item has specific filters, only use those directions
        if (hasSpecificFilter) {
            return allowedDirections;
        }
        
        // If no specific filter, item can go in any direction that doesn't have a filter
        for (int i = 0; i < 6; i++) {
            ItemStack filterStack = getFilterStack(i);
            if (filterStack.isEmpty()) {
                Direction direction = DIRECTION_ORDER[i];
                allowedDirections.add(direction);
            }
        }
        
        // If all directions are filtered for other items, allow all directions as fallback
        if (allowedDirections.isEmpty()) {
            for (Direction dir : Direction.values()) {
                allowedDirections.add(dir);
            }
        }
        
        return allowedDirections;
    }
    
    /**
     * Try to insert item into an inventory.
     */
    private boolean tryInsertIntoInventory(World world, BlockPos pos, ItemStack item) {
        if (world.getBlockState(pos).getBlock() instanceof BasePipeBlock) {
            return false;
        }
        
        Inventory inventory = getInventoryAt(world, pos);
        if (inventory == null) return false;
        
        if (!hasSpaceForItem(inventory, item)) return false;
        
        ItemStack remaining = insertIntoInventory(inventory, item.copy());
        if (remaining.isEmpty()) {
            inventory.markDirty();
            
            if (world instanceof ServerWorld serverWorld) {
                PipeNetworkAnimator.sendPipeToPipeAnimation(serverWorld, item, this.pos, pos);
            }
            
            Circuitmod.LOGGER.debug("[SORTING-PIPE] Delivered {} to inventory at {}", 
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
        
        targetPipe.setStack(0, item.copy());
        
        if (targetEntity instanceof ItemPipeBlockEntity targetItemPipe) {
            targetItemPipe.setMovementDirection(direction);
            targetItemPipe.setSourcePosition(this.pos);
            targetItemPipe.setTransferCooldown(1);
        } else if (targetEntity instanceof SortingPipeBlockEntity targetSortingPipe) {
            targetSortingPipe.setLastInputDirection(direction.getOpposite());
            targetSortingPipe.setTransferCooldown(1);
        }
        
        targetPipe.markDirty();
        
        if (world instanceof ServerWorld serverWorld) {
            PipeNetworkAnimator.sendPipeToPipeAnimation(serverWorld, item, this.pos, pos);
        }
        
        Circuitmod.LOGGER.debug("[SORTING-PIPE] Passed {} to pipe at {}", 
            item.getItem().getName().getString(), pos);
        return true;
    }
    
    // Helper methods (similar to ItemPipe)
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
    
    private ItemStack insertIntoInventory(Inventory inventory, ItemStack stack) {
        if (inventory instanceof SidedInventory sidedInventory) {
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
            for (int slot = 0; slot < inventory.size(); slot++) {
                stack = insertIntoSlot(inventory, stack, slot);
                if (stack.isEmpty()) break;
            }
        }
        return stack;
    }
    
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
    
    private Direction getOppositeDirection(Direction direction) {
        return direction != null ? direction.getOpposite() : null;
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

    // Setters for external control
    public void setLastInputDirection(Direction direction) {
        this.lastInputDirection = direction;
    }

    public Direction getLastInputDirection() {
        return this.lastInputDirection;
    }
    
    public void setTransferCooldown(int cooldown) {
        this.transferCooldown = cooldown;
    }
    
    public int getTransferCooldown() {
        return this.transferCooldown;
    }
    
    /**
     * Gets the current network this pipe belongs to.
     */
    public ItemNetwork getNetwork() {
        return ItemNetworkManager.getNetworkForPipe(pos);
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
        
        nbt.putInt("TransferCooldown", transferCooldown);
        nbt.putInt("StuckTimer", stuckTimer);
        
        if (lastInputDirection != null) {
            nbt.putInt("LastInputDir", lastInputDirection.ordinal());
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
        
        transferCooldown = nbt.getInt("TransferCooldown").orElse(0);
        stuckTimer = nbt.getInt("StuckTimer").orElse(0);
        
        if (nbt.contains("LastInputDir")) {
            int dirOrdinal = nbt.getInt("LastInputDir").orElse(-1);
            if (dirOrdinal >= 0 && dirOrdinal < Direction.values().length) {
                lastInputDirection = Direction.values()[dirOrdinal];
            }
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
}