package starduster.circuitmod.client.render;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import starduster.circuitmod.block.entity.QuarryBlockEntity;

public class QuarryBlockEntityRenderer implements BlockEntityRenderer<QuarryBlockEntity> {
    
    public QuarryBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
        // Constructor for renderer registration
    }
    
    @Override
    public void render(QuarryBlockEntity blockEntity, float tickDelta, MatrixStack matrices, 
                      VertexConsumerProvider vertexConsumers, int light, int overlay, Vec3d cameraPos) {
        
        // Get the current breaking progress from your block entity
        int breakingProgress = blockEntity.getCurrentMiningProgress();
        
        if (breakingProgress > 0) {
            // Get the block position being mined
            BlockPos miningPos = blockEntity.getCurrentMiningPos();
            
            if (miningPos != null) {
                // Render the breaking overlay
                renderBreakingOverlay(matrices, vertexConsumers, light, overlay, breakingProgress, miningPos, blockEntity.getPos());
            }
        }
    }
    
    private void renderBreakingOverlay(MatrixStack matrices, VertexConsumerProvider vertexConsumers, 
                                      int light, int overlay, int progress, BlockPos blockPos, BlockPos quarryPos) {
        
        // Calculate which destroy stage to use (0-9)
        int destroyStage = Math.min((progress * 10) / 100, 9);
        
        if (destroyStage <= 0) {
            return;
        }
        
        // Get the world and check the block
        World world = MinecraftClient.getInstance().world;
        if (world == null) {
            return;
        }
        
        BlockState blockState = world.getBlockState(blockPos);
        if (blockState.isAir()) {
            return;
        }
        
        // Get the breaking texture identifier
        Identifier breakingTexture = Identifier.of("minecraft", "textures/block/destroy_stage_" + destroyStage + ".png");
        
        // Get the render layer for breaking overlay - use translucent for proper blending
        RenderLayer renderLayer = RenderLayer.getEntityTranslucent(breakingTexture);
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(renderLayer);
        
        matrices.push();
        
        // Translate to the block position relative to the quarry
        Vec3d offset = Vec3d.of(blockPos.subtract(quarryPos));
        matrices.translate(offset.x, offset.y, offset.z);
        
        // Render a simple cube overlay with the breaking texture
        renderCubeOverlay(matrices, vertexConsumer, light, overlay);
        
        matrices.pop();
    }
    
    private void renderCubeOverlay(MatrixStack matrices, VertexConsumer vertexConsumer, int light, int overlay) {
        // Render all 6 faces of the cube with the breaking texture
        float min = 0.0f;
        float max = 1.0f;
        
        // Get the matrix for transformations
        var matrix = matrices.peek().getPositionMatrix();
        var matrixEntry = matrices.peek();
        
        // Top face (Y+)
        vertexConsumer.vertex(matrix, min, max, min).color(255, 255, 255, 255).texture(0.0f, 0.0f).overlay(overlay).light(light).normal(matrixEntry, 0.0f, 1.0f, 0.0f);
        vertexConsumer.vertex(matrix, max, max, min).color(255, 255, 255, 255).texture(1.0f, 0.0f).overlay(overlay).light(light).normal(matrixEntry, 0.0f, 1.0f, 0.0f);
        vertexConsumer.vertex(matrix, max, max, max).color(255, 255, 255, 255).texture(1.0f, 1.0f).overlay(overlay).light(light).normal(matrixEntry, 0.0f, 1.0f, 0.0f);
        vertexConsumer.vertex(matrix, min, max, max).color(255, 255, 255, 255).texture(0.0f, 1.0f).overlay(overlay).light(light).normal(matrixEntry, 0.0f, 1.0f, 0.0f);
        
        // Bottom face (Y-)
        vertexConsumer.vertex(matrix, min, min, max).color(255, 255, 255, 255).texture(0.0f, 1.0f).overlay(overlay).light(light).normal(matrixEntry, 0.0f, -1.0f, 0.0f);
        vertexConsumer.vertex(matrix, max, min, max).color(255, 255, 255, 255).texture(1.0f, 1.0f).overlay(overlay).light(light).normal(matrixEntry, 0.0f, -1.0f, 0.0f);
        vertexConsumer.vertex(matrix, max, min, min).color(255, 255, 255, 255).texture(1.0f, 0.0f).overlay(overlay).light(light).normal(matrixEntry, 0.0f, -1.0f, 0.0f);
        vertexConsumer.vertex(matrix, min, min, min).color(255, 255, 255, 255).texture(0.0f, 0.0f).overlay(overlay).light(light).normal(matrixEntry, 0.0f, -1.0f, 0.0f);
        
        // North face (Z-)
        vertexConsumer.vertex(matrix, min, min, min).color(255, 255, 255, 255).texture(0.0f, 1.0f).overlay(overlay).light(light).normal(matrixEntry, 0.0f, 0.0f, -1.0f);
        vertexConsumer.vertex(matrix, max, min, min).color(255, 255, 255, 255).texture(1.0f, 1.0f).overlay(overlay).light(light).normal(matrixEntry, 0.0f, 0.0f, -1.0f);
        vertexConsumer.vertex(matrix, max, max, min).color(255, 255, 255, 255).texture(1.0f, 0.0f).overlay(overlay).light(light).normal(matrixEntry, 0.0f, 0.0f, -1.0f);
        vertexConsumer.vertex(matrix, min, max, min).color(255, 255, 255, 255).texture(0.0f, 0.0f).overlay(overlay).light(light).normal(matrixEntry, 0.0f, 0.0f, -1.0f);
        
        // South face (Z+)
        vertexConsumer.vertex(matrix, min, max, max).color(255, 255, 255, 255).texture(0.0f, 0.0f).overlay(overlay).light(light).normal(matrixEntry, 0.0f, 0.0f, 1.0f);
        vertexConsumer.vertex(matrix, max, max, max).color(255, 255, 255, 255).texture(1.0f, 0.0f).overlay(overlay).light(light).normal(matrixEntry, 0.0f, 0.0f, 1.0f);
        vertexConsumer.vertex(matrix, max, min, max).color(255, 255, 255, 255).texture(1.0f, 1.0f).overlay(overlay).light(light).normal(matrixEntry, 0.0f, 0.0f, 1.0f);
        vertexConsumer.vertex(matrix, min, min, max).color(255, 255, 255, 255).texture(0.0f, 1.0f).overlay(overlay).light(light).normal(matrixEntry, 0.0f, 0.0f, 1.0f);
        
        // West face (X-)
        vertexConsumer.vertex(matrix, min, min, max).color(255, 255, 255, 255).texture(0.0f, 1.0f).overlay(overlay).light(light).normal(matrixEntry, -1.0f, 0.0f, 0.0f);
        vertexConsumer.vertex(matrix, min, min, min).color(255, 255, 255, 255).texture(1.0f, 1.0f).overlay(overlay).light(light).normal(matrixEntry, -1.0f, 0.0f, 0.0f);
        vertexConsumer.vertex(matrix, min, max, min).color(255, 255, 255, 255).texture(1.0f, 0.0f).overlay(overlay).light(light).normal(matrixEntry, -1.0f, 0.0f, 0.0f);
        vertexConsumer.vertex(matrix, min, max, max).color(255, 255, 255, 255).texture(0.0f, 0.0f).overlay(overlay).light(light).normal(matrixEntry, -1.0f, 0.0f, 0.0f);
        
        // East face (X+)
        vertexConsumer.vertex(matrix, max, max, max).color(255, 255, 255, 255).texture(0.0f, 0.0f).overlay(overlay).light(light).normal(matrixEntry, 1.0f, 0.0f, 0.0f);
        vertexConsumer.vertex(matrix, max, max, min).color(255, 255, 255, 255).texture(1.0f, 0.0f).overlay(overlay).light(light).normal(matrixEntry, 1.0f, 0.0f, 0.0f);
        vertexConsumer.vertex(matrix, max, min, min).color(255, 255, 255, 255).texture(1.0f, 1.0f).overlay(overlay).light(light).normal(matrixEntry, 1.0f, 0.0f, 0.0f);
        vertexConsumer.vertex(matrix, max, min, max).color(255, 255, 255, 255).texture(0.0f, 1.0f).overlay(overlay).light(light).normal(matrixEntry, 1.0f, 0.0f, 0.0f);
    }
} 