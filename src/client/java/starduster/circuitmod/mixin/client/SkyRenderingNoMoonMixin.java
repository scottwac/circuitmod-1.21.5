package starduster.circuitmod.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Suppress moon rendering only in the Circuit dimension, while keeping sun and stars.
 */
@Environment(EnvType.CLIENT)
@Mixin(SkyRendering.class)
abstract class SkyRenderingNoMoonMixin {

    private static final RegistryKey<World> CIRCUIT_DIM_KEY =
            RegistryKey.of(RegistryKeys.WORLD, Identifier.of("circuitmod", "moon"));

    @Invoker("renderMoon")
    abstract void circuitmod$invokeRenderMoon(int phase, float alpha, VertexConsumerProvider vertexConsumers, MatrixStack matrices);

    @Redirect(
            method = "renderCelestialBodies",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/SkyRendering;renderMoon(IFLnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/util/math/MatrixStack;)V"
            )
    )
    private void circuitmod$skipMoonInCircuit(SkyRendering self, int phase, float alpha, VertexConsumerProvider vertexConsumers, MatrixStack matrices) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null && client.world.getRegistryKey().equals(CIRCUIT_DIM_KEY)) {
            // Do nothing: hide moon in our dimension only
            return;
        }
        // Other dimensions: call vanilla behaviour
        this.circuitmod$invokeRenderMoon(phase, alpha, vertexConsumers, matrices);
    }

    // Sun rendering is allowed; only the moon is suppressed above.
}

