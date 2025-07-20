package starduster.circuitmod.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;


public class CrusherScreenHandler extends ScreenHandler {
    private final Inventory inventory;
    private final PropertyDelegate propertyDelegate;


    // Client constructor
    public CrusherScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(6), new ArrayPropertyDelegate(2));
    }

    // Server constructor
    public CrusherScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate arrayPropertyDelegate) {
        super(ModScreenHandlers.CRUSHER_SCREEN_HANDLER, syncId);
        this.inventory = inventory;
        this.propertyDelegate = arrayPropertyDelegate;

        // Add property delegate for progress bars
        this.addProperties(arrayPropertyDelegate);
        
        // Add slots
        // Input slot
        this.addSlot(new Slot(inventory, 0, 80, 18));

        // Output slots
        this.addSlot(new Slot(inventory, 1, 44, 54));
        this.addSlot(new Slot(inventory, 2, 62, 54));
        this.addSlot(new Slot(inventory, 3, 80, 54));
        this.addSlot(new Slot(inventory, 4, 98, 54));
        this.addSlot(new Slot(inventory, 5, 116, 54));

        // Player inventory slots
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }
        
        // Player hotbar slots
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
        }
    }
    
    @Override
    public boolean canUse(PlayerEntity player) {
        return this.inventory.canPlayerUse(player);
    }
    
    // Handle quick transfer (shift-clicking)
    @Override
    public ItemStack quickMove(PlayerEntity player, int invSlot) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(invSlot);
        
        if (slot != null && slot.hasStack()) {
            ItemStack slotStack = slot.getStack();
            itemStack = slotStack.copy();
            
            // If clicking on output slots (1-5), move to player inventory
            if (invSlot >= 1 && invSlot <= 5) {
                if (!this.insertItem(slotStack, 6, 42, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickTransfer(slotStack, itemStack);
            } 
            // If clicking on player inventory, try to move to input slot
            else if (invSlot >= 6) {
                if (!this.insertItem(slotStack, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            }
            
            if (slotStack.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
            
            if (slotStack.getCount() == itemStack.getCount()) {
                return ItemStack.EMPTY;
            }
            
            slot.onTakeItem(player, slotStack);
        }
        
        return itemStack;
    }
    
    // Utility methods
    public boolean isCrushing() {
        return propertyDelegate.get(0) > 0;
    }
    
    public int getScaledArrowProgress() {
        int progress = this.propertyDelegate.get(0);
        int maxProgress = this.propertyDelegate.get(1);
        int arrowPixelSize = 15; // This is the width in pixels of your arrow

        if(maxProgress != 0 && progress != 0) {
            return progress * arrowPixelSize / maxProgress;
        } else {
            return 0;
        }
    }
} 