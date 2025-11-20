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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import starduster.circuitmod.block.entity.SatelliteControlBlockEntity;

@Environment(EnvType.CLIENT)
public class SatelliteControlBlockEntityRenderer implements BlockEntityRenderer<SatelliteControlBlockEntity> {

    public SatelliteControlBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
    }

    @Override
    public void render(SatelliteControlBlockEntity entity,
                       float tickDelta,
                       MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers,
                       int light,
                       int overlay,
                       Vec3d cameraPos) {

        // Get active beam data from the block entity
        BlockPos beamTarget = entity.getActiveBeamTarget();
        if (beamTarget == null) {
            return; // No active beam
        }

        float beamHeight = entity.getActiveBeamHeight();
        float beamRadius = entity.getActiveBeamRadius();

        // Calculate relative position from controller to beam target
        BlockPos controllerPos = entity.getPos();
        float relativeX = beamTarget.getX() - controllerPos.getX();
        float relativeY = beamTarget.getY() - controllerPos.getY();
        float relativeZ = beamTarget.getZ() - controllerPos.getZ();

        matrices.push();
        
        // Translate to beam target position
        matrices.translate(relativeX + 0.5, relativeY, relativeZ + 0.5);
        
        // Render the beam
        renderSatelliteBeam(beamHeight, beamRadius, matrices, vertexConsumers, light);
        
        matrices.pop();
    }

    /**
     * Render a thick red beam from sky to ground
     */
    private void renderSatelliteBeam(float beamHeight,
                                     float beamRadius,
                                     MatrixStack matrices,
                                     VertexConsumerProvider vertexConsumers,
                                     int light) {
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getLightning());
        MatrixStack.Entry entry = matrices.peek();
        
        int r = 255;
        int g = 40;
        int b = 40;
        
        int rings = 3;
        int segments = 24;
        
        for (int ring = 0; ring < rings; ring++) {
            float radius = beamRadius * ((ring + 1) / (float) rings);
            
            for (int i = 0; i < segments; i++) {
                float angle = (i / (float) segments) * (float) (2 * Math.PI);
                float nextAngle = ((i + 1) / (float) segments) * (float) (2 * Math.PI);
                
                float x1 = MathHelper.cos(angle) * radius;
                float z1 = MathHelper.sin(angle) * radius;
                float x2 = MathHelper.cos(nextAngle) * radius;
                float z2 = MathHelper.sin(nextAngle) * radius;
                
                // Vertical beam segment
                vertexConsumer.vertex(entry, x1, beamHeight, z1).color(r, g, b, 220).normal(entry, 0, -1, 0).light(15728880);
                vertexConsumer.vertex(entry, x1, 0.0f, z1).color(r, g, b, 150).normal(entry, 0, 1, 0).light(15728880);
                
                // Ring at top
                vertexConsumer.vertex(entry, x1, beamHeight, z1).color(r, g, b, 220).normal(entry, 0, 1, 0).light(15728880);
                vertexConsumer.vertex(entry, x2, beamHeight, z2).color(r, g, b, 220).normal(entry, 0, 1, 0).light(15728880);
                
                // Ring at bottom
                vertexConsumer.vertex(entry, x1, 0.0f, z1).color(r, g, b, 150).normal(entry, 0, 1, 0).light(15728880);
                vertexConsumer.vertex(entry, x2, 0.0f, z2).color(r, g, b, 150).normal(entry, 0, 1, 0).light(15728880);
            }
        }
        
        // Add cross lines for energy effect
        for (int i = 0; i < 8; i++) {
            float y = (i / 8.0f) * beamHeight;
            float angle = i * (float) Math.PI / 4;
            float x = MathHelper.cos(angle) * beamRadius;
            float z = MathHelper.sin(angle) * beamRadius;
            
            vertexConsumer.vertex(entry, x, y, z).color(r, g, b, 200).normal(entry, 0, -1, 0).light(15728880);
            vertexConsumer.vertex(entry, -x, y, -z).color(r, g, b, 200).normal(entry, 0, 1, 0).light(15728880);
        }
    }

    @Override
    public boolean rendersOutsideBoundingBox(SatelliteControlBlockEntity blockEntity) {
        return true; // Beam extends far beyond block bounds
    }

    @Override
    public int getRenderDistance() {
        return 4096; // Render from very far away
    }
}

