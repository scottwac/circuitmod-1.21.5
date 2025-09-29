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
    private static final Identifier EARTH_TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/environment/earth_wide.png");
    private static final Identifier LUNA_SUN_TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/environment/space_sun.png");

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
        Circuitmod.LOGGER.info("[CLIENT] Luna sky renderer executing - rendering stars, Earth, and Luna sun");

        matrices.push();

        // Don't translate by camera position - this causes issues with sky rendering
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));

        // ---- Stars ----
        Circuitmod.LOGGER.info("[CLIENT] About to render stars...");
        renderStars(matrices);
        Circuitmod.LOGGER.info("[CLIENT] Stars rendered successfully");

        // ---- Earth (stationary, replacing vanilla sun position) ----
        matrices.push();
        Circuitmod.LOGGER.info("[CLIENT] About to render Earth...");
        
        // Position Earth in the sky - stationary at a visible location
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(0.0F)); // Fixed position
        matrices.translate(0.0, 50.0, -100.0); // Move Earth up and out to sky distance
        
        Circuitmod.LOGGER.info("[CLIENT] Earth rendered at fixed position");
        // Earth uses only left 20% of its atlas (uMax=0.2), vMax=1.0
        drawTexturedQuad(matrices, EARTH_TEXTURE, 30.0F, 0.0F, 0.2F, 1.0F);
        Circuitmod.LOGGER.info("[CLIENT] Earth rendered successfully");
        matrices.pop();

        // ---- Luna Sun ----
        matrices.push();
        Circuitmod.LOGGER.info("[CLIENT] About to render Luna Sun...");
        
        // Get world time for Luna sun movement
        float worldTime = client.world.getTimeOfDay();
        // Luna sun moves at normal speed - proper calculation for 24000 tick day cycle
        float lunaSunAngle = (worldTime / 24000.0F) * 360.0F; // Normal speed
        
        // Apply Luna sun rotation - moves across the sky
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(lunaSunAngle));
        matrices.translate(0.0, 30.0, -100.0); // Move Luna sun up and out to sky distance
        
        Circuitmod.LOGGER.info("[CLIENT] Luna Sun angle: {} degrees (world time: {})", lunaSunAngle, worldTime);
        // Sun uses full texture (1.0, 1.0)
        drawTexturedQuad(matrices, LUNA_SUN_TEXTURE, 25.0F, 0.0F, 1.0F, 1.0F);
        Circuitmod.LOGGER.info("[CLIENT] Luna Sun rendered successfully");
        matrices.pop();

      

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

        
    }

    /**
     * Draws a textured quad (ie for sun/moon) using full UV range.
     */
    

    /**
     * Draws a textured quad with custom UV bounds.
     */
    private void drawTexturedQuad(MatrixStack matrices, Identifier texture, float size, float yOffset, float uMax, float vMax) {
        Circuitmod.LOGGER.info("[CLIENT] drawTexturedQuad: texture={}, size={}, yOffset={}, uMax={}, vMax={}", texture, size, yOffset, uMax, vMax);
        
        VertexConsumerProvider.Immediate consumers =
                MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getCelestial(texture));

        Matrix4f mat = matrices.peek().getPositionMatrix();
        int color = ColorHelper.getWhite(1.0F);
        
        Circuitmod.LOGGER.info("[CLIENT] drawTexturedQuad: color=0x{}", Integer.toHexString(color));

        vc.vertex(mat, -size, yOffset, -size).texture(0.0F, 0.0F).color(color);
        vc.vertex(mat,  size, yOffset, -size).texture(uMax, 0.0F).color(color);
        vc.vertex(mat,  size, yOffset,  size).texture(uMax, vMax).color(color);
        vc.vertex(mat, -size, yOffset,  size).texture(0.0F, vMax).color(color);

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
        Circuitmod.LOGGER.info("[CLIENT] renderDirectly executing - rendering stars, Earth, and Luna sun");

        matrices.push();

        // Don't translate by camera position - this causes issues with sky rendering
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));

        // ---- Stars ----
        Circuitmod.LOGGER.info("[CLIENT] renderDirectly: About to render stars...");
        renderStars(matrices);
        Circuitmod.LOGGER.info("[CLIENT] renderDirectly: Stars rendered successfully");

        // ---- Earth (stationary, replacing vanilla sun position) ----
        matrices.push();
        Circuitmod.LOGGER.info("[CLIENT] renderDirectly: About to render Earth...");
        
        // Position Earth in the sky - stationary at a visible location
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(0.0F)); // Fixed position
        matrices.translate(0.0, 50.0, -100.0); // Move Earth up and out to sky distance
        
        Circuitmod.LOGGER.info("[CLIENT] renderDirectly: Earth rendered at fixed position");
        // Earth uses only left 20% of its atlas (uMax=0.2), vMax=1.0
        drawTexturedQuad(matrices, EARTH_TEXTURE, 30.0F, 0.0F, 0.2F, 1.0F);
        Circuitmod.LOGGER.info("[CLIENT] renderDirectly: Earth rendered successfully");
        matrices.pop();

        // ---- Luna Sun ----
        matrices.push();
        Circuitmod.LOGGER.info("[CLIENT] renderDirectly: About to render Luna Sun...");
        
        // Get world time for Luna sun movement
        float worldTime = client.world.getTimeOfDay();
        // Luna sun moves at normal speed - proper calculation for 24000 tick day cycle
        float lunaSunAngle = (worldTime / 24000.0F) * 360.0F; // Normal speed
        
        // Apply Luna sun rotation - moves across the sky
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(lunaSunAngle));
        matrices.translate(0.0, 30.0, -100.0); // Move Luna sun up and out to sky distance
        
        Circuitmod.LOGGER.info("[CLIENT] renderDirectly: Luna Sun angle: {} degrees (world time: {})", lunaSunAngle, worldTime);
        // Sun uses full texture (1.0, 1.0)
        drawTexturedQuad(matrices, LUNA_SUN_TEXTURE, 25.0F, 0.0F, 1.0F, 1.0F);
        Circuitmod.LOGGER.info("[CLIENT] renderDirectly: Luna Sun rendered successfully");
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
