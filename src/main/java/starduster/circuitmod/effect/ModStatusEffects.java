package starduster.circuitmod.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;

public class ModStatusEffects {
    
    // The custom pulse velocity effect - acts as a marker for players with pulse velocity
    public static final StatusEffect PULSE_VELOCITY_EFFECT = new PulseVelocityEffect();
    
    // Registry entry for the effect (what we use with hasStatusEffect)
    public static RegistryEntry<StatusEffect> PULSE_VELOCITY;
    
    public static void initialize() {
        Circuitmod.LOGGER.info("Registering status effects for CircuitMod");
        
        // Register the effect and get the registry entry
        PULSE_VELOCITY = Registry.registerReference(Registries.STATUS_EFFECT, 
            Identifier.of(Circuitmod.MOD_ID, "pulse_velocity"), 
            PULSE_VELOCITY_EFFECT);
    }
    
    /**
     * Custom status effect that marks a player as having pulse velocity
     */
    public static class PulseVelocityEffect extends StatusEffect {
        public PulseVelocityEffect() {
            // Neutral effect with a blue-ish color
            super(StatusEffectCategory.NEUTRAL, 0x7F9FFF); // Light blue color
        }
        
        @Override
        public boolean canApplyUpdateEffect(int duration, int amplifier) {
            // We don't need to do anything each tick, this is just a marker effect
            return false;
        }
    }
} 