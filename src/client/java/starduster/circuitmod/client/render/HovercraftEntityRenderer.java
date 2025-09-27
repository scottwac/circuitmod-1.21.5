package starduster.circuitmod.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import starduster.circuitmod.entity.HovercraftEntity;

@Environment(EnvType.CLIENT)
public class HovercraftEntityRenderer extends EntityRenderer<HovercraftEntity, EntityRenderState> {
    // Use the minecart texture for now
    private static final Identifier TEXTURE = Identifier.of("minecraft", "textures/entity/minecart.png");
    private static final RenderLayer RENDER_LAYER = RenderLayer.getEntityCutout(TEXTURE);
    
    public HovercraftEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.shadowRadius = 0.7F;
    }
    
    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }
    
    @Override
    public void render(EntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();
        
        // For now, just rotate 180 degrees - proper yaw handling would need more complex state
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
        
        // Scale the hovercraft
        matrices.scale(-1.0F, -1.0F, 1.0F);
        
        // Get vertex consumer
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RENDER_LAYER);
        
        // Render the hovercraft body (simple rectangular platform)
        renderHovercraftBody(matrices, vertexConsumer, light);
        
        matrices.pop();
        
        super.render(state, matrices, vertexConsumers, light);
    }
    
    private void renderHovercraftBody(MatrixStack matrices, VertexConsumer vertexConsumer, int light) {
        MatrixStack.Entry entry = matrices.peek();
        
        // Define the dimensions of the hovercraft platform
        float width = 1.375F / 2.0F; // Half width
        float height = 0.5625F / 2.0F; // Half height
        float length = 1.375F / 2.0F; // Half length
        
        // Bottom face
        vertex(entry, vertexConsumer, -width, -height, -length, 0, 1, light);
        vertex(entry, vertexConsumer, width, -height, -length, 1, 1, light);
        vertex(entry, vertexConsumer, width, -height, length, 1, 0, light);
        vertex(entry, vertexConsumer, -width, -height, length, 0, 0, light);
        
        // Top face
        vertex(entry, vertexConsumer, -width, height, length, 0, 0, light);
        vertex(entry, vertexConsumer, width, height, length, 1, 0, light);
        vertex(entry, vertexConsumer, width, height, -length, 1, 1, light);
        vertex(entry, vertexConsumer, -width, height, -length, 0, 1, light);
        
        // Front face
        vertex(entry, vertexConsumer, -width, -height, length, 0, 1, light);
        vertex(entry, vertexConsumer, width, -height, length, 1, 1, light);
        vertex(entry, vertexConsumer, width, height, length, 1, 0, light);
        vertex(entry, vertexConsumer, -width, height, length, 0, 0, light);
        
        // Back face
        vertex(entry, vertexConsumer, width, -height, -length, 0, 1, light);
        vertex(entry, vertexConsumer, -width, -height, -length, 1, 1, light);
        vertex(entry, vertexConsumer, -width, height, -length, 1, 0, light);
        vertex(entry, vertexConsumer, width, height, -length, 0, 0, light);
        
        // Left face
        vertex(entry, vertexConsumer, -width, -height, -length, 0, 1, light);
        vertex(entry, vertexConsumer, -width, -height, length, 1, 1, light);
        vertex(entry, vertexConsumer, -width, height, length, 1, 0, light);
        vertex(entry, vertexConsumer, -width, height, -length, 0, 0, light);
        
        // Right face
        vertex(entry, vertexConsumer, width, -height, length, 0, 1, light);
        vertex(entry, vertexConsumer, width, -height, -length, 1, 1, light);
        vertex(entry, vertexConsumer, width, height, -length, 1, 0, light);
        vertex(entry, vertexConsumer, width, height, length, 0, 0, light);
    }
    
    private void vertex(MatrixStack.Entry entry, VertexConsumer vertexConsumer, float x, float y, float z, float u, float v, int light) {
        vertexConsumer.vertex(entry, x, y, z)
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(entry, 0.0F, 1.0F, 0.0F);
    }
    
    protected Identifier getTexture(EntityRenderState state) {
        return TEXTURE;
    }
}
