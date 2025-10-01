package starduster.circuitmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.util.Identifier;
import net.minecraft.world.LunarWorldView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin to override sky angle calculation for Luna dimension.
 * This ensures the sky brightness follows the 192,000 tick cycle instead of the normal 24,000 tick cycle.
 */
@Mixin(LunarWorldView.class)
public interface WorldMixin {
    
    /**
     * Override getSkyAngle for Luna dimension to use the 192,000 tick cycle.
     * This prevents the sky from darkening at 12,000 ticks and aligns stars/darkness with the Luna sun.
     */
    @ModifyReturnValue(method = "getSkyAngle", at = @At("RETURN"))
    default float circuitmod$lunaCustomSkyAngle(float original) {
        World world = (World) this;
        
        // Only apply to Luna dimension
        if (world.getRegistryKey().getValue().equals(Identifier.of("circuitmod", "luna"))) {
            long timeOfDay = world.getTimeOfDay();
            
            // Use 192,000 tick cycle instead of 24,000
            // Match the Luna sun calculation: (worldTime + 30000) / 192000
            // The +30000 offset makes Lunar noon align with Overworld midnight
            long lunaTime = (timeOfDay + 30000L) % 192000L;
            float lunaAngle = (float) lunaTime / 192000.0F;
            
            // Adjust for Minecraft's sky angle calculation
            // Minecraft's getSkyAngle expects 0.0 at sunrise, 0.5 at sunset
            float adjustedAngle = lunaAngle - 0.25F;
            if (adjustedAngle < 0.0F) {
                adjustedAngle += 1.0F;
            }
            
            return adjustedAngle;
        }
        
        return original;
    }
}

