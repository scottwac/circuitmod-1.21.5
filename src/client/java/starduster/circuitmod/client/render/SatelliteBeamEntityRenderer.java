package starduster.circuitmod.client.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import starduster.circuitmod.entity.SatelliteBeamEntity;

/**
 * Renderer for the satellite mining beam that comes from high in the sky
 */
public class SatelliteBeamEntityRenderer extends EntityRenderer<SatelliteBeamEntity, SatelliteBeamEntityRenderer.BeamRenderState> {
    private static final int BEAM_START_HEIGHT = 320; // Start beam from build limit
    
    public SatelliteBeamEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }
    
    @Override
    public BeamRenderState createRenderState() {
        return new BeamRenderState();
    }

    public int getRenderDistance() {
        return 512;
    }
    
    @Override
    public void updateRenderState(SatelliteBeamEntity entity, BeamRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        state.targetPos = entity.getTargetPos();
        state.age = entity.getAge();
        state.tickDelta = tickDelta;
        state.beamHeight = entity.getBeamHeight();
        state.beamRadius = entity.getBeamRadius();
    }
    
    @Override
    public void render(BeamRenderState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        BlockPos targetPos = state.targetPos;
        if (targetPos == null) {
            return;
        }
        
        matrices.push();
        
        float beamHeight = Math.max(32f, state.beamHeight > 0 ? state.beamHeight : BEAM_START_HEIGHT - targetPos.getY());
        float beamRadius = Math.max(0.5f, state.beamRadius);
        
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
        
        int segments = 48;
        
        matrices.push();
        matrices.translate(0.0, beamHeight, 0.0);
        
        for (int segment = 0; segment < segments; segment++) {
            float angle = (float) (segment * 2 * Math.PI / segments);
            float nextAngle = (float) ((segment + 1) * 2 * Math.PI / segments);
            float x1 = MathHelper.cos(angle) * beamRadius;
            float z1 = MathHelper.sin(angle) * beamRadius;
            float x2 = MathHelper.cos(nextAngle) * beamRadius;
            float z2 = MathHelper.sin(nextAngle) * beamRadius;
            
            vertexConsumer.vertex(entry, x1, 0.0f, z1).color(r, g, b, 220).normal(entry, 0, -1, 0).light(15728880);
            vertexConsumer.vertex(entry, x1, -beamHeight, z1).color(r, g, b, 120).normal(entry, 0, 1, 0).light(15728880);
            
            vertexConsumer.vertex(entry, x1, 0.0f, z1).color(r, g, b, 220).normal(entry, 0, 1, 0).light(15728880);
            vertexConsumer.vertex(entry, x2, 0.0f, z2).color(r, g, b, 220).normal(entry, 0, 1, 0).light(15728880);
            
            vertexConsumer.vertex(entry, x1, -beamHeight, z1).color(r, g, b, 120).normal(entry, 0, 1, 0).light(15728880);
            vertexConsumer.vertex(entry, x2, -beamHeight, z2).color(r, g, b, 120).normal(entry, 0, 1, 0).light(15728880);
        }
        
        matrices.pop();
    }
    
    
    /**
     * Render state for the satellite beam
     */
    public static class BeamRenderState extends EntityRenderState {
        public BlockPos targetPos;
        public int age;
        public float tickDelta;
        public float beamHeight;
        public float beamRadius;
    }
}

