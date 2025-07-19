package starduster.circuitmod.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import starduster.circuitmod.block.entity.ConstructorBlockEntity;

import java.util.List;

@Environment(EnvType.CLIENT)
public class ConstructorBlockEntityRenderer implements BlockEntityRenderer<ConstructorBlockEntity> {

    public ConstructorBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) { }

    @Override
    public void render(ConstructorBlockEntity entity,
                       float tickDelta,
                       MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers,
                       int light,
                       int overlay,
                       Vec3d cameraPos) {
        
        // Only render if there's a blueprint loaded
        if (!entity.hasBlueprint()) {
            return;
        }
        
        matrices.push();
        VertexConsumer v = vertexConsumers.getBuffer(RenderLayer.getLines());

        // Get blueprint build positions
        List<BlockPos> buildPositions = entity.getBlueprintBuildPositions();
        if (buildPositions.isEmpty()) {
            matrices.pop();
            return;
        }

        // Calculate bounding box of the build area
        int minX = buildPositions.stream().mapToInt(BlockPos::getX).min().orElse(0);
        int maxX = buildPositions.stream().mapToInt(BlockPos::getX).max().orElse(0);
        int minY = buildPositions.stream().mapToInt(BlockPos::getY).min().orElse(0);
        int maxY = buildPositions.stream().mapToInt(BlockPos::getY).max().orElse(0);
        int minZ = buildPositions.stream().mapToInt(BlockPos::getZ).min().orElse(0);
        int maxZ = buildPositions.stream().mapToInt(BlockPos::getZ).max().orElse(0);

        // Convert to local coordinates relative to the constructor
        float x0 = (minX - entity.getPos().getX()) + 0.5f;
        float x1 = (maxX - entity.getPos().getX()) + 0.5f;
        float y0 = (minY - entity.getPos().getY()) + 0.5f;
        float y1 = (maxY - entity.getPos().getY()) + 0.5f;
        float z0 = (minZ - entity.getPos().getZ()) + 0.5f;
        float z1 = (maxZ - entity.getPos().getZ()) + 0.5f;

        // Use cyan/blue color for constructor outline (different from quarry's green)
        float r = 0f, g = 0.8f, b = 1f, a = 0.8f;

        // Draw the 3D wireframe outline of the build area
        drawWireframeBox(v, matrices, x0, y0, z0, x1, y1, z1, 0.03f, r, g, b, a);

        matrices.pop();
    }

    private static void drawWireframeBox(VertexConsumer consumer,
                                         MatrixStack ms,
                                         float x0, float y0, float z0,
                                         float x1, float y1, float z1,
                                         float width,
                                         float r, float g, float b, float a) {
        
        // Draw the 12 edges of the box
        // Bottom face edges
        drawThickLine(consumer, ms, x0, y0, z0, x1, y0, z0, width, r, g, b, a);
        drawThickLine(consumer, ms, x1, y0, z0, x1, y0, z1, width, r, g, b, a);
        drawThickLine(consumer, ms, x1, y0, z1, x0, y0, z1, width, r, g, b, a);
        drawThickLine(consumer, ms, x0, y0, z1, x0, y0, z0, width, r, g, b, a);
        
        // Top face edges
        drawThickLine(consumer, ms, x0, y1, z0, x1, y1, z0, width, r, g, b, a);
        drawThickLine(consumer, ms, x1, y1, z0, x1, y1, z1, width, r, g, b, a);
        drawThickLine(consumer, ms, x1, y1, z1, x0, y1, z1, width, r, g, b, a);
        drawThickLine(consumer, ms, x0, y1, z1, x0, y1, z0, width, r, g, b, a);
        
        // Vertical edges
        drawThickLine(consumer, ms, x0, y0, z0, x0, y1, z0, width, r, g, b, a);
        drawThickLine(consumer, ms, x1, y0, z0, x1, y1, z0, width, r, g, b, a);
        drawThickLine(consumer, ms, x1, y0, z1, x1, y1, z1, width, r, g, b, a);
        drawThickLine(consumer, ms, x0, y0, z1, x0, y1, z1, width, r, g, b, a);
    }

    private static void drawThickLine(VertexConsumer consumer,
                                      MatrixStack ms,
                                      float x1, float y1, float z1,
                                      float x2, float y2, float z2,
                                      float width,
                                      float r, float g, float b, float a) {
        // Compute a perpendicular offset vector
        Vec3d direction = new Vec3d(x2 - x1, y2 - y1, z2 - z1);
        Vec3d offset;
        
        // Choose an arbitrary perpendicular vector
        if (Math.abs(direction.x) < 0.9) {
            offset = new Vec3d(1, 0, 0).crossProduct(direction).normalize().multiply(width);
        } else {
            offset = new Vec3d(0, 1, 0).crossProduct(direction).normalize().multiply(width);
        }
        
        // Draw multiple parallel lines to create thickness
        int steps = 6;
        for (int i = 0; i <= steps; i++) {
            float t = (i / (float) steps) - 0.5f;
            float ox = (float) (offset.x * t);
            float oy = (float) (offset.y * t);
            float oz = (float) (offset.z * t);
            
            consumer.vertex(ms.peek(), x1 + ox, y1 + oy, z1 + oz)
                    .color(r, g, b, a).normal(0, 1, 0);
            consumer.vertex(ms.peek(), x2 + ox, y2 + oy, z2 + oz)
                    .color(r, g, b, a).normal(0, 1, 0);
        }
    }

    @Override
    public boolean rendersOutsideBoundingBox(ConstructorBlockEntity entity) {
        return true;
    }

    @Override
    public int getRenderDistance() {
        return 96;
    }
} 