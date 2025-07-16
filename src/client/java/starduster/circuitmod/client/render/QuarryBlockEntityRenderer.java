package starduster.circuitmod.client.render;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import starduster.circuitmod.block.entity.QuarryBlockEntity;

import java.util.List;

public class QuarryBlockEntityRenderer implements BlockEntityRenderer<QuarryBlockEntity> {
    
    public QuarryBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
    }

    @Override
    public void render(QuarryBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, Vec3d cameraPos) {
        // Get the perimeter positions from the quarry
        List<BlockPos> perimeterPositions = entity.getPerimeterPositions();
        
        // Debug logging (only log occasionally to avoid spam)
        if (System.currentTimeMillis() % 5000 < 50) { // Log every ~5 seconds
            System.out.println("[QUARRY-RENDER] Rendering quarry at " + entity.getPos() + 
                ", perimeter positions: " + perimeterPositions.size() + 
                ", mining area: " + entity.getMiningWidth() + "x" + entity.getMiningLength());
        }
        
        if (perimeterPositions.isEmpty()) {
            return;
        }
        
        try {
            // Render lines connecting the perimeter points
            renderPerimeterLines(matrices, perimeterPositions, 0xFF00FF00); // Green lines
            
            if (System.currentTimeMillis() % 5000 < 50) {
                System.out.println("[QUARRY-RENDER] Rendered perimeter lines");
            }
        } catch (Exception e) {
            System.err.println("[QUARRY-RENDER] Error rendering quarry: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void renderPerimeterLines(MatrixStack matrices, List<BlockPos> perimeterPositions, int color) {
        if (perimeterPositions.size() < 4) {
            return; // Need at least 4 points for a rectangle
        }
        
        // Extract color components
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        int alpha = 255;
        
        // Get tessellator and begin buffer for lines
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR);
        
        // Get the transformation matrix
        var matrix = matrices.peek().getPositionMatrix();
        
        // Draw lines connecting the perimeter points
        // We'll draw lines between consecutive points to form the perimeter
        for (int i = 0; i < perimeterPositions.size(); i++) {
            BlockPos current = perimeterPositions.get(i);
            BlockPos next = perimeterPositions.get((i + 1) % perimeterPositions.size());
            
            // Draw line from current to next point
            float x1 = current.getX() + 0.5f;
            float y1 = current.getY() + 0.1f; // Slightly above ground
            float z1 = current.getZ() + 0.5f;
            
            float x2 = next.getX() + 0.5f;
            float y2 = next.getY() + 0.1f;
            float z2 = next.getZ() + 0.5f;
            
            // Add the line vertices
            buffer.vertex(matrix, x1, y1, z1).color(red, green, blue, alpha);
            buffer.vertex(matrix, x2, y2, z2).color(red, green, blue, alpha);
        }
        
        // Draw the buffer - the buffer.end() should automatically render the lines
        buffer.end();
    }
} 