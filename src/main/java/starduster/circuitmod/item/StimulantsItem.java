package starduster.circuitmod.item;

import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class StimulantsItem extends Item {
    
    public StimulantsItem(Settings settings) {
        super(settings.food(new FoodComponent.Builder()
            .nutrition(1) // Low hunger restoration since it's more about the effects
            .saturationModifier(0.1f) // Low saturation
            .alwaysEdible() // Can be eaten even when full
            .build()));
    }
    
    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (!world.isClient) {
            // 4x speed (Speed IV = 3, but we want 4x so Speed VII = 6)
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 6000, 4)); // 5 minutes, Speed VII
            
            // 4x jump boost (Jump Boost IV should be enough for 4x jump height)
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 6000, 3)); // 5 minutes, Jump Boost IV
            
           
            // Haste for faster mining/attacking
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 6000, 1)); // 5 minutes, Haste II
            
            // Optional: Add some negative effects for balance
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 1200, 1)); // 1 minute of hunger
        }
        
        return super.finishUsing(stack, world, user);
    }
} 