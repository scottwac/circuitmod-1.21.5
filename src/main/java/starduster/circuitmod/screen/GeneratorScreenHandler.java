package starduster.circuitmod.screen;

import dev.architectury.registry.fuel.fabric.FuelRegistryImpl;
import net.fabricmc.fabric.api.registry.FuelRegistryEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.FuelRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;

public class GeneratorScreenHandler extends ScreenHandler {
    private final Inventory inventory;
    private final PropertyDelegate propertyDelegate;

    // Client constructor
    public GeneratorScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(1), new ArrayPropertyDelegate(3));
    }
    
    // Server constructor
    public GeneratorScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate propertyDelegate) {
        super(ModScreenHandlers.GENERATOR_SCREEN_HANDLER, syncId);
        this.inventory = inventory;
        this.propertyDelegate = propertyDelegate;

        // Add property delegate for fuel burning and power production
        this.addProperties(propertyDelegate);
        
        // Add generator slots
        // Fuel slot
        this.addSlot(new GeneratorFuelSlot(inventory, 0, 125, 54));
        
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
            
            if (invSlot == 0) {
                // From fuel slot to player inventory
                if (!this.insertItem(slotStack, 1, 37, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // From player inventory to fuel slot
                if (isFuel(slotStack)) {
                    if (!this.insertItem(slotStack, 0, 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
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
    public boolean isBurning() {
        return propertyDelegate.get(0) > 0;
    }
    
    public int getScaledFuelProgress() {
        int burnTime = this.propertyDelegate.get(0);
        int maxBurnTime = this.propertyDelegate.get(1);
        int spriteHeight = 14;

        if (maxBurnTime != 0 && burnTime != 0) {
            return burnTime * spriteHeight / maxBurnTime;
        } else {
            return 0;
        }
    }
    
    public int getPowerProduction() {
        return this.propertyDelegate.get(2);
    }

    private boolean isFuel(ItemStack stack) {
        // Check if the item is a valid fuel using Minecraft's fuel registry
        return !stack.isEmpty();// && stack.isIn(ItemTags.COALS);
    }

    // Custom fuel slot class for the Generator
    public static class GeneratorFuelSlot extends Slot {
        public GeneratorFuelSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return !stack.isEmpty();// && stack.isIn(ItemTags.COALS);
        }

    }

} 