package starduster.circuitmod.client.render.sky;

import com.mojang.blaze3d.systems.RenderSystem;
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
    private static final Identifier EARTH_TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/environment/earth_map_mirrored.png");
    private static final Identifier LUNA_SUN_TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/environment/space_sun.png");
    private static final Identifier CLOUDS_TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/environment/earth_clouds.png");
    private static final Identifier EARTH_PHASE_TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/environment/earth_phases.png");

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
        drawTexturedQuad(matrices, EARTH_TEXTURE, 30.0F, 0.0F, 0.2F, 1.0F,0,0);
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
        drawTexturedQuad(matrices, LUNA_SUN_TEXTURE, 25.0F, 0.0F, 1.0F, 1.0F, 0,0);
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
     * Draws a textured quad with custom UV bounds.
     */
    private void drawTexturedQuad(MatrixStack matrices, Identifier texture,
                                  float size, float yOffset,
                                  float uMax, float vMax,
                                  float uOff, float vOff) {
        VertexConsumerProvider.Immediate consumers =
                MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();

        //uses getText instead of getCelestial, since getCelestial turns black pixels transparent
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getText(texture));

        Matrix4f mat = matrices.peek().getPositionMatrix();
        int color = 0xFFFFFFFF;

        vc.vertex(mat, -size, yOffset, -size).color(color).texture(uOff, vOff).light(0xF000F0).normal(0f, 1f, 0f);
        vc.vertex(mat,  size, yOffset, -size).color(color).texture(uOff + uMax, vOff).light(0xF000F0).normal(0f, 1f, 0f);
        vc.vertex(mat,  size, yOffset,  size).color(color).texture(uOff + uMax, vOff + vMax).light(0xF000F0).normal(0f, 1f, 0f);
        vc.vertex(mat, -size, yOffset,  size).color(color).texture(uOff, vOff + vMax).light(0xF000F0).normal(0f, 1f, 0f);

        consumers.draw();
    }
    
    /**
     * Render the Luna sky directly without a WorldRenderContext.
     * This is called from our mixin to bypass Fabric's context system.
     */
    public void renderDirectly() {
        //Circuitmod.LOGGER.info("[CLIENT] renderDirectly called");
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            Circuitmod.LOGGER.warn("[CLIENT] renderDirectly: client.world is null!");
            return;
        }
        
        //check current dimension for debugging
        //var currentDim = client.world.getRegistryKey();
        MatrixStack matrices = new MatrixStack();
        float worldTime = client.world.getTimeOfDay(); //Get world time
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F)); // Don't translate by camera position - this causes issues with sky rendering

        /**
         * Stars
         */
        renderStars(matrices);

        /**
         * Earth Base
         */
        matrices.push();
        float EarthX = -72.0F; //Reusable values for Earth angle, shared between the 3 layers
        float EarthZ = 2.0F;
        float EarthY = 3.0F;
        float earthSurfacePos = (worldTime % 24000) / 24000; //Get Overworld time and convert to Earth surface position offset
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(EarthX)); // Position Earth in the sky - stationary at a visible location
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(EarthZ));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(EarthY));
        //matrices.translate(0.0, 0.0, 0.0); //Unused transform
        drawTexturedQuad(matrices, EARTH_TEXTURE, 16.0F, 100.1F, 0.25F, 1.0F,earthSurfacePos,0); // Earth uses only left 25% of its atlas (uMax=0.25), vMax=1.0
        matrices.pop();

        /**
         * Earth Clouds
         */
        matrices.push();
        float earthCloudsPos = (worldTime % 24750) / 24750; //Slightly different value from Earth Surface
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(EarthX)); // Position Earth in the sky - uses the values from Earth Base layer
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(EarthZ));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(EarthY));
        //matrices.translate(0.0, 0.0, 0.0); //Unused transform
        drawTexturedQuad(matrices, CLOUDS_TEXTURE, 16.0F, 100.0F, 0.20F, 1.0F,earthCloudsPos,0); // Clouds uses only left 20% of its atlas (uMax=0.20), vMax=1.0
        matrices.pop();

        /**
         * Earth Phase
         */
        matrices.push();
        float earthPhase = (int) ((worldTime % 192000) / (192000/8)); //Get current phase
        float earthPhaseAtlasOffset = (0.125F) * earthPhase; //Turn current phase to atlas coordinate
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(EarthX)); // Position Earth in the sky - uses the values from Earth Base layer
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(EarthZ));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(EarthY));
        //matrices.translate(0.0, 0.0, 0.0); //Unused transform
        drawTexturedQuad(matrices, EARTH_PHASE_TEXTURE, 17.44F, 99.9F, 0.125F, 1.0F,earthPhaseAtlasOffset,0); // Earth uses only left 12.5% of its atlas (uMax=0.125), vMax=1.0
        matrices.pop();

        /**
         * Sun
         */
        matrices.push();
        float lunaSunAngle = ((worldTime+30000) / 192000.0F) * 360.0F; // 24000 is Overworld day, 192000 lines up with lunar cycle (x8 longer). Adding 30000 to make Lunar noon line up exactly with Overworld Midnight
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-90.0F)); //Rotate to make move east-west
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-lunaSunAngle)); //Apply day angle to sun position
        //matrices.translate(0.0, 0.0, 0.0); //Unused transform
        drawTexturedQuad(matrices, LUNA_SUN_TEXTURE, 30.0F, 101.0F, 1.0F, 1.0F,0,0); // Sun uses full texture (1.0, 1.0)
        matrices.pop();

        matrices.pop();
    }
}
