package starduster.circuitmod.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.screen.ModScreenHandlers;
import starduster.circuitmod.screen.QuarryScreenHandler;

import org.jetbrains.annotations.Nullable;

public class QuarryBlockEntity extends BlockEntity implements SidedInventory, NamedScreenHandlerFactory {
    // The current progress of the quarry (example property)
    private int progress = 0;
    private static final int MAX_PROGRESS = 100;
    
    // Inventory with chest size (27 slots)
    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(27, ItemStack.EMPTY);

    public QuarryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QUARRY_BLOCK_ENTITY, pos, state);
    }

    // Save data to NBT when the block is saved
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putInt("progress", this.progress);
        Inventories.writeNbt(nbt, this.inventory, registries);
    }

    // Load data from NBT when the block is loaded
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        this.progress = nbt.getInt("progress").orElse(0);
        Inventories.readNbt(nbt, this.inventory, registries);
    }

    // The tick method called by the ticker in QuarryBlock
    public static void tick(World world, BlockPos pos, BlockState state, QuarryBlockEntity blockEntity) {
        if (world.isClient()) {
            return;
        }

        // Simple progress increment logic
        if (blockEntity.progress < MAX_PROGRESS) {
            blockEntity.progress++;
            blockEntity.markDirty();

            // Log every 20 ticks (about 1 second)
            if (blockEntity.progress % 20 == 0) {
                Circuitmod.LOGGER.info("Quarry progress: " + blockEntity.progress + "/" + MAX_PROGRESS);
            }
        } else {
            // Reset progress when complete
            blockEntity.progress = 0;
            Circuitmod.LOGGER.info("Quarry operation complete!");
            
            // TODO: Mining logic - add blocks to inventory
            blockEntity.mineBlock(world, pos);
        }
    }
    
    // Mining logic to add blocks to inventory
    private void mineBlock(World world, BlockPos pos) {
        // Get a block under the quarry - this is a simple example
        // Real implementation would scan an area or mine in a pattern
        BlockPos miningPos = pos.down(2); // 2 blocks below the quarry
        BlockState blockState = world.getBlockState(miningPos);
        
        // Skip if it's air or bedrock
        if (blockState.isAir() || blockState.getHardness(world, miningPos) < 0) {
            return;
        }
        
        // Get drops from the block
        ItemStack minedItem = new ItemStack(blockState.getBlock().asItem());
        
        // Add to inventory if there's space
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.get(i);
            if (stack.isEmpty()) {
                inventory.set(i, minedItem);
                world.removeBlock(miningPos, false);
                markDirty();
                return;
            } else if (ItemStack.areItemsEqual(stack, minedItem) && stack.getCount() < stack.getMaxCount()) {
                stack.increment(1);
                world.removeBlock(miningPos, false);
                markDirty();
                return;
            }
        }
    }
    
    // Inventory implementation
    @Override
    public int size() {
        return inventory.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getStack(int slot) {
        return inventory.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        return Inventories.splitStack(inventory, slot, amount);
    }

    @Override
    public ItemStack removeStack(int slot) {
        return Inventories.removeStack(inventory, slot);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        inventory.set(slot, stack);
        if (stack.getCount() > getMaxCountPerStack()) {
            stack.setCount(getMaxCountPerStack());
        }
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        if (world.getBlockEntity(pos) != this) {
            return false;
        }
        return player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clear() {
        inventory.clear();
    }
    
    // SidedInventory implementation for automation compatibility
    @Override
    public int[] getAvailableSlots(Direction side) {
        // All slots are available from all sides
        int[] slots = new int[inventory.size()];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = i;
        }
        return slots;
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        return true; // Allow insertion from any side
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        return true; // Allow extraction from any side
    }
    
    // NamedScreenHandlerFactory implementation
    @Override
    public Text getDisplayName() {
        return Text.translatable("block.circuitmod.quarry_block");
    }
    
    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new QuarryScreenHandler(syncId, playerInventory, this);
    }
} 