package starduster.circuitmod.client.render.sky;

import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import starduster.circuitmod.Circuitmod;

public class LunaSkyRenderer implements DimensionRenderingRegistry.SkyRenderer {
    private static final Identifier MOON_TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/environment/earth_wide.png");

    @Override
    public void render(WorldRenderContext context) {
        MatrixStack matrices = context.matrixStack();
        if (matrices == null) {
            Circuitmod.LOGGER.warn("[CLIENT] Luna sky renderer called but matrices is null!");
            return;
        }
        
        // Debug: Check current dimension
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            var currentDim = client.world.getRegistryKey();
            Circuitmod.LOGGER.info("[CLIENT] Luna sky renderer called in dimension: {}", currentDim);
        }
        
        // Debug logging - remove this later
        Circuitmod.LOGGER.info("[CLIENT] Luna sky renderer executing - rendering stars, sun, and moon");

        matrices.push();

        // Don't translate by camera position - this causes issues with sky rendering
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));

        // ---- Stars ----
        Circuitmod.LOGGER.info("[CLIENT] About to render stars...");
        renderStars(matrices);
        Circuitmod.LOGGER.info("[CLIENT] Stars rendered successfully");

        // ---- Sun ----
        matrices.push();
        Circuitmod.LOGGER.info("[CLIENT] About to render sun...");
        drawTexturedQuad(matrices, MOON_TEXTURE, 20.0F, 100.0F);
        Circuitmod.LOGGER.info("[CLIENT] Sun rendered successfully");
        matrices.pop();

        // ---- Moon ----
//        matrices.push();
//        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0F));
//        matrices.translate(0.0, 0.0, -0.0);
//        Circuitmod.LOGGER.info("[CLIENT] About to render moon...");
//        drawTexturedQuad(matrices, MOON_TEXTURE, 20.0F, 0.0F);
//        Circuitmod.LOGGER.info("[CLIENT] Moon rendered successfully");
//        matrices.pop();

        matrices.pop();
        Circuitmod.LOGGER.info("[CLIENT] Luna sky renderer completed successfully");
    }

    /**
     * Renders stars as white points across the sky.
     */
    private void renderStars(MatrixStack matrices) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            Circuitmod.LOGGER.warn("[CLIENT] renderStars: client.world is null!");
            return;
        }

        // No custom stars - let vanilla star rendering handle this
        Circuitmod.LOGGER.info("[CLIENT] renderStars: skipping custom star rendering");
    }

    /**
     * Draws a textured quad (ie for sun/moon).
     */
    private void drawTexturedQuad(MatrixStack matrices, Identifier texture, float size, float yOffset) {
        Circuitmod.LOGGER.info("[CLIENT] drawTexturedQuad: texture={}, size={}, yOffset={}", texture, size, yOffset);
        
        VertexConsumerProvider.Immediate consumers =
                MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getCelestial(texture));

        Matrix4f mat = matrices.peek().getPositionMatrix();
        int color = ColorHelper.getWhite(1.0F);
        
        Circuitmod.LOGGER.info("[CLIENT] drawTexturedQuad: color=0x{}", Integer.toHexString(color));

        vc.vertex(mat, -size, yOffset, -size).texture(0.0F, 0.0F).color(color);
        vc.vertex(mat,  size, yOffset, -size).texture(0.2F, 0.0F).color(color);
        vc.vertex(mat,  size, yOffset,  size).texture(0.2F, 1.0F).color(color);
        vc.vertex(mat, -size, yOffset,  size).texture(0.0F, 1.0F).color(color);

        Circuitmod.LOGGER.info("[CLIENT] drawTexturedQuad: calling consumers.draw()");
        consumers.draw();
        Circuitmod.LOGGER.info("[CLIENT] drawTexturedQuad: completed");
    }
    
    /**
     * Render the Luna sky directly without a WorldRenderContext.
     * This is called from our mixin to bypass Fabric's context system.
     */
    public void renderDirectly() {
        Circuitmod.LOGGER.info("[CLIENT] renderDirectly called");
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            Circuitmod.LOGGER.warn("[CLIENT] renderDirectly: client.world is null!");
            return;
        }
        
        // Debug: Check current dimension
        var currentDim = client.world.getRegistryKey();
        Circuitmod.LOGGER.info("[CLIENT] renderDirectly called in dimension: {}", currentDim);
        
        MatrixStack matrices = new MatrixStack();
        
        // Debug logging
        Circuitmod.LOGGER.info("[CLIENT] renderDirectly executing - rendering stars, sun, and moon");

        matrices.push();

        // Don't translate by camera position - this causes issues with sky rendering
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));

        // ---- Stars ----
        Circuitmod.LOGGER.info("[CLIENT] renderDirectly: About to render stars...");
        renderStars(matrices);
        Circuitmod.LOGGER.info("[CLIENT] renderDirectly: Stars rendered successfully");

        // ---- Sun ----
        matrices.push();
        Circuitmod.LOGGER.info("[CLIENT] renderDirectly: About to render sun...");
        drawTexturedQuad(matrices, MOON_TEXTURE, 20.0F, 100.0F);
        Circuitmod.LOGGER.info("[CLIENT] renderDirectly: Sun rendered successfully");
        matrices.pop();

        // ---- Moon ----
//        matrices.push();
//        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0F));
//        matrices.translate(0.0, 0.0, 0.0);
//        Circuitmod.LOGGER.info("[CLIENT] renderDirectly: About to render moon...");
//        drawTexturedQuad(matrices, MOON_TEXTURE, 20.0F, 100.0F);
//        Circuitmod.LOGGER.info("[CLIENT] renderDirectly: Moon rendered successfully");
//        matrices.pop();

        matrices.pop();
        Circuitmod.LOGGER.info("[CLIENT] renderDirectly completed successfully");
    }
}
