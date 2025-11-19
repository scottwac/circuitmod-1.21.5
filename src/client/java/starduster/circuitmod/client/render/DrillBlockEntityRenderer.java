package starduster.circuitmod.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import starduster.circuitmod.block.entity.DrillBlockEntity;



@Environment(EnvType.CLIENT)
public class DrillBlockEntityRenderer
    implements BlockEntityRenderer<DrillBlockEntity> {

    public DrillBlockEntityRenderer(
        BlockEntityRendererFactory.Context ctx) { }

    @Override
    public void render(DrillBlockEntity entity,
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

        // EXTENSIVE DEBUG LOGGING
        // System.out.println("[DRILL-RENDER-DEBUG] ========================================");
        // System.out.println("[DRILL-RENDER-DEBUG] Entity position: " + entity.getPos());
        
        // Get the mining direction directly from the drill entity
        net.minecraft.util.math.Direction miningDirection = entity.getFacingDirection();
        // System.out.println("[DRILL-RENDER-DEBUG] Mining direction from entity: " + miningDirection);
        
        // Get the drill's dimensions
        int miningHeight = entity.getMiningHeight();
        int miningWidth = entity.getMiningWidth();
        // System.out.println("[DRILL-RENDER-DEBUG] Mining dimensions: " + miningWidth + "x" + miningHeight);
        
        // Calculate local Y coordinates for the mining area (height direction)
        float y0 = -miningHeight / 2.0f + 0.5f; // Bottom of mining area
        float y1 = miningHeight / 2.0f + 0.5f;  // Top of mining area
        // System.out.println("[DRILL-RENDER-DEBUG] Local Y coordinates: " + y0 + " to " + y1);
        
        // Calculate the cross-sectional rectangle position (always one block in front of drill)
        float frontX, frontZ;
        if (miningDirection == Direction.NORTH) {
            frontX = 0.5f; // Center of drill X
            frontZ = -0.5f; // 1 block north of drill
        } else if (miningDirection == Direction.SOUTH) {
            frontX = 0.5f; // Center of drill X  
            frontZ = 1.5f; // 1 block south of drill
        } else if (miningDirection == Direction.EAST) {
            frontX = 1.5f; // 1 block east of drill
            frontZ = 0.5f; // Center of drill Z
        } else { // WEST
            frontX = -0.5f; // 1 block west of drill
            frontZ = 0.5f; // Center of drill Z
        }
        // System.out.println("[DRILL-RENDER-DEBUG] Cross-sectional rectangle position: (" + frontX + ", " + frontZ + ")");
        
        // Calculate width bounds for the cross-sectional rectangle (centered around front position)
        float width0, width1;
        if (miningDirection == Direction.NORTH || miningDirection == Direction.SOUTH) {
            // Width is X direction for north/south facing
            width0 = frontX - miningWidth / 2.0f + 0.5f; // Left edge
            width1 = frontX + miningWidth / 2.0f - 0.5f; // Right edge
        } else {
            // Width is Z direction for east/west facing  
            width0 = frontZ - miningWidth / 2.0f + 0.5f; // Near edge
            width1 = frontZ + miningWidth / 2.0f - 0.5f; // Far edge
        }
        // System.out.println("[DRILL-RENDER-DEBUG] Width bounds: " + width0 + " to " + width1);
        // System.out.println("[DRILL-RENDER-DEBUG] ========================================");

        // Draw ONLY the cross-sectional rectangle (one block in front of drill)
        if (miningDirection == Direction.NORTH || miningDirection == Direction.SOUTH) {
            // Cross-section is vertical plane in X-Y, fixed Z position
            drawThickLine(v, matrices, width0, y0, frontZ, width1, y0, frontZ, 0.05f, 0f, 1f, 0f, 1f); // Bottom edge
            drawThickLine(v, matrices, width1, y0, frontZ, width1, y1, frontZ, 0.05f, 0f, 1f, 0f, 1f); // Right edge
            drawThickLine(v, matrices, width1, y1, frontZ, width0, y1, frontZ, 0.05f, 0f, 1f, 0f, 1f); // Top edge
            drawThickLine(v, matrices, width0, y1, frontZ, width0, y0, frontZ, 0.05f, 0f, 1f, 0f, 1f); // Left edge
        } else {
            // Cross-section is vertical plane in Z-Y, fixed X position
            drawThickLine(v, matrices, frontX, y0, width0, frontX, y0, width1, 0.05f, 0f, 1f, 0f, 1f); // Bottom edge
            drawThickLine(v, matrices, frontX, y0, width1, frontX, y1, width1, 0.05f, 0f, 1f, 0f, 1f); // Right edge  
            drawThickLine(v, matrices, frontX, y1, width1, frontX, y1, width0, 0.05f, 0f, 1f, 0f, 1f); // Top edge
            drawThickLine(v, matrices, frontX, y1, width0, frontX, y0, width0, 0.05f, 0f, 1f, 0f, 1f); // Left edge
        }

        // Draw current mining position indicator (red rectangle)
        if (entity.getCurrentMiningPos() != null) {
            net.minecraft.util.math.BlockPos miningPos = entity.getCurrentMiningPos();
            
            // Calculate current position relative to drill
            float currentX = (miningPos.getX() - entity.getPos().getX()) + 0.5f;
            float currentY = (miningPos.getY() - entity.getPos().getY()) + 0.5f;
            float currentZ = (miningPos.getZ() - entity.getPos().getZ()) + 0.5f;
            
            // Draw a red rectangle at the current mining position (1x1 block indicator)
            float size = 0.5f; // Half block size for visibility
            drawThickLine(v, matrices, currentX - size, currentY - size, currentZ - size, 
                         currentX + size, currentY - size, currentZ - size, 0.05f, 1f, 0f, 0f, 1f);
            drawThickLine(v, matrices, currentX + size, currentY - size, currentZ - size, 
                         currentX + size, currentY + size, currentZ - size, 0.05f, 1f, 0f, 0f, 1f);
            drawThickLine(v, matrices, currentX + size, currentY + size, currentZ - size, 
                         currentX - size, currentY + size, currentZ - size, 0.05f, 1f, 0f, 0f, 1f);
            drawThickLine(v, matrices, currentX - size, currentY + size, currentZ - size, 
                         currentX - size, currentY - size, currentZ - size, 0.05f, 1f, 0f, 0f, 1f);
        }

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
        DrillBlockEntity be) {
      return true;
    }

    @Override
    public int getRenderDistance() {
      return 96;
    }
} 