package starduster.circuitmod.network;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import starduster.circuitmod.Circuitmod;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class ClientNetworkAnimator {
    private static final List<Animation> animations = new ArrayList<>();
    
    public record Animation(ItemStack stack, BlockPos from, BlockPos to, long start, int duration) {}
    
    /**
     * Add a new animation to the queue
     */
    public static void addAnimation(ItemStack stack, BlockPos from, BlockPos to, long start, int duration) {
        animations.add(new Animation(stack, from, to, start, duration));
        Circuitmod.LOGGER.info("[ANIMATOR] Added animation: {} from {} to {}", 
            stack.getItem().getName().getString(), from, to);
    }
    
    /**
     * Initialize the animator with render and tick callbacks
     */
    public static void initialize() {
        // Register tick callback to clean up finished animations
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long worldTime = client.world != null ? client.world.getTime() : 0;
            animations.removeIf(anim -> {
                double t = (worldTime - anim.start()) / (double)anim.duration();
                if (t >= 1.0) {
                    Circuitmod.LOGGER.info("[ANIMATOR] Removed completed animation: {} from {} to {}", 
                        anim.stack().getItem().getName().getString(), anim.from(), anim.to());
                    return true;  // done
                }
                return false;
            });
        });
        
        // Register render callback to draw all active animations
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            renderAll(context.matrixStack(), context.camera().getPos(), 
                     context.consumers(), 15728880, 0); // Use default light value
        });
        
        Circuitmod.LOGGER.info("[ANIMATOR] ClientNetworkAnimator initialized");
    }
    
    /**
     * Render all active animations
     */
    private static void renderAll(MatrixStack ms, Vec3d cameraPos, 
                                  VertexConsumerProvider vc, int light, int overlay) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        
        long worldTime = client.world.getTime();
        ItemRenderer itemRenderer = client.getItemRenderer();
        
        for (Animation anim : animations) {
            double t = (worldTime - anim.start()) / (double)anim.duration();
            t = MathHelper.clamp((float)t, 0f, 1f);
            
            // Interpolate position between from and to
            Vec3d fromCenter = Vec3d.ofCenter(anim.from());
            Vec3d toCenter = Vec3d.ofCenter(anim.to());
            Vec3d pos = fromCenter.lerp(toCenter, t);
            
            // Raise the item above the block center to avoid depth testing issues
            Vec3d raised = pos.add(0, 0.75, 0);
            
            // Move into world coordinates relative to camera
            ms.push();
            ms.translate(raised.x - cameraPos.x, raised.y - cameraPos.y, raised.z - cameraPos.z);
            ms.scale(0.5f, 0.5f, 0.5f);
            
            // Draw on top of everything by disabling depth test
            
            itemRenderer.renderItem(anim.stack(), ItemDisplayContext.GROUND, light, overlay, ms, vc, client.world, 0);
            
            
            ms.pop();
        }
    }
    
    /**
     * Get the number of active animations (for debugging)
     */
    public static int getActiveAnimationCount() {
        return animations.size();
    }
    
    /**
     * Clear all animations (for debugging)
     */
    public static void clearAllAnimations() {
        animations.clear();
        Circuitmod.LOGGER.info("[ANIMATOR] Cleared all animations");
    }
} 