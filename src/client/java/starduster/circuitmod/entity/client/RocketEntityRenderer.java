package starduster.circuitmod.entity.client;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.entity.RocketEntity;

/**
 * Renderer for the rocket entity - provides a simple placeholder until a proper model is added
 */
public class RocketEntityRenderer extends EntityRenderer<RocketEntity, EntityRenderState> {
    
    private static final Identifier TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/entity/rocket.png");
    
    public RocketEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
    }
    
    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }
    
    @Override
    public void render(EntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();
        
        // Scale the rocket
        matrices.scale(1.5f, 3.0f, 1.5f);
        
        // Render a simple colored box as placeholder
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getEntitySolid(TEXTURE));
        
        // Render rocket body (simple box for now)
        this.renderBox(matrices, vertexConsumer, light);
        
        // Add some visual effects (simplified since we don't have entity state)
        matrices.translate(0, -0.6, 0);
        matrices.scale(0.8f, 0.4f, 0.8f);
        
        // Render exhaust glow
        VertexConsumer glowConsumer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
        this.renderBox(matrices, glowConsumer, light, 1.0f, 0.5f, 0.0f, 0.7f); // Orange glow
        
        matrices.pop();
        
        super.render(state, matrices, vertexConsumers, light);
    }
    
    private void renderBox(MatrixStack matrices, VertexConsumer vertices, int light) {
        this.renderBox(matrices, vertices, light, 0.8f, 0.8f, 0.9f, 1.0f); // Default silver color
    }
    
    private void renderBox(MatrixStack matrices, VertexConsumer vertices, int light, float r, float g, float b, float a) {
        MatrixStack.Entry entry = matrices.peek();
        
        // Simple box rendering - this is a placeholder until you add a proper model
        // Bottom face
        this.renderQuad(entry, vertices, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, r, g, b, a, light);
        // Top face  
        this.renderQuad(entry, vertices, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, r, g, b, a, light);
        // North face
        this.renderQuad(entry, vertices, -0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, r, g, b, a, light);
        // South face
        this.renderQuad(entry, vertices, 0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, r, g, b, a, light);
        // West face
        this.renderQuad(entry, vertices, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, -0.5f, r, g, b, a, light);
        // East face
        this.renderQuad(entry, vertices, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f, r, g, b, a, light);
    }
    
    private void renderQuad(MatrixStack.Entry entry, VertexConsumer vertices, 
                           float x1, float y1, float z1, float x2, float y2, float z2,
                           float r, float g, float b, float a, int light) {
        // This is a simplified quad rendering - you'll want to replace this with proper model rendering
        vertices.vertex(entry, x1, y1, z1).color(r, g, b, a).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 1, 0);
        vertices.vertex(entry, x2, y1, z1).color(r, g, b, a).texture(1, 0).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 1, 0);
        vertices.vertex(entry, x2, y2, z2).color(r, g, b, a).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 1, 0);
        vertices.vertex(entry, x1, y2, z2).color(r, g, b, a).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 1, 0);
    }
    
    protected Identifier getTexture(EntityRenderState state) {
        return TEXTURE;
    }
}
