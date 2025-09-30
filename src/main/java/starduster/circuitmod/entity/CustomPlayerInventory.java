package starduster.circuitmod.entity;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.collection.DefaultedList;
import starduster.circuitmod.mixin.PlayerEntityAccessor;

/**
 * Custom player inventory that extends the regular PlayerInventory
 * to add 2 oxygen tank slots.
 */
public class CustomPlayerInventory extends PlayerInventory {
    
    // Oxygen tank slots (2 slots)
    public final DefaultedList<ItemStack> oxygenTanks = DefaultedList.ofSize(2, ItemStack.EMPTY);
    
    // Slot indices for oxygen tanks (after armor slots)
    public static final int OXYGEN_TANK_1_SLOT = 41; // After offhand (40)
    public static final int OXYGEN_TANK_2_SLOT = 42;
    
    public CustomPlayerInventory(PlayerEntity player) {
        super(player, ((PlayerEntityAccessor) player).getEquipment());
    }
    
    @Override
    public int size() {
        return super.size() + 2; // Add 2 oxygen tank slots
    }
    
    @Override
    public ItemStack getStack(int slot) {
        if (slot == OXYGEN_TANK_1_SLOT) {
            return this.oxygenTanks.get(0);
        } else if (slot == OXYGEN_TANK_2_SLOT) {
            return this.oxygenTanks.get(1);
        }
        return super.getStack(slot);
    }
    
    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot == OXYGEN_TANK_1_SLOT) {
            this.oxygenTanks.set(0, stack);
            this.markDirty();
        } else if (slot == OXYGEN_TANK_2_SLOT) {
            this.oxygenTanks.set(1, stack);
            this.markDirty();
        } else {
            super.setStack(slot, stack);
        }
    }
    
    @Override
    public ItemStack removeStack(int slot, int amount) {
        if (slot == OXYGEN_TANK_1_SLOT) {
            ItemStack result = this.oxygenTanks.get(0).split(amount);
            if (!result.isEmpty()) {
                this.markDirty();
            }
            return result;
        } else if (slot == OXYGEN_TANK_2_SLOT) {
            ItemStack result = this.oxygenTanks.get(1).split(amount);
            if (!result.isEmpty()) {
                this.markDirty();
            }
            return result;
        }
        return super.removeStack(slot, amount);
    }
    
    @Override
    public ItemStack removeStack(int slot) {
        if (slot == OXYGEN_TANK_1_SLOT) {
            ItemStack result = this.oxygenTanks.get(0);
            this.oxygenTanks.set(0, ItemStack.EMPTY);
            if (!result.isEmpty()) {
                this.markDirty();
            }
            return result;
        } else if (slot == OXYGEN_TANK_2_SLOT) {
            ItemStack result = this.oxygenTanks.get(1);
            this.oxygenTanks.set(1, ItemStack.EMPTY);
            if (!result.isEmpty()) {
                this.markDirty();
            }
            return result;
        }
        return super.removeStack(slot);
    }
    
    @Override
    public void dropAll() {
        super.dropAll();
        
        // Drop oxygen tanks
        for (int i = 0; i < this.oxygenTanks.size(); i++) {
            ItemStack stack = this.oxygenTanks.get(i);
            if (!stack.isEmpty()) {
                this.player.dropItem(stack, true);
                this.oxygenTanks.set(i, ItemStack.EMPTY);
            }
        }
    }
    
    @Override
    public NbtList writeNbt(NbtList nbtList) {
        // Write regular inventory
        nbtList = super.writeNbt(nbtList);
        
        System.out.println("[CircuitMod] CustomPlayerInventory.writeNbt - Writing oxygen tanks");
        
        // Write oxygen tanks
        for (int i = 0; i < this.oxygenTanks.size(); i++) {
            ItemStack stack = this.oxygenTanks.get(i);
            if (!stack.isEmpty()) {
                NbtCompound nbtCompound = new NbtCompound();
                nbtCompound.putByte("Slot", (byte) (OXYGEN_TANK_1_SLOT + i));
                nbtList.add(stack.toNbt(this.player.getRegistryManager(), nbtCompound));
                System.out.println("[CircuitMod] Wrote oxygen tank " + (i+1) + " to slot " + (OXYGEN_TANK_1_SLOT + i) + ": " + stack);
            } else {
                System.out.println("[CircuitMod] Oxygen tank " + (i+1) + " is empty, not writing");
            }
        }
        
        System.out.println("[CircuitMod] Total items in NBT list: " + nbtList.size());
        
        return nbtList;
    }
    
    @Override
    public void readNbt(NbtList nbtList) {
        System.out.println("[CircuitMod] CustomPlayerInventory.readNbt called with " + nbtList.size() + " items");
        
        // Clear oxygen tanks first
        this.oxygenTanks.clear();
        for (int i = 0; i < 2; i++) {
            this.oxygenTanks.add(ItemStack.EMPTY);
        }
        
        // Separate the NBT list into oxygen tanks and regular items
        NbtList regularItems = new NbtList();
        
        for (int i = 0; i < nbtList.size(); i++) {
            NbtCompound nbtCompound = nbtList.getCompoundOrEmpty(i);
            int slot = nbtCompound.getByte("Slot", (byte)0) & 255;
            
            System.out.println("[CircuitMod] Reading slot " + slot);
            
            if (slot == OXYGEN_TANK_1_SLOT) {
                ItemStack stack = ItemStack.fromNbt(this.player.getRegistryManager(), nbtCompound).orElse(ItemStack.EMPTY);
                this.oxygenTanks.set(0, stack);
                System.out.println("[CircuitMod] Loaded oxygen tank 1: " + stack);
            } else if (slot == OXYGEN_TANK_2_SLOT) {
                ItemStack stack = ItemStack.fromNbt(this.player.getRegistryManager(), nbtCompound).orElse(ItemStack.EMPTY);
                this.oxygenTanks.set(1, stack);
                System.out.println("[CircuitMod] Loaded oxygen tank 2: " + stack);
            } else {
                // Add to regular items list for parent to handle
                regularItems.add(nbtCompound);
            }
        }
        
        // Let parent handle regular inventory slots
        super.readNbt(regularItems);
        
        System.out.println("[CircuitMod] After readNbt - Oxygen tank 1: " + this.oxygenTanks.get(0));
        System.out.println("[CircuitMod] After readNbt - Oxygen tank 2: " + this.oxygenTanks.get(1));
    }
    
    /**
     * Gets an oxygen tank from the specified slot.
     * @param slot 0 for first tank, 1 for second tank
     * @return The oxygen tank ItemStack
     */
    public ItemStack getOxygenTank(int slot) {
        if (slot >= 0 && slot < this.oxygenTanks.size()) {
            return this.oxygenTanks.get(slot);
        }
        return ItemStack.EMPTY;
    }
    
    /**
     * Sets an oxygen tank in the specified slot.
     * @param slot 0 for first tank, 1 for second tank
     * @param stack The ItemStack to set
     */
    public void setOxygenTank(int slot, ItemStack stack) {
        if (slot >= 0 && slot < this.oxygenTanks.size()) {
            this.oxygenTanks.set(slot, stack);
            this.markDirty();
        }
    }
}
