package starduster.circuitmod.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import starduster.circuitmod.block.entity.ItemPipeBlockEntity;

@Environment(EnvType.CLIENT)
public class ItemPipeBlockEntityRenderer implements BlockEntityRenderer<ItemPipeBlockEntity> {
    
    public ItemPipeBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        // You can store context if needed (e.g., for models)
    }

    @Override
    public void render(ItemPipeBlockEntity pipeEntity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay, Vec3d cameraPos) {
        if (!pipeEntity.isEmpty()) { 
            ItemStack stack = pipeEntity.getStack(0);
            if (!stack.isEmpty()) {
                matrices.push();
                
                // Compute animation progress (0.0 to 1.0)
                float progress = tickDelta;
                Direction origin = pipeEntity.getLastInputDirection();
                double x = 0.5, y = 0.5, z = 0.5;  // default to center

                if (origin != null) {
                    // Set start offset near the face where the item came from
                    double start = 0.15;   // 0.15 = 15% into the block from that face
                    double end   = 0.5;    // center of block
                    switch (origin) {
                        case NORTH -> z = lerp(progress, start, end);  // move from z=0.15 to 0.5
                        case SOUTH -> z = lerp(progress, 0.85, end);   // from z=0.85 to 0.5
                        case WEST  -> x = lerp(progress, start, end);  // from x=0.15 to 0.5
                        case EAST  -> x = lerp(progress, 0.85, end);   // from x=0.85 to 0.5
                        case DOWN  -> y = lerp(progress, start, end);  // from y=0.15 to 0.5
                        case UP    -> y = lerp(progress, 0.85, end);   // from y=0.85 to 0.5
                    }
                }

                // Apply translation to position the item
                matrices.translate(x, y, z);
                
                // Add a subtle rotation based on world time for dynamic effect
                float rotation = (pipeEntity.getWorld().getTime() + tickDelta) * 2.0f;
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(rotation));
                
                // Optionally scale the item down to fit into the pipe
                matrices.scale(0.5f, 0.5f, 0.5f);
                
                // Render the item using the ItemRenderer
                MinecraftClient.getInstance().getItemRenderer().renderItem(
                    stack, 
                    ItemDisplayContext.GROUND,   // Use GROUND for item display context
                    light, overlay, matrices, vertexConsumers, 
                    pipeEntity.getWorld(), 0
                );
                
                matrices.pop();
            }
        }
    }
    
    /**
     * Simple linear interpolation function
     */
    private static double lerp(float t, double start, double end) {
        return (1 - t) * start + t * end;
    }
} 