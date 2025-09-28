package starduster.circuitmod.client.render.sky;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import starduster.circuitmod.Circuitmod;

public class LunaSkyRenderer implements DimensionRenderingRegistry.SkyRenderer {
    private static final Identifier SUN_TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/block/natural/luna/lunar_ice.png");
    private static final Identifier MOON_TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/block/natural/luna/lunar_basalt.png");

    @Override
    public void render(WorldRenderContext context) {
        MatrixStack matrices = context.matrixStack();
        if(matrices == null) return;
        Circuitmod.LOGGER.info("[CLIENT] running luna sky renderer");
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;

        matrices.push();

        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));

        // ---- Stars ----
        renderStars(matrices);

        // ---- Sun ----
        matrices.push();
        drawTexturedQuad(matrices, SUN_TEXTURE, 30.0F, 100.0F);
        matrices.pop();

        // ---- Moon ----
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0F));
        matrices.translate(100.0, 0.0, -100.0);
        drawTexturedQuad(matrices, MOON_TEXTURE, 20.0F, 0.0F);
        matrices.pop();

        matrices.pop();
    }

    /**
     * Draws stars manually each frame using a VertexConsumer.
     */
    private void renderStars(MatrixStack matrices) {
        VertexConsumerProvider.Immediate consumers =
                MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getDebugQuads());

        Matrix4f mat = matrices.peek().getPositionMatrix();
        int color = ColorHelper.getWhite(1.0F);
        Random random = Random.create(10842L);

        for (int i = 0; i < 1500; i++) {
            float rx = random.nextFloat() * 2.0F - 1.0F;
            float ry = random.nextFloat() * 2.0F - 1.0F;
            float rz = random.nextFloat() * 2.0F - 1.0F;
            float scale = 0.15F + random.nextFloat() * 0.1F;
            float mag = MathHelper.magnitude(rx, ry, rz);
            if (mag <= 0.01F || mag >= 1.0F) continue;

            Vector3f center = new Vector3f(rx, ry, rz).normalize(100.0F);
            float rot = (float) (random.nextDouble() * Math.PI * 2.0);
            Matrix3f rotMat = new Matrix3f()
                    .rotateTowards(new Vector3f(center).negate(), new Vector3f(0.0F, 1.0F, 0.0F))
                    .rotateZ(-rot);

            // Build a small quad for the star
            Vector3f v1 = new Vector3f(scale, -scale, 0).mul(rotMat).add(center);
            Vector3f v2 = new Vector3f(scale,  scale, 0).mul(rotMat).add(center);
            Vector3f v3 = new Vector3f(-scale, scale, 0).mul(rotMat).add(center);
            Vector3f v4 = new Vector3f(-scale,-scale, 0).mul(rotMat).add(center);

            vc.vertex(mat, v1.x, v1.y, v1.z).color(color);
            vc.vertex(mat, v2.x, v2.y, v2.z).color(color);
            vc.vertex(mat, v3.x, v3.y, v3.z).color(color);
            vc.vertex(mat, v4.x, v4.y, v4.z).color(color);
        }

        consumers.draw();
    }

    /**
     * Draws a textured quad (ie for sun/moon).
     */
    private void drawTexturedQuad(MatrixStack matrices, Identifier texture, float size, float yOffset) {
        VertexConsumerProvider.Immediate consumers =
                MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getCelestial(texture));

        Matrix4f mat = matrices.peek().getPositionMatrix();
        int color = ColorHelper.getWhite(1.0F);

        vc.vertex(mat, -size, yOffset, -size).texture(0.0F, 0.0F).color(color);
        vc.vertex(mat,  size, yOffset, -size).texture(1.0F, 0.0F).color(color);
        vc.vertex(mat,  size, yOffset,  size).texture(1.0F, 1.0F).color(color);
        vc.vertex(mat, -size, yOffset,  size).texture(0.0F, 1.0F).color(color);

        consumers.draw();
    }
}
