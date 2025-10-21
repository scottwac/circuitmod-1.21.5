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
import starduster.circuitmod.item.ModItems;


public class RubberTapScreenHandler extends ScreenHandler {
    private final Inventory inventory;
    private final PropertyDelegate propertyDelegate;


    // Client constructor
    public RubberTapScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(3), new ArrayPropertyDelegate(3));
    }

    // Server constructor
    public RubberTapScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate arrayPropertyDelegate) {
        super(ModScreenHandlers.RUBBER_TAP_SCREEN_HANDLER, syncId);
        this.inventory = inventory;
        this.propertyDelegate = arrayPropertyDelegate;

        // Add property delegate for progress bars
        this.addProperties(arrayPropertyDelegate);

        this.addSlot(new Slot(inventory, 0, 80, 49));


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


    //TODO Fix quickMove
    //Handle quick transfer (shift-clicking)
    @Override
    public ItemStack quickMove(PlayerEntity player, int invSlot) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(invSlot);

//        if (slot != null && slot.hasStack()) {
//            ItemStack slotStack = slot.getStack();
//            itemStack = slotStack.copy();
//
//            if (invSlot == 0) {
//                // From output slot to player inventory
//                if (!this.insertItem(slotStack, 3, 39, true)) {
//                    return ItemStack.EMPTY;
//                }
//
//                slot.onQuickTransfer(slotStack, itemStack);
//            } else {
//                // From bloomery slots to player inventory
//                if (!this.insertItem(slotStack, 3, 39, false)) {
//                    return ItemStack.EMPTY;
//                }
//            }
//
//            if (slotStack.isEmpty()) {
//                slot.setStack(ItemStack.EMPTY);
//            } else {
//                slot.markDirty();
//            }
//
//            if (slotStack.getCount() == itemStack.getCount()) {
//                return ItemStack.EMPTY;
//            }
//
//            slot.onTakeItem(player, slotStack);
//        }

        return itemStack;
    }
    
    // Utility methods
    public boolean outItemCount() {
        return propertyDelegate.get(2) > 0;
    }

    public int getScaledArrowProgress() {
        int progress = this.propertyDelegate.get(0);
        int maxProgress = this.propertyDelegate.get(1); // Max Progress
        int arrowPixelSize = 10; // This is the width in pixels of your arrow

        //return maxProgress != 0 && progress != 0 ? progress * arrowPixelSize / maxProgress : 0;

        if(maxProgress != 0 && progress != 0) {
            return progress * arrowPixelSize / maxProgress;
        } else {
            return 0;
        }
    }

    public int isOnValidBlock() {
        int value = this.propertyDelegate.get(2);
        if (value == 1) {
            return 1;
        } else {
            return 0;
        }
    }

    private boolean isRubber(ItemStack stack) {
        // For now, just coal and charcoal
        return stack.isOf(ModItems.NATURAL_RUBBER);
    }

    // Custom fuel slot class for the Bloomery
    public class RubberSlot extends Slot {
        public RubberSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return RubberTapScreenHandler.this.isRubber(stack);
        }

        @Override
        public int getMaxItemCount(ItemStack stack) {return super.getMaxItemCount(stack);}
    }
} 