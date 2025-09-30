package starduster.circuitmod.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Oxygen tank item that can be placed in the oxygen tank slots.
 * Stores oxygen for use in space or other environments.
 */
public class OxygenTankItem extends Item {
    
    private final int maxOxygen;
    
    public OxygenTankItem(Settings settings, int maxOxygen) {
        super(settings);
        this.maxOxygen = maxOxygen;
    }
    
    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, net.minecraft.component.type.TooltipDisplayComponent displayComponent, java.util.function.Consumer<Text> consumer, net.minecraft.item.tooltip.TooltipType type) {
        super.appendTooltip(stack, context, displayComponent, consumer, type);
        
        int oxygen = getOxygen(stack);
        consumer.accept(Text.translatable("item.circuitmod.oxygen_tank.oxygen", oxygen, maxOxygen)
                .formatted(Formatting.BLUE));
        
        if (oxygen == 0) {
            consumer.accept(Text.translatable("item.circuitmod.oxygen_tank.empty")
                    .formatted(Formatting.RED));
        }
    }
    
    /**
     * Gets the current oxygen level in the tank.
     * @param stack The oxygen tank ItemStack
     * @return Current oxygen level
     */
    public int getOxygen(ItemStack stack) {
        return stack.getOrDefault(net.minecraft.component.DataComponentTypes.DAMAGE, 0);
    }
    
    /**
     * Sets the oxygen level in the tank.
     * @param stack The oxygen tank ItemStack
     * @param oxygen New oxygen level
     */
    public void setOxygen(ItemStack stack, int oxygen) {
        stack.set(net.minecraft.component.DataComponentTypes.DAMAGE, Math.max(0, Math.min(oxygen, maxOxygen)));
    }
    
    /**
     * Consumes oxygen from the tank.
     * @param stack The oxygen tank ItemStack
     * @param amount Amount to consume
     * @return Amount actually consumed
     */
    public int consumeOxygen(ItemStack stack, int amount) {
        int current = getOxygen(stack);
        int consumed = Math.min(current, amount);
        setOxygen(stack, current - consumed);
        return consumed;
    }
    
    /**
     * Adds oxygen to the tank.
     * @param stack The oxygen tank ItemStack
     * @param amount Amount to add
     * @return Amount actually added
     */
    public int addOxygen(ItemStack stack, int amount) {
        int current = getOxygen(stack);
        int space = maxOxygen - current;
        int added = Math.min(space, amount);
        setOxygen(stack, current + added);
        return added;
    }
    
    /**
     * Checks if the tank is empty.
     * @param stack The oxygen tank ItemStack
     * @return true if empty
     */
    public boolean isEmpty(ItemStack stack) {
        return getOxygen(stack) == 0;
    }
    
    /**
     * Checks if the tank is full.
     * @param stack The oxygen tank ItemStack
     * @return true if full
     */
    public boolean isFull(ItemStack stack) {
        return getOxygen(stack) >= maxOxygen;
    }
    
    /**
     * Gets the maximum oxygen capacity.
     * @return Maximum oxygen capacity
     */
    public int getMaxOxygen() {
        return maxOxygen;
    }
}
