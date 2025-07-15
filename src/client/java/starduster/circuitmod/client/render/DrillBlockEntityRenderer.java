package starduster.circuitmod.client.render;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import starduster.circuitmod.block.entity.DrillBlockEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.render.model.BlockModelPart;

import java.util.ArrayList;
import java.util.List;

public class DrillBlockEntityRenderer implements BlockEntityRenderer<DrillBlockEntity> {
    
    public DrillBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
        // Constructor for renderer registration
    }
    
    @Override
    public void render(DrillBlockEntity blockEntity, float tickDelta, MatrixStack matrices, 
                      VertexConsumerProvider vertexConsumers, int light, int overlay, Vec3d cameraPos) {
        
        // Debug: Check if render method is being called
        System.out.println("[DEBUG] DrillBlockEntityRenderer.render() called for " + blockEntity.getPos());
        
        // Get the current mining progress and position
        int miningProgress = blockEntity.getCurrentMiningProgress();
        BlockPos miningPos = blockEntity.getCurrentMiningPos();
        
        System.out.println("[DEBUG] Mining progress: " + miningProgress + ", mining position: " + miningPos);
        
        // Only render breaking overlay if we're actually mining
        if (miningProgress > 0 && miningPos != null) {
            System.out.println("[DEBUG] Calling renderBreakingOverlay");
            renderBreakingOverlay(matrices, vertexConsumers, light, overlay, miningProgress, miningPos, blockEntity);
        } else {
            System.out.println("[DEBUG] Not rendering breaking overlay - progress: " + miningProgress + ", pos: " + miningPos);
        }
    }
    
    private void renderBreakingOverlay(MatrixStack matrices, VertexConsumerProvider vertexConsumers, 
                                      int light, int overlay, int progress, BlockPos blockPos, DrillBlockEntity blockEntity) {
        // Debug: Check if we're being called
        System.out.println("[DEBUG] renderBreakingOverlay called with progress: " + progress + " at " + blockPos);
        
        // Calculate which destroy stage to use (0-9)
        int destroyStage = (progress * 10) / 100;
        destroyStage = Math.min(destroyStage, 9); // Clamp to 0-9
        
        System.out.println("[DEBUG] Calculated destroyStage: " + destroyStage);
        
        if (destroyStage < 0) {
            System.out.println("[DEBUG] destroyStage < 0, returning early");
            return;
        }
        
        // Get the block state at the mining position
        World world = MinecraftClient.getInstance().world;
        if (world == null) {
            System.out.println("[DEBUG] world is null, returning early");
            return;
        }
        
        BlockState blockState = world.getBlockState(blockPos);
        if (blockState.isAir()) {
            System.out.println("[DEBUG] blockState is air, returning early");
            return;
        }
        
        System.out.println("[DEBUG] Block at " + blockPos + " is: " + blockState.getBlock().getName().getString());
        
        // Get the block render manager
        MinecraftClient client = MinecraftClient.getInstance();
        BlockRenderManager blockRenderManager = client.getBlockRenderManager();
        
        if (blockRenderManager == null) {
            System.out.println("[DEBUG] blockRenderManager is null, returning early");
            return;
        }
        
        matrices.push();
        
        // Translate to the block position relative to the drill block entity
        BlockPos drillPos = blockEntity.getPos();
        matrices.translate(
            blockPos.getX() - drillPos.getX(),
            blockPos.getY() - drillPos.getY(),
            blockPos.getZ() - drillPos.getZ()
        );
        
        System.out.println("[DEBUG] Translated to: (" + (blockPos.getX() - drillPos.getX()) + ", " + 
                          (blockPos.getY() - drillPos.getY()) + ", " + (blockPos.getZ() - drillPos.getZ()) + ")");
        
        try {
            // Get the correct vertex consumer for damage rendering
            // Use the breaking overlay render layer with the block's texture
            Identifier textureId = blockState.getBlock().asItem().getRegistryEntry().registryKey().getValue();
            System.out.println("[DEBUG] Texture ID: " + textureId);
            
            VertexConsumer vertexConsumer = vertexConsumers.getBuffer(
                RenderLayer.getBlockBreaking(textureId)
            );
            
            // Calculate the overlay UV coordinates based on destroy stage
            // Convert progress (0-100) to float (0.0-1.0) for OverlayTexture.getUv()
            float progressFloat = destroyStage / 9.0f; // Convert 0-9 to 0.0-1.0
            int overlayUV = OverlayTexture.getUv(progressFloat, false); // false = not hurt
            
            System.out.println("[DEBUG] progressFloat: " + progressFloat + ", overlayUV: " + overlayUV);
            
            // Get the block model and render it with our custom overlay
            BlockStateModel blockStateModel = blockRenderManager.getModel(blockState);
            List<BlockModelPart> parts = new ArrayList<>();
            blockStateModel.addParts(world.getRandom(), parts);
            
            System.out.println("[DEBUG] Got " + parts.size() + " model parts");
            
            // Render the breaking overlay using the block model renderer
            blockRenderManager.getModelRenderer().render(world, parts, blockState, blockPos, matrices, vertexConsumer, true, overlayUV);
            
            System.out.println("[DEBUG] Rendering completed successfully");
            
        } catch (Exception e) {
            System.out.println("[DEBUG] Exception during rendering: " + e.getMessage());
            e.printStackTrace();
        }
        
        matrices.pop();
    }
} 