package starduster.circuitmod.mixin.client;


import net.minecraft.client.world.ClientWorld;

import org.spongepowered.asm.mixin.Mixin;


/**
 * Mixin to override client player entity creation.
 * This ensures that our CustomClientPlayerEntity is used instead of the regular ClientPlayerEntity.
 */
@Mixin(ClientWorld.class)
public class ClientWorldMixin {
    
    /**
     * Redirects the ClientPlayerEntity constructor call to create our CustomClientPlayerEntity instead.
     */
 //   @Redirect(method = "addPlayer", at = @At(value = "NEW", target = "Lnet/minecraft/client/network/ClientPlayerEntity;"))
 //   private ClientPlayerEntity circuitmod$createCustomClientPlayer(MinecraftClient client, ClientWorld world, ClientPlayNetworkHandler networkHandler, StatHandler stats, ClientRecipeBook recipeBook, boolean lastSneaking, boolean lastSprinting) {
 //       System.out.println("[CircuitMod] ClientWorldMixin creating CustomClientPlayerEntity");
 //       return new CustomClientPlayerEntity(client, world, networkHandler, stats, recipeBook, lastSneaking, lastSprinting);
 //   }
}
