package starduster.circuitmod.mixin;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.luna.LunaTimeManager;

/**
 * Mixin to intercept time updates in ServerWorld and apply custom Luna time cycle.
 */
@Mixin(ServerWorld.class)
public class ServerWorldMixin {
    
    private static boolean debugLogged = false;
    
    /**
     * Intercepts the time tick to apply custom time progression for Luna dimension.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void circuitmod$applyLunaTimeCycle(CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;
        
        // Only apply to Luna dimension
        if (world.getRegistryKey().getValue().equals(Identifier.of("circuitmod", "luna"))) {
            LunaTimeManager timeManager = LunaTimeManager.getInstance(world);
            
            if (timeManager != null) {
                // Log once for debugging
                if (!debugLogged) {
                    Circuitmod.LOGGER.info("[LUNA-TIME] ServerWorldMixin fired! Custom time cycle active for Luna dimension.");
                    debugLogged = true;
                }
                
                // Let the time manager handle time updates
                // This will be called before vanilla time updates
                timeManager.tickTime();
            }
        }
    }
}

