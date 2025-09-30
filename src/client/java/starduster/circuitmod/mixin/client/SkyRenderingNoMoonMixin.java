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
 * Suppress moon rendering and replace sun rendering with our custom Luna sky renderer in the Luna dimension.
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

    @Invoker("renderSun")
    abstract void circuitmod$invokeRenderSun(float alpha, VertexConsumerProvider vertexConsumers, MatrixStack matrices);

    @Redirect(
            method = "renderCelestialBodies",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/SkyRendering;renderSun(FLnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/util/math/MatrixStack;)V"
            )
    )
    private void circuitmod$replaceSunWithLunaRender(SkyRendering self, float alpha, VertexConsumerProvider vertexConsumers, MatrixStack matrices) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null && client.world.getRegistryKey().equals(CIRCUIT_DIM_KEY)) {
            // starduster.circuitmod.Circuitmod.LOGGER.info("[CLIENT] Replacing vanilla sun rendering with custom Luna sky renderer!");
            // Call our custom Luna sky rendering instead of the sun
            LUNA_SKY_RENDERER.renderDirectly();
            return;
        }
        // Other dimensions: call vanilla sun rendering
        this.circuitmod$invokeRenderSun(alpha, vertexConsumers, matrices);
    }

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


    // Sun rendering is replaced with Luna sky renderer; moon is suppressed above.
}

