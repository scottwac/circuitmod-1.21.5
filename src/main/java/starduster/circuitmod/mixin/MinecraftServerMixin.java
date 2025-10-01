package starduster.circuitmod.mixin;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.luna.LunaTimeManager;

/**
 * Mixin to intercept time synchronization packets for the Luna dimension.
 * Prevents client-side time interpolation when using custom time rates.
 */
@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
    
    private static boolean debugLogged = false;
    
    /**
     * Intercepts the time sync broadcast to send custom packets for Luna dimension.
     * This prevents the client from doing its own time interpolation.
     */
    @Redirect(
        method = "tickWorlds",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/PlayerManager;sendToDimension(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/registry/RegistryKey;)V"
        )
    )
    private void circuitmod$customLunaTimeSync(PlayerManager playerManager, Packet<?> packet, RegistryKey<World> dimension) {
        // Check if this is a time update packet for Luna dimension
        if (packet instanceof WorldTimeUpdateS2CPacket && dimension.getValue().equals(Identifier.of("circuitmod", "luna"))) {
            @SuppressWarnings("resource")
            MinecraftServer server = (MinecraftServer) (Object) this;
            ServerWorld lunaWorld = server.getWorld(dimension);
            
            if (lunaWorld != null) {
                LunaTimeManager timeManager = LunaTimeManager.getInstance(lunaWorld);
                
                if (timeManager != null && !timeManager.isNormalTimeRate()) {
                    // Log once for debugging
                    if (!debugLogged) {
                        Circuitmod.LOGGER.info("[LUNA-TIME] MinecraftServerMixin fired! Sending custom time sync packet (doDaylightCycle=false) to Luna dimension.");
                        debugLogged = true;
                    }
                    
                    // Send custom packet with doDaylightCycle = false to prevent client-side time updates
                    WorldTimeUpdateS2CPacket customPacket = new WorldTimeUpdateS2CPacket(
                        lunaWorld.getTime(),
                        lunaWorld.getTimeOfDay(),
                        false  // doDaylightCycle = false prevents client from updating time locally
                    );
                    playerManager.sendToDimension(customPacket, dimension);
                    return;
                }
            }
        }
        
        // For all other cases, use the original packet
        playerManager.sendToDimension(packet, dimension);
    }
}

