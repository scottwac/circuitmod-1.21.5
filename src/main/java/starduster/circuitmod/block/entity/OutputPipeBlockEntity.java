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
import starduster.circuitmod.item.network.ItemNetwork;

import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.BasePipeBlock;
import starduster.circuitmod.item.network.ItemNetworkManager;
import starduster.circuitmod.network.PipeNetworkAnimator;

/**
 * OutputPipe - ONLY extracts from connected inventories and pushes items into adjacent pipes.
 * No routing logic - just extraction and injection into the pipe network.
 */
public class OutputPipeBlockEntity extends BlockEntity implements Inventory {
    private static final int INVENTORY_SIZE = 1;
    private static final int EXTRACT_COOLDOWN_TICKS = 20; // Extract every second
    private static final int PUSH_COOLDOWN_TICKS = 5; // Try to push every 5 ticks
    
    private DefaultedList<ItemStack> inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private int extractCooldown = 0;
    private int pushCooldown = 0;
    
    public OutputPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OUTPUT_PIPE, pos, state);
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

    public static void tick(World world, BlockPos pos, BlockState state, OutputPipeBlockEntity blockEntity) {
        if (world.isClient()) return;
        
        blockEntity.extractCooldown--;
        blockEntity.pushCooldown--;
        
        boolean didWork = false;
        
        // Phase 1: Try to push current item into adjacent pipes (higher priority)
        if (!blockEntity.isEmpty() && blockEntity.pushCooldown <= 0) {
            if (blockEntity.tryPushToAdjacentPipes(world, pos)) {
                blockEntity.pushCooldown = PUSH_COOLDOWN_TICKS;
                didWork = true;
            }
        }
        
        // Phase 2: Try to extract new item if we're empty and cooldown is ready
        if (blockEntity.isEmpty() && blockEntity.extractCooldown <= 0) {
            if (blockEntity.tryExtractFromAdjacentInventories(world, pos)) {
                blockEntity.extractCooldown = EXTRACT_COOLDOWN_TICKS;
                didWork = true;
            }
        }
        
        if (didWork) {
            blockEntity.markDirty();
        }
    }

    public ItemNetwork getNetwork() {
        return ItemNetworkManager.getNetworkForPipe(pos);
    }
    
    public int getTransferCooldown() {
        return this.pushCooldown;
    }
    
    /**
     * Try to push the current item to any adjacent pipe that can accept it.
     * Prioritizes pipes that aren't full and can move items forward.
     */
    private boolean tryPushToAdjacentPipes(World world, BlockPos pos) {
        ItemStack currentItem = getStack(0);
        if (currentItem.isEmpty()) return false;
        
        // Try each direction to find the best available pipe
        Direction bestDirection = null;
        BlockEntity bestTarget = null;
        
        for (Direction direction : Direction.values()) {
            BlockPos targetPos = pos.offset(direction);
            
            // Only push to pipes, never directly to inventories
            if (!(world.getBlockState(targetPos).getBlock() instanceof BasePipeBlock)) {
                continue;
            }
            
            BlockEntity targetEntity = world.getBlockEntity(targetPos);
            if (targetEntity instanceof Inventory targetPipe && targetPipe.isEmpty()) {
                // Found an empty pipe - prefer this one
                bestDirection = direction;
                bestTarget = targetEntity;
                break; // Take the first empty pipe we find
            }
        }
        
        if (bestDirection != null && bestTarget != null) {
            // Transfer the item
            ItemStack itemToTransfer = removeStack(0);
            ((Inventory) bestTarget).setStack(0, itemToTransfer);
            
            // Set movement state for the target pipe
            if (bestTarget instanceof ItemPipeBlockEntity itemPipe) {
                itemPipe.setMovementDirection(bestDirection);
                itemPipe.setSourcePosition(pos);
                itemPipe.setTransferCooldown(1);
            } else if (bestTarget instanceof SortingPipeBlockEntity sortingPipe) {
                sortingPipe.setLastInputDirection(bestDirection.getOpposite());
                sortingPipe.setTransferCooldown(1);
            }
            
            bestTarget.markDirty();
            
            // Send animation
            if (world instanceof ServerWorld serverWorld) {
                PipeNetworkAnimator.sendMoveAnimation(serverWorld, itemToTransfer, 
                    pos, pos.offset(bestDirection), 5);
            }
            
            Circuitmod.LOGGER.debug("[OUTPUT-PIPE] Pushed {} to pipe at {}", 
                itemToTransfer.getItem().getName().getString(), pos.offset(bestDirection));
            return true;
        }
        
        return false;
    }
    
    /**
     * Try to extract an item from any adjacent inventory.
     * Prefers full stacks for better throughput.
     */
    private boolean tryExtractFromAdjacentInventories(World world, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos inventoryPos = pos.offset(direction);
            
            // Skip pipes - only extract from actual inventories
            if (world.getBlockState(inventoryPos).getBlock() instanceof BasePipeBlock) {
                continue;
            }
            
            Inventory inventory = getInventoryAt(world, inventoryPos);
            if (inventory != null) {
                ItemStack extracted = extractFromInventory(inventory, direction.getOpposite());
                if (!extracted.isEmpty()) {
                    setStack(0, extracted);
                    inventory.markDirty();
                    
                    Circuitmod.LOGGER.debug("[OUTPUT-PIPE] Extracted {} from inventory at {}", 
                        extracted.getItem().getName().getString(), inventoryPos);
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Extract one full stack from an inventory, respecting SidedInventory rules.
     * Prefers full stacks, then largest stacks.
     */
    private ItemStack extractFromInventory(Inventory inventory, Direction side) {
        // First pass: look for full stacks
        ItemStack extracted = findAndExtractFullStack(inventory, side);
        if (!extracted.isEmpty()) return extracted;
        
        // Second pass: look for largest available stack
        return findAndExtractLargestStack(inventory, side);
    }
    
    private ItemStack findAndExtractFullStack(Inventory inventory, Direction side) {
        if (inventory instanceof SidedInventory sidedInventory) {
            int[] availableSlots = sidedInventory.getAvailableSlots(side);
            
            for (int slot : availableSlots) {
                ItemStack stack = inventory.getStack(slot);
                if (!stack.isEmpty() && 
                    stack.getCount() >= stack.getMaxCount() && 
                    sidedInventory.canExtract(slot, stack, side)) {
                    
                    ItemStack extracted = stack.copy();
                    inventory.setStack(slot, ItemStack.EMPTY);
                    return extracted;
                }
            }
        } else {
            for (int slot = 0; slot < inventory.size(); slot++) {
                ItemStack stack = inventory.getStack(slot);
                if (!stack.isEmpty() && stack.getCount() >= stack.getMaxCount()) {
                    ItemStack extracted = stack.copy();
                    inventory.setStack(slot, ItemStack.EMPTY);
                    return extracted;
                }
            }
        }
        
        return ItemStack.EMPTY;
    }
    
    private ItemStack findAndExtractLargestStack(Inventory inventory, Direction side) {
        int bestSlot = -1;
        int bestCount = 0;
        
        if (inventory instanceof SidedInventory sidedInventory) {
            int[] availableSlots = sidedInventory.getAvailableSlots(side);
            
            for (int slot : availableSlots) {
                ItemStack stack = inventory.getStack(slot);
                if (!stack.isEmpty() && 
                    stack.getCount() > bestCount && 
                    sidedInventory.canExtract(slot, stack, side)) {
                    bestSlot = slot;
                    bestCount = stack.getCount();
                }
            }
        } else {
            for (int slot = 0; slot < inventory.size(); slot++) {
                ItemStack stack = inventory.getStack(slot);
                if (!stack.isEmpty() && stack.getCount() > bestCount) {
                    bestSlot = slot;
                    bestCount = stack.getCount();
                }
            }
        }
        
        if (bestSlot >= 0) {
            ItemStack stack = inventory.getStack(bestSlot);
            ItemStack extracted = stack.copy();
            inventory.setStack(bestSlot, ItemStack.EMPTY);
            return extracted;
        }
        
        return ItemStack.EMPTY;
    }
    
    @Nullable
    private static Inventory getInventoryAt(World world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof Inventory inventory) {
            // Handle special cases like double chests
            BlockState state = world.getBlockState(pos);
            if (blockEntity instanceof net.minecraft.block.entity.ChestBlockEntity && 
                state.getBlock() instanceof net.minecraft.block.ChestBlock chestBlock) {
                return net.minecraft.block.ChestBlock.getInventory(chestBlock, state, world, pos, true);
            }
            return inventory;
        }
        return null;
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
        nbt.putInt("extract_cooldown", extractCooldown);
        nbt.putInt("push_cooldown", pushCooldown);
    }
    
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        Inventories.readNbt(nbt, inventory, registries);
        extractCooldown = nbt.getInt("extract_cooldown").orElse(0);
        pushCooldown = nbt.getInt("push_cooldown").orElse(0);
    }
}