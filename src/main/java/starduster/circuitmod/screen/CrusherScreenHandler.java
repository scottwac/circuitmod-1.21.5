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
        this(syncId, playerInventory, new SimpleInventory(3), new ArrayPropertyDelegate(4));
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

        // Output slot
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
            
            if (invSlot == 2) {
                // From output slot to player inventory
                if (!this.insertItem(slotStack, 3, 39, true)) {
                    return ItemStack.EMPTY;
                }
                
                slot.onQuickTransfer(slotStack, itemStack);
            } else {
                // From bloomery slots to player inventory
                if (!this.insertItem(slotStack, 3, 39, false)) {
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
    public boolean isSmelting() {
        return propertyDelegate.get(0) > 0;
    }
    public boolean isBurning() {
        return propertyDelegate.get(2) > 0;
    }
    //TODO refactor delegates
    public int getScaledArrowProgress() {
        int progress = this.propertyDelegate.get(0);
        int maxProgress = this.propertyDelegate.get(1); // Max Progress
        int arrowPixelSize = 24; // This is the width in pixels of your arrow

        //return maxProgress != 0 && progress != 0 ? progress * arrowPixelSize / maxProgress : 0;

        if(maxProgress != 0 && progress != 0) {
            return progress * arrowPixelSize / maxProgress;
        } else {
            return 0;
        }
    }

} 