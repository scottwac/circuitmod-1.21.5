package starduster.circuitmod.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import starduster.circuitmod.block.entity.FluidTankBlockEntity;
import starduster.circuitmod.util.ImplementedInventory;
import net.minecraft.util.math.BlockPos;

public class FluidTankScreenHandler extends ScreenHandler {
    private final Inventory inventory;
    private final PropertyDelegate propertyDelegate;
    private final FluidTankBlockEntity blockEntity;

    // Client constructor
    public FluidTankScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new ImplementedInventory() {
            @Override
            public net.minecraft.util.collection.DefaultedList<ItemStack> getItems() {
                return net.minecraft.util.collection.DefaultedList.ofSize(2, ItemStack.EMPTY);
            }
        }, new ArrayPropertyDelegate(3), null);
    }

    // Client constructor with data (for networking)
    public FluidTankScreenHandler(int syncId, PlayerInventory playerInventory, ModScreenHandlers.FluidTankData data) {
        super(ModScreenHandlers.FLUID_TANK_SCREEN_HANDLER, syncId);
        
        // Get the block position from the data
        BlockPos pos = data.pos();
        // Look up the block entity in the client world
        FluidTankBlockEntity blockEntity = 
            (FluidTankBlockEntity) playerInventory.player.getWorld().getBlockEntity(pos);
        
        // Initialize fields
        this.inventory = blockEntity != null ? blockEntity : new ImplementedInventory() {
            @Override
            public net.minecraft.util.collection.DefaultedList<ItemStack> getItems() {
                return net.minecraft.util.collection.DefaultedList.ofSize(2, ItemStack.EMPTY);
            }
        };
        this.propertyDelegate = blockEntity != null ? blockEntity.getPropertyDelegate() : new ArrayPropertyDelegate(3);
        this.blockEntity = blockEntity;
        
        // Add property delegate for synchronization
        this.addProperties(propertyDelegate);
        
        // Add input bucket slot (left of center)
        this.addSlot(new InputBucketSlot(inventory, 0, 62, 35));
        // Add output bucket slot (right of center)
        this.addSlot(new OutputBucketSlot(inventory, 1, 98, 35));

        // Add player inventory slots
        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);
    }

    // Server constructor
    public FluidTankScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate propertyDelegate) {
        this(syncId, playerInventory, inventory, propertyDelegate, null);
    }

    // Full constructor
    public FluidTankScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate propertyDelegate, FluidTankBlockEntity blockEntity) {
        super(ModScreenHandlers.FLUID_TANK_SCREEN_HANDLER, syncId);
        this.inventory = inventory;
        this.propertyDelegate = propertyDelegate;
        this.blockEntity = blockEntity;

        // Add property delegate for synchronization
        this.addProperties(propertyDelegate);

        // Add input bucket slot (left of center)
        this.addSlot(new InputBucketSlot(inventory, 0, 62, 35));
        // Add output bucket slot (right of center)
        this.addSlot(new OutputBucketSlot(inventory, 1, 98, 35));

        // Add player inventory slots
        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);
    }

    private void addPlayerInventory(PlayerInventory playerInventory) {
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }
    }

    private void addPlayerHotbar(PlayerInventory playerInventory) {
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int invSlot) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(invSlot);
        if (slot != null && slot.hasStack()) {
            ItemStack originalStack = slot.getStack();
            newStack = originalStack.copy();
            if (invSlot < this.inventory.size()) {
                if (!this.insertItem(originalStack, this.inventory.size(), this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.insertItem(originalStack, 0, this.inventory.size(), false)) {
                return ItemStack.EMPTY;
            }

            if (originalStack.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
        }

        return newStack;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return this.inventory.canPlayerUse(player);
    }

    // Input slot accepts all bucket types; Output slot does not accept inserts
    private static class InputBucketSlot extends Slot {
        public InputBucketSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            // Accept empty buckets and filled buckets
            return stack.getItem() == net.minecraft.item.Items.BUCKET
                || stack.getItem() == net.minecraft.item.Items.WATER_BUCKET
                || stack.getItem() == net.minecraft.item.Items.LAVA_BUCKET;
        }
    }

    private static class OutputBucketSlot extends Slot {
        public OutputBucketSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return false;
        }
    }

    // Getters for GUI
    public int getStoredFluidAmount() {
        return propertyDelegate.get(0);
    }

    public int getMaxCapacity() {
        return propertyDelegate.get(1);
    }

    public int getFluidId() {
        return propertyDelegate.get(2);
    }
    
    public FluidTankBlockEntity getBlockEntity() {
        return blockEntity;
    }
} 