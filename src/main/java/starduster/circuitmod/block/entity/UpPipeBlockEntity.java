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
import starduster.circuitmod.block.BasePipeBlock;
import starduster.circuitmod.network.PipeNetworkAnimator;
import starduster.circuitmod.util.ImplementedInventory;

public class UpPipeBlockEntity extends BlockEntity implements ImplementedInventory {
    private static final int INVENTORY_SIZE = 1;
    private static final int COOLDOWN_TICKS = 5;
    public static final int ANIMATION_TICKS = 5;

    private DefaultedList<ItemStack> inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);
    int transferCooldown = -1; // Package-private for access from other pipe classes
    private long lastTickTime;
    @Nullable Direction lastInputDirection = null; // Package-private for access from other pipe classes
    private int lastAnimationTick = -1; // Track when we last sent an animation to prevent duplicates

    public UpPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.UP_PIPE, pos, state);
    }

    @Override
    public DefaultedList<ItemStack> getItems() {
        return inventory;
    }

    /**
     * Called every tick to update the pipe's state.
     */
    public static void tick(World world, BlockPos pos, BlockState state, UpPipeBlockEntity blockEntity) {
        if (world.isClient()) {
            return;
        }
        
        blockEntity.transferCooldown--;
        blockEntity.lastTickTime = world.getTime();
        
        if (!blockEntity.needsCooldown()) {
            // Only transport items upward, don't extract from inventories
            boolean didWork = false;
            
            // If we have an item, try to push it upward
            if (!blockEntity.isEmpty()) {
                didWork = transferItemUpward(world, pos, state, blockEntity);
            }
            
            // Always set cooldown to prevent constant ticking
            blockEntity.setTransferCooldown(COOLDOWN_TICKS);
            
            if (didWork) {
                blockEntity.markDirty();
            }
        }
    }

    /**
     * Transfer an item upward to either a pipe or inventory above.
     */
    private static boolean transferItemUpward(World world, BlockPos pos, BlockState state, UpPipeBlockEntity blockEntity) {
        if (blockEntity.isEmpty()) {
            return false;
        }

        BlockPos abovePos = pos.up();
        BlockState aboveState = world.getBlockState(abovePos);
        ItemStack currentStack = blockEntity.getStack(0);

        // Try to transfer to a pipe above first
        if (aboveState.getBlock() instanceof BasePipeBlock && 
            world.getBlockEntity(abovePos) instanceof Inventory abovePipe) {
            
            if (abovePipe.isEmpty()) {
                // Transfer the entire stack to the pipe above
                abovePipe.setStack(0, currentStack.copy());
                abovePipe.markDirty();
                
                // Set input direction for the receiving pipe
                if (abovePipe instanceof ItemPipeBlockEntity targetItemPipe) {
                    targetItemPipe.lastInputDirection = Direction.DOWN;
                    targetItemPipe.transferCooldown = COOLDOWN_TICKS;
                } else if (abovePipe instanceof SortingPipeBlockEntity targetSortingPipe) {
                    targetSortingPipe.setLastInputDirection(Direction.DOWN);
                    targetSortingPipe.setTransferCooldown(COOLDOWN_TICKS);
                } else if (abovePipe instanceof OutputPipeBlockEntity targetOutputPipe) {
                    targetOutputPipe.lastInputDirection = Direction.DOWN;
                }
                
                // Send animation to clients
                if (world instanceof ServerWorld serverWorld) {
                    blockEntity.sendAnimationIfAllowed(serverWorld, currentStack, pos, abovePos);
                }
                
                blockEntity.setStack(0, ItemStack.EMPTY);
                blockEntity.markDirty();
                
                Circuitmod.LOGGER.info("[UP-PIPE-TRANSFER] Transferred to pipe above at {}", abovePos);
                return true;
            }
        }
        
        // Try to transfer to an inventory above
        Inventory aboveInventory = getInventoryAt(world, abovePos);
        if (aboveInventory != null) {
            // Skip if it's a pipe (already handled above)
            if (aboveState.getBlock() instanceof BasePipeBlock) {
                return false;
            }
            
            ItemStack remaining = transferToInventory(blockEntity, aboveInventory, currentStack.copy(), Direction.DOWN);
            
            if (remaining.isEmpty() || remaining.getCount() < currentStack.getCount()) {
                // Successfully transferred at least part of the item
                blockEntity.setStack(0, remaining);
                blockEntity.markDirty();
                
                // Send animation to clients
                if (world instanceof ServerWorld serverWorld) {
                    blockEntity.sendAnimationIfAllowed(serverWorld, currentStack, pos, abovePos);
                }
                
                Circuitmod.LOGGER.info("[UP-PIPE-TRANSFER] Transferred to inventory above at {}", abovePos);
                return true;
            }
        }
        
        return false;
    }

    /**
     * Transfer an item to a target inventory.
     */
    private static ItemStack transferToInventory(Inventory from, Inventory to, ItemStack stack, Direction side) {
        if (to instanceof SidedInventory sidedInventory) {
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

    /**
     * Try to insert an item into a specific slot of an inventory.
     */
    private static ItemStack tryInsertIntoSlot(Inventory from, Inventory to, ItemStack stack, int slot, Direction side) {
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

    /**
     * Check if an item can be inserted into a specific slot of an inventory.
     */
    private static boolean canInsert(Inventory inventory, ItemStack stack, int slot, Direction side) {
        if (inventory instanceof SidedInventory sidedInventory) {
            return sidedInventory.canInsert(slot, stack, side);
        }
        return inventory.isValid(slot, stack);
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

    private void setTransferCooldown(int cooldown) {
        this.transferCooldown = cooldown;
    }

    private boolean needsCooldown() {
        return this.transferCooldown > 0;
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
            Circuitmod.LOGGER.info("[UP-PIPE-ANIMATOR] Sending move animation: {} from {} to {} at tick {}", 
                stack.getItem().getName().getString(), from, to, currentTick);
        } else {
            Circuitmod.LOGGER.info("[UP-PIPE-ANIMATION-SKIP] Skipped duplicate animation at {} (last: {}, current: {})", 
                from, this.lastAnimationTick, currentTick);
        }
    }

    // NBT serialization
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        
        // Save inventory
        Inventories.writeNbt(nbt, inventory, registries);
        
        nbt.putInt("TransferCooldown", this.transferCooldown);
        nbt.putInt("LastAnimationTick", this.lastAnimationTick);
        
        if (this.lastInputDirection != null) {
            nbt.putInt("LastInputDir", this.lastInputDirection.ordinal());
        }
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        
        // Load inventory
        Inventories.readNbt(nbt, inventory, registries);
        
        this.transferCooldown = nbt.getInt("TransferCooldown", -1);
        this.lastAnimationTick = nbt.getInt("LastAnimationTick", -1);
        
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
        
        Circuitmod.LOGGER.info("[UP-PIPE-NBT-READ] Reading NBT at {}, hasItem: {}, lastInputDir: {}, isClient: {}, nbtKeys: {}", 
            pos, !inventory.get(0).isEmpty(), lastInputDirection, world != null ? world.isClient() : "unknown", nbt.getKeys());
    }
} 