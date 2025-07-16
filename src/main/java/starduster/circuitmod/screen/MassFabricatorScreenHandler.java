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
import starduster.circuitmod.block.entity.MassFabricatorBlockEntity;

public class MassFabricatorScreenHandler extends ScreenHandler {
    private final Inventory inventory;
    private final PropertyDelegate propertyDelegate;

    // Client constructor
    public MassFabricatorScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(2), new ArrayPropertyDelegate(5));
    }

    // Server constructor
    public MassFabricatorScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate propertyDelegate) {
        super(ModScreenHandlers.MASS_FABRICATOR_SCREEN_HANDLER, syncId);
        this.inventory = inventory;
        this.propertyDelegate = propertyDelegate;
        this.addProperties(propertyDelegate);

        // Input slot (Nether Star)
        this.addSlot(new Slot(inventory, MassFabricatorBlockEntity.INPUT_SLOT, 56, 45));
        // Output slot (result)
        this.addSlot(new Slot(inventory, MassFabricatorBlockEntity.OUTPUT_SLOT, 116, 36));

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

    @Override
    public ItemStack quickMove(PlayerEntity player, int invSlot) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(invSlot);
        if (slot != null && slot.hasStack()) {
            ItemStack slotStack = slot.getStack();
            itemStack = slotStack.copy();
            if (invSlot == MassFabricatorBlockEntity.OUTPUT_SLOT) {
                // Output to player inventory
                if (!this.insertItem(slotStack, 2, 38, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickTransfer(slotStack, itemStack);
            } else if (invSlot != MassFabricatorBlockEntity.INPUT_SLOT) {
                // Player inventory to input slot
                if (!this.insertItem(slotStack, MassFabricatorBlockEntity.INPUT_SLOT, MassFabricatorBlockEntity.INPUT_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Input slot to player inventory
                if (!this.insertItem(slotStack, 2, 38, false)) {
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

    // Property accessors for GUI
    public int getProgress() { return propertyDelegate.get(0); }
    public int getMaxProgress() { return propertyDelegate.get(1); }
    public int getEnergyStored() { return propertyDelegate.get(2); }
    public int getEnergyRequired() { return propertyDelegate.get(3); }
    public int getSelectedResource() { return propertyDelegate.get(4); }
} 