package starduster.circuitmod.entity;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Custom server player entity that uses CustomPlayerInventory.
 * This replaces the standard ServerPlayerEntity to add oxygen tank slots.
 */
public class CustomServerPlayerEntity extends ServerPlayerEntity {
    
    public CustomServerPlayerEntity(MinecraftServer server, ServerWorld world, GameProfile profile, SyncedClientOptions clientOptions) {
        super(server, world, profile, clientOptions);
        // Note: The inventory is already created in PlayerEntity constructor before this runs
        // We need to use a mixin to replace it at creation time
        System.out.println("[CircuitMod] CustomServerPlayerEntity created for " + profile.getName());
    }
    
    /**
     * Gets the custom inventory if available.
     */
    public CustomPlayerInventory getCustomInventory() {
        if (this.getInventory() instanceof CustomPlayerInventory customInv) {
            return customInv;
        }
        return null;
    }
}
