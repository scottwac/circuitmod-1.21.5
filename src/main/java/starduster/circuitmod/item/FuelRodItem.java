package starduster.circuitmod.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

public class FuelRodItem extends Item {
    public static final int MAX_DURABILITY_SECONDS = 21600; // 6 hours in seconds (6 * 60 * 60)
    
    public FuelRodItem(Settings settings) {
        super(settings.maxDamage(MAX_DURABILITY_SECONDS));
    }
    
    /**
     * Reduces the durability of a fuel rod by the specified amount in seconds
     * @param stack The fuel rod item stack
     * @param seconds The amount of seconds to reduce durability by
     * @return true if the rod was consumed (durability reached 0), false otherwise
     */
    public static boolean reduceDurability(ItemStack stack, int seconds) {
        if (stack.isEmpty() || !(stack.getItem() instanceof FuelRodItem)) {
            return false;
        }
        
        int currentDamage = stack.getDamage();
        int newDamage = currentDamage + seconds;
        
        if (newDamage >= MAX_DURABILITY_SECONDS) {
            // Rod is consumed
            stack.setCount(0);
            return true;
        } else {
            // Reduce durability
            stack.setDamage(newDamage);
            return false;
        }
    }
    
    /**
     * Gets the remaining durability in seconds
     * @param stack The fuel rod item stack
     * @return The remaining durability in seconds
     */
    public static int getRemainingSeconds(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof FuelRodItem)) {
            return 0;
        }
        
        int currentDamage = stack.getDamage();
        return Math.max(0, MAX_DURABILITY_SECONDS - currentDamage);
    }
    
    /**
     * Gets the remaining durability percentage (0.0 to 1.0)
     * @param stack The fuel rod item stack
     * @return The remaining durability as a percentage
     */
    public static float getDurabilityPercentage(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof FuelRodItem)) {
            return 0.0f;
        }
        
        int currentDamage = stack.getDamage();
        return 1.0f - ((float) currentDamage / MAX_DURABILITY_SECONDS);
    }
    
    /**
     * Checks if a fuel rod has any remaining durability
     * @param stack The fuel rod item stack
     * @return true if the rod has remaining durability, false otherwise
     */
    public static boolean hasDurability(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof FuelRodItem)) {
            return false;
        }
        
        return stack.getDamage() < MAX_DURABILITY_SECONDS;
    }
    
    /**
     * Gets the total durability in seconds
     * @return The total durability in seconds
     */
    public static int getMaxDurabilitySeconds() {
        return MAX_DURABILITY_SECONDS;
    }
} 