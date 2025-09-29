package starduster.circuitmod.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Suppress moon rendering and replace stars with our custom stars in the Luna dimension.
 */
@Environment(EnvType.CLIENT)
@Mixin(SkyRendering.class)
public abstract class SkyRenderingNoMoonMixin {

    private static final RegistryKey<World> CIRCUIT_DIM_KEY =
            RegistryKey.of(RegistryKeys.WORLD, Identifier.of("circuitmod", "luna"));
    
    // Keep a static instance of our sky renderer
    private static final starduster.circuitmod.client.render.sky.LunaSkyRenderer LUNA_SKY_RENDERER = 
            new starduster.circuitmod.client.render.sky.LunaSkyRenderer();

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

    @Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
    private void circuitmod$replaceStarsInLuna(Fog fog, float color, MatrixStack matrices, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null && client.world.getRegistryKey().equals(CIRCUIT_DIM_KEY)) {
            starduster.circuitmod.Circuitmod.LOGGER.info("[CLIENT] Replacing vanilla stars with custom Luna stars!");
            // Call our custom star rendering
            LUNA_SKY_RENDERER.renderDirectly();
            // Cancel vanilla star rendering
            ci.cancel();
        }
        // For other dimensions, let vanilla star rendering proceed normally
    }

    // Sun rendering is allowed; only the moon is suppressed above.
}

