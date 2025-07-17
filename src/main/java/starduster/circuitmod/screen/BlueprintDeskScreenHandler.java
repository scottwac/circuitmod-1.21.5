package starduster.circuitmod.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;
import starduster.circuitmod.item.BlueprintItem;
import starduster.circuitmod.item.ModItems;

public class BlueprintDeskScreenHandler extends ScreenHandler {
    private final Inventory inventory;
    private final PropertyDelegate propertyDelegate;
    
    // Constructor for server-side (with block entity)
    public BlueprintDeskScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate propertyDelegate) {
        super(ModScreenHandlers.BLUEPRINT_DESK_SCREEN_HANDLER, syncId);
        this.inventory = inventory;
        this.propertyDelegate = propertyDelegate;
        
        // Add properties for client synchronization
        this.addProperties(propertyDelegate);
        
        // Blueprint item slot (center of desk interface)
        this.addSlot(new BlueprintSlot(inventory, 0, 80, 35));
        
        // Player inventory (standard layout)
        // Main inventory (3x9 grid)
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }
        
        // Hotbar (1x9 grid)
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
        }
    }
    
    // Constructor for client-side (without block entity)
    public BlueprintDeskScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(1), new ArrayPropertyDelegate(3));
    }
    
    @Override
    public boolean canUse(PlayerEntity player) {
        return this.inventory.canPlayerUse(player);
    }
    
    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slotObject = this.slots.get(slot);
        
        if (slotObject != null && slotObject.hasStack()) {
            ItemStack originalStack = slotObject.getStack();
            newStack = originalStack.copy();
            
            if (slot < this.inventory.size()) {
                // Moving from blueprint desk to player inventory
                if (!this.insertItem(originalStack, this.inventory.size(), this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Moving from player inventory to blueprint desk
                if (originalStack.getItem() instanceof BlueprintItem || originalStack.isOf(ModItems.BLUEPRINT)) {
                    if (!this.insertItem(originalStack, 0, this.inventory.size(), false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    return ItemStack.EMPTY;
                }
            }
            
            if (originalStack.isEmpty()) {
                slotObject.setStack(ItemStack.EMPTY);
            } else {
                slotObject.markDirty();
            }
        }
        
        return newStack;
    }
    
    // Property getters for client-side display
    public boolean hasValidPartner() {
        return this.propertyDelegate.get(0) == 1;
    }
    
    public boolean isScanning() {
        return this.propertyDelegate.get(1) == 1;
    }
    
    public int getScanProgress() {
        return this.propertyDelegate.get(2);
    }
    
    // Get the block entity position (for networking)
    public BlockPos getBlockEntityPos() {
        // This is a bit hacky, but we can get the position from the inventory if it's a block entity
        if (inventory instanceof starduster.circuitmod.block.entity.BlueprintDeskBlockEntity blockEntity) {
            return blockEntity.getPos();
        }
        return null;
    }
    
    /**
     * Custom slot for blueprint items
     */
    private static class BlueprintSlot extends Slot {
        public BlueprintSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }
        
        @Override
        public boolean canInsert(ItemStack stack) {
            // Only allow blueprint items
            return stack.getItem() instanceof BlueprintItem || stack.isOf(ModItems.BLUEPRINT);
        }
        
        @Override
        public int getMaxItemCount() {
            return 1; // Only one blueprint at a time
        }
    }
    
    /**
     * Simple inventory implementation for client-side
     */
    private static class SimpleInventory implements Inventory {
        private final ItemStack[] stacks;
        
        public SimpleInventory(int size) {
            this.stacks = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                this.stacks[i] = ItemStack.EMPTY;
            }
        }
        
        @Override
        public int size() {
            return this.stacks.length;
        }
        
        @Override
        public boolean isEmpty() {
            for (ItemStack stack : this.stacks) {
                if (!stack.isEmpty()) {
                    return false;
                }
            }
            return true;
        }
        
        @Override
        public ItemStack getStack(int slot) {
            return this.stacks[slot];
        }
        
        @Override
        public ItemStack removeStack(int slot, int amount) {
            ItemStack stack = this.stacks[slot];
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            
            if (stack.getCount() <= amount) {
                this.stacks[slot] = ItemStack.EMPTY;
                return stack;
            } else {
                ItemStack split = stack.split(amount);
                return split;
            }
        }
        
        @Override
        public ItemStack removeStack(int slot) {
            ItemStack stack = this.stacks[slot];
            this.stacks[slot] = ItemStack.EMPTY;
            return stack;
        }
        
        @Override
        public void setStack(int slot, ItemStack stack) {
            this.stacks[slot] = stack;
        }
        
        @Override
        public void markDirty() {
            // No-op for client-side
        }
        
        @Override
        public boolean canPlayerUse(PlayerEntity player) {
            return true;
        }
        
        @Override
        public void clear() {
            for (int i = 0; i < this.stacks.length; i++) {
                this.stacks[i] = ItemStack.EMPTY;
            }
        }
    }
    
    /**
     * Simple property delegate implementation for client-side
     */
    private static class ArrayPropertyDelegate implements PropertyDelegate {
        private final int[] properties;
        
        public ArrayPropertyDelegate(int size) {
            this.properties = new int[size];
        }
        
        @Override
        public int get(int index) {
            return this.properties[index];
        }
        
        @Override
        public void set(int index, int value) {
            this.properties[index] = value;
        }
        
        @Override
        public int size() {
            return this.properties.length;
        }
    }
} 