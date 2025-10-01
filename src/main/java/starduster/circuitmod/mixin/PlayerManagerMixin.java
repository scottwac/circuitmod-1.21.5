package starduster.circuitmod.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import starduster.circuitmod.entity.CustomServerPlayerEntity;

/**
 * Mixin to override player entity creation in PlayerManager.
 * This ensures that our CustomPlayerEntity is used instead of the regular ServerPlayerEntity.
 */
@Mixin(PlayerManager.class)
public class PlayerManagerMixin {
    
    /**
     * Redirects the ServerPlayerEntity constructor call to create our CustomPlayerEntity instead.
     */
    @Redirect(method = "createPlayer", at = @At(value = "NEW", target = "net/minecraft/server/network/ServerPlayerEntity"))
    private ServerPlayerEntity circuitmod$createCustomPlayer(MinecraftServer server, ServerWorld world, GameProfile profile, net.minecraft.network.packet.c2s.common.SyncedClientOptions clientOptions) {
        System.out.println("[CircuitMod] PlayerManagerMixin creating CustomServerPlayerEntity");
        return new CustomServerPlayerEntity(server, world, profile, clientOptions);
    }
}
