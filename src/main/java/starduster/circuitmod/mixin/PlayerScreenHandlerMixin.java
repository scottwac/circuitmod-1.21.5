package starduster.circuitmod.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import starduster.circuitmod.screen.OxygenTankSlot;

/**
 * Mixin to add oxygen tank slots to the PlayerScreenHandler.
 * This adds the 2 oxygen tank slots to the player's inventory screen.
 */
@Mixin(PlayerScreenHandler.class)
public class PlayerScreenHandlerMixin {
    
    /**
     * Add oxygen tank slots after the regular slots are added.
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void circuitmod$addOxygenTankSlots(PlayerInventory inventory, boolean onServer, PlayerEntity owner, CallbackInfo ci) {
        PlayerScreenHandler self = (PlayerScreenHandler) (Object) this;
        
        // Adding oxygen tank slots
        
        // Add oxygen tank slots above the shield slot (offhand)
        // Shield is at x=77, y=62, so put oxygen slots at x=77, y=26 and x=77, y=44
        ((ScreenHandlerAccessor) self).invokeAddSlot(new OxygenTankSlot(inventory, 41, 77, 26));
        ((ScreenHandlerAccessor) self).invokeAddSlot(new OxygenTankSlot(inventory, 42, 77, 44));
    }
}
