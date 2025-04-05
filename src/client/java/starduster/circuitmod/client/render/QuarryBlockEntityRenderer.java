package starduster.circuitmod.client.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import starduster.circuitmod.block.entity.QuarryBlockEntity;

public class QuarryBlockEntityRenderer implements BlockEntityRenderer<QuarryBlockEntity> {
    // Animation state tracking
    private static float totalTickDelta = 0.0f;
    
    public QuarryBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        // Constructor required for renderer registration
    }
    
    @Override
    public void render(QuarryBlockEntity entity, float tickDelta, MatrixStack matrices, 
                      VertexConsumerProvider vertexConsumers, int light, int overlay, Vec3d cameraPos) {
        // Only render the diamond if the quarry is running (mining speed > 0)
        int miningSpeed = entity.getMiningSpeed();
        if (miningSpeed <= 0) {
            return;
        }
        
        // Update animation state
        totalTickDelta += tickDelta;
        
        // Save the current matrix state
        matrices.push();
        
        // Position the diamond above the quarry block (centered, 1.5 blocks above)
        matrices.translate(0.5, 1.5, 0.5);
        
        // Make the diamond rotate around the Y axis
        float rotationAmount = (totalTickDelta / 20.0f) % 360.0f;
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotationAmount));
        
        // Make the diamond pulse in size
        float pulseAmount = (MathHelper.sin(totalTickDelta / 10.0f) * 0.2f) + 0.8f;
        matrices.scale(pulseAmount, pulseAmount, pulseAmount);
        
        // Change the diamond color based on mining speed (green to red gradient)
        float speedRatio = Math.min(1.0f, miningSpeed / 20.0f); // 20 blocks/sec is max
        float red = speedRatio;
        float green = 1.0f - (speedRatio * 0.5f);
        float blue = 0.3f;
        float alpha = 0.7f; // Semi-transparent
        
        // Get the vertex consumer for translucent rendering
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getTranslucent());
        
        // Render the diamond shape
        renderDiamond(matrices, buffer, red, green, blue, alpha);
        
        // Restore the matrix state
        matrices.pop();
    }
    
    /**
     * Renders a diamond shape using triangles
     */
    private void renderDiamond(MatrixStack matrices, VertexConsumer buffer, 
                               float red, float green, float blue, float alpha) {
        // Get the transformation matrix
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        
        // Diamond vertices
        float size = 0.3f;
        
        // Top point
        float topX = 0;
        float topY = size;
        float topZ = 0;
        
        // Bottom point
        float bottomX = 0;
        float bottomY = -size;
        float bottomZ = 0;
        
        // Middle points (diamond cross-section)
        float frontX = 0;
        float frontZ = size;
        float backX = 0;
        float backZ = -size;
        float leftX = -size;
        float leftZ = 0;
        float rightX = size;
        float rightZ = 0;
        
        float midY = 0;
        
        // Top half triangles (4 triangles)
        // Top-Front-Right
        addVertex(matrix, buffer, topX, topY, topZ, red, green, blue, alpha);
        addVertex(matrix, buffer, frontX, midY, frontZ, red, green, blue, alpha);
        addVertex(matrix, buffer, rightX, midY, rightZ, red, green, blue, alpha);
        
        // Top-Right-Back
        addVertex(matrix, buffer, topX, topY, topZ, red, green, blue, alpha);
        addVertex(matrix, buffer, rightX, midY, rightZ, red, green, blue, alpha);
        addVertex(matrix, buffer, backX, midY, backZ, red, green, blue, alpha);
        
        // Top-Back-Left
        addVertex(matrix, buffer, topX, topY, topZ, red, green, blue, alpha);
        addVertex(matrix, buffer, backX, midY, backZ, red, green, blue, alpha);
        addVertex(matrix, buffer, leftX, midY, leftZ, red, green, blue, alpha);
        
        // Top-Left-Front
        addVertex(matrix, buffer, topX, topY, topZ, red, green, blue, alpha);
        addVertex(matrix, buffer, leftX, midY, leftZ, red, green, blue, alpha);
        addVertex(matrix, buffer, frontX, midY, frontZ, red, green, blue, alpha);
        
        // Bottom half triangles (4 triangles)
        // Bottom-Front-Right
        addVertex(matrix, buffer, bottomX, bottomY, bottomZ, red, green, blue, alpha);
        addVertex(matrix, buffer, frontX, midY, frontZ, red, green, blue, alpha);
        addVertex(matrix, buffer, rightX, midY, rightZ, red, green, blue, alpha);
        
        // Bottom-Right-Back
        addVertex(matrix, buffer, bottomX, bottomY, bottomZ, red, green, blue, alpha);
        addVertex(matrix, buffer, rightX, midY, rightZ, red, green, blue, alpha);
        addVertex(matrix, buffer, backX, midY, backZ, red, green, blue, alpha);
        
        // Bottom-Back-Left
        addVertex(matrix, buffer, bottomX, bottomY, bottomZ, red, green, blue, alpha);
        addVertex(matrix, buffer, backX, midY, backZ, red, green, blue, alpha);
        addVertex(matrix, buffer, leftX, midY, leftZ, red, green, blue, alpha);
        
        // Bottom-Left-Front
        addVertex(matrix, buffer, bottomX, bottomY, bottomZ, red, green, blue, alpha);
        addVertex(matrix, buffer, leftX, midY, leftZ, red, green, blue, alpha);
        addVertex(matrix, buffer, frontX, midY, frontZ, red, green, blue, alpha);
    }
    
    /**
     * Helper method to add a vertex to the buffer
     */
    private void addVertex(Matrix4f matrix, VertexConsumer buffer, 
                          float x, float y, float z, 
                          float red, float green, float blue, float alpha) {
        buffer.vertex(matrix, x, y, z)
              .color(red, green, blue, alpha)
              .normal(0, 1, 0) // Normal pointing up
              .light(0xF000F0)
              .overlay(0)
              .texture(0, 0);
              
    }
} 