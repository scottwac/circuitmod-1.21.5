package starduster.circuitmod.screen;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;

/**
 * Custom slot for oxygen tanks in the player inventory.
 * This slot only accepts items that are oxygen tanks.
 */
public class OxygenTankSlot extends Slot {
    
    public OxygenTankSlot(Inventory inventory, int index, int x, int y) {
        super(inventory, index, x, y);
    }
    
    @Override
    public boolean canInsert(ItemStack stack) {
        return stack.getItem() instanceof starduster.circuitmod.item.OxygenTankItem;
    }
    
    @Override
    public int getMaxItemCount() {
        return 1; // Only one oxygen tank per slot
    }
}
