package starduster.circuitmod.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import starduster.circuitmod.block.entity.QuarryBlockEntity;

import java.util.List;

@Environment(EnvType.CLIENT)
public class QuarryBlockEntityRenderer
    implements BlockEntityRenderer<QuarryBlockEntity> {

    public QuarryBlockEntityRenderer(
        BlockEntityRendererFactory.Context ctx) { }

    @Override
    public void render(QuarryBlockEntity entity,
                       float tickDelta,
                       MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers,
                       int light,
                       int overlay,
                       Vec3d cameraPos) {
        // dispatcher already applied (pos - camera), so no camera subtraction here
        matrices.push();
        VertexConsumer v = vertexConsumers
            .getBuffer(RenderLayer.getLines());

        // collect min/max X and Z from perimeter
        List<net.minecraft.util.math.BlockPos> perim =
            entity.getPerimeterPositions();
        if (perim.size() < 2) {
            matrices.pop();
            return;
        }
        int minX = perim.stream().mapToInt(p -> p.getX()).min().getAsInt();
        int maxX = perim.stream().mapToInt(p -> p.getX()).max().getAsInt();
        int minZ = perim.stream().mapToInt(p -> p.getZ()).min().getAsInt();
        int maxZ = perim.stream().mapToInt(p -> p.getZ()).max().getAsInt();

        // local coords (center of each block)
        float x0 = (minX - entity.getPos().getX()) + 0.5f;
        float x1 = (maxX - entity.getPos().getX()) + 0.5f;
        float z0 = (minZ - entity.getPos().getZ()) + 0.5f;
        float z1 = (maxZ - entity.getPos().getZ()) + 0.5f;

        // ground‐level y, slightly above the block surface
        float y = 0.1f;

        // draw base rectangle
        drawThickLine(v, matrices, x0, y, z0, x1, y, z0, 0.05f, 0f, 1f, 0f, 1f);
        drawThickLine(v, matrices, x1, y, z0, x1, y, z1, 0.05f, 0f, 1f, 0f, 1f);
        drawThickLine(v, matrices, x1, y, z1, x0, y, z1, 0.05f, 0f, 1f, 0f, 1f);
        drawThickLine(v, matrices, x0, y, z1, x0, y, z0, 0.05f, 0f, 1f, 0f, 1f);

        // height of vertical posts
        float height = 5.0f;
        float topY = y + height;

        // draw vertical corner posts
        drawThickLine(v, matrices, x0, y,  z0,  x0, topY, z0, 0.05f, 0f,1f,0f,1f);
        drawThickLine(v, matrices, x1, y,  z0,  x1, topY, z0, 0.05f, 0f,1f,0f,1f);
        drawThickLine(v, matrices, x1, y,  z1,  x1, topY, z1, 0.05f, 0f,1f,0f,1f);
        drawThickLine(v, matrices, x0, y,  z1,  x0, topY, z1, 0.05f, 0f,1f,0f,1f);

        // draw top rectangle
        drawThickLine(v, matrices, x0, topY, z0, x1, topY, z0, 0.05f, 0f,1f,0f,1f);
        drawThickLine(v, matrices, x1, topY, z0, x1, topY, z1, 0.05f, 0f,1f,0f,1f);
        drawThickLine(v, matrices, x1, topY, z1, x0, topY, z1, 0.05f, 0f,1f,0f,1f);
        drawThickLine(v, matrices, x0, topY, z1, x0, topY, z0, 0.05f, 0f,1f,0f,1f);

        matrices.pop();
    }

    private static void drawThickLine(VertexConsumer consumer,
                                      MatrixStack ms,
                                      float x1, float y1, float z1,
                                      float x2, float y2, float z2,
                                      float width,
                                      float r, float g, float b, float a) {
        // compute a perpendicular horizontal offset
        float dx = x2 - x1, dz = z2 - z1;
        float offX = dz, offZ = -dx;
        float len = (float)Math.sqrt(offX*offX + offZ*offZ);
        if (len > 0) {
            offX = offX/len * width;
            offZ = offZ/len * width;
        }
        // fan of parallel segments
        int steps = 8;
        for (int i = 0; i <= steps; i++) {
            float t = (i/(float)steps) - 0.5f;
            float ox = offX * t, oz = offZ * t;
            consumer.vertex(ms.peek(), x1 + ox, y1, z1 + oz)
                    .color(r, g, b, a).normal(0, 1, 0);
            consumer.vertex(ms.peek(), x2 + ox, y2, z2 + oz)
                    .color(r, g, b, a).normal(0, 1, 0);
        }
    }

    @Override
    public boolean rendersOutsideBoundingBox(
        QuarryBlockEntity be) {
      return true;
    }

    @Override
    public int getRenderDistance() {
      return 96;
    }
}

