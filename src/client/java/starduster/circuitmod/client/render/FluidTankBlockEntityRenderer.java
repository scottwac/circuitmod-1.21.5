package starduster.circuitmod.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.block.Blocks;
import starduster.circuitmod.block.entity.FluidTankBlockEntity;

@Environment(EnvType.CLIENT)
public class FluidTankBlockEntityRenderer implements BlockEntityRenderer<FluidTankBlockEntity> {
    public FluidTankBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {}

    @Override
    public void render(FluidTankBlockEntity entity,
                       float tickDelta,
                       MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers,
                       int light,
                       int overlay,
                       Vec3d cameraPos) {
        if (entity == null || entity.getWorld() == null) return;
        if (entity.getStoredFluidType() == Fluids.EMPTY) return;

        double percent = entity.getFluidPercentage();
        if (percent <= 0.0) return;

        matrices.push();
        // dispatcher already translated origin; render above block center
        matrices.translate(0.5, 1.05, 0.5);

        double height = 0.4 + 0.6 * percent;
        double radius = 0.20;

        int color = getFluidColor(entity);
        float a = 0.65f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        // Get the fluid sprite like Minecraft's FluidRenderer does
        Sprite fluidSprite = getFluidSprite(entity);
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getTranslucent());

        float x1 = (float) -radius;
        float x2 = (float) radius;
        float z1 = (float) -radius;
        float z2 = (float) radius;
        float y1 = 0f;
        float y2 = (float) height;

        // sides as quads with fluid texture
        drawQuad(consumer, matrices, x1, y1, z1, x2, y1, z1, x2, y2, z1, x1, y2, z1, fluidSprite, r, g, b, a, light);
        drawQuad(consumer, matrices, x2, y1, z1, x2, y1, z2, x2, y2, z2, x2, y2, z1, fluidSprite, r, g, b, a, light);
        drawQuad(consumer, matrices, x2, y1, z2, x1, y1, z2, x1, y2, z2, x2, y2, z2, fluidSprite, r, g, b, a, light);
        drawQuad(consumer, matrices, x1, y1, z2, x1, y1, z1, x1, y2, z1, x1, y2, z2, fluidSprite, r, g, b, a, light);
        // top cap
        drawQuad(consumer, matrices, x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2, fluidSprite, r, g, b, a, light);

        matrices.pop();
    }

    private static void drawQuad(VertexConsumer consumer, MatrixStack ms,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float x3, float y3, float z3,
                                 float x4, float y4, float z4,
                                 Sprite sprite, float r, float g, float b, float a, int light) {
        // Calculate normal vector for the quad
        float nx = (y2 - y1) * (z3 - z1) - (z2 - z1) * (y3 - y1);
        float ny = (z2 - z1) * (x3 - x1) - (x2 - x1) * (z3 - z1);
        float nz = (x2 - x1) * (y3 - y1) - (y2 - y1) * (x3 - x1);
        
        // Normalize the normal vector
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length > 0) {
            nx /= length;
            ny /= length;
            nz /= length;
        }
        
        // Use the sprite's UV coordinates
        float u1 = sprite.getMinU();
        float u2 = sprite.getMaxU();
        float v1 = sprite.getMinV();
        float v2 = sprite.getMaxV();
        
        consumer.vertex(ms.peek(), x1, y1, z1).color(r, g, b, a).texture(u1, v1).overlay(0).light(light).normal(ms.peek(), nx, ny, nz);
        consumer.vertex(ms.peek(), x2, y2, z2).color(r, g, b, a).texture(u2, v1).overlay(0).light(light).normal(ms.peek(), nx, ny, nz);
        consumer.vertex(ms.peek(), x3, y3, z3).color(r, g, b, a).texture(u2, v2).overlay(0).light(light).normal(ms.peek(), nx, ny, nz);
        consumer.vertex(ms.peek(), x4, y4, z4).color(r, g, b, a).texture(u1, v2).overlay(0).light(light).normal(ms.peek(), nx, ny, nz);
    }

    private static Sprite getFluidSprite(FluidTankBlockEntity entity) {
        // Get the fluid sprite like Minecraft's FluidRenderer does
        if (entity.getStoredFluidType() == Fluids.WATER) {
            return MinecraftClient.getInstance().getBakedModelManager()
                .getBlockModels().getModel(Blocks.WATER.getDefaultState()).particleSprite();
        } else if (entity.getStoredFluidType() == Fluids.LAVA) {
            return MinecraftClient.getInstance().getBakedModelManager()
                .getBlockModels().getModel(Blocks.LAVA.getDefaultState()).particleSprite();
        }
        // Fallback to water sprite for unknown fluids
        return MinecraftClient.getInstance().getBakedModelManager()
            .getBlockModels().getModel(Blocks.WATER.getDefaultState()).particleSprite();
    }
    
    private static int getFluidColor(FluidTankBlockEntity entity) {
        if (entity.getStoredFluidType() == Fluids.WATER) return 0x3F76E4;
        if (entity.getStoredFluidType() == Fluids.LAVA) return 0xFC9003;
        return 0x808080;
    }
}


