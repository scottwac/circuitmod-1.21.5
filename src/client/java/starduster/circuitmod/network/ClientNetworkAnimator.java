package starduster.circuitmod.network;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import starduster.circuitmod.Circuitmod;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class ClientNetworkAnimator {
    private static final List<Animation> animations = new ArrayList<>();
    
    public record Animation(ItemStack stack, BlockPos from, BlockPos to, long start, int duration) {}
    
    /**
     * Add an animation for an item moving from one position to another
     */
    public static void addAnimation(ItemStack stack, BlockPos from, BlockPos to, long startTick, int duration) {
        // Create a deep copy of the ItemStack to ensure each animation has its own independent item
        ItemStack stackCopy = stack.copy();
        Animation anim = new Animation(stackCopy, from, to, startTick, duration);
        animations.add(anim);
        
        Circuitmod.LOGGER.info("[ANIMATOR] Added animation: {} from {} to {}", 
            stackCopy.getItem().getName().getString(), from, to);
    }
    
    /**
     * Initialize the animator with render and tick callbacks
     */
    public static void initialize() {
        // Register tick callback to clean up finished animations
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) return;
            
            long worldTime = client.world.getTime();
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

        // Render during the debug render pass, which is one of the very last render passes.
        // This ensures our items render on top of everything, including opaque pipes.
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(context -> {
            renderAll(
                context.matrixStack(),
                context.camera().getPos(),
                context.consumers(),
                15728880,
                OverlayTexture.DEFAULT_UV
            );
        });

        Circuitmod.LOGGER.info("[ANIMATOR] ClientNetworkAnimator initialized in debug render pass");
    }
    
    /**
     * Render all active animations
     */
    private static void renderAll(MatrixStack ms, Vec3d cam, 
                                  VertexConsumerProvider vc, int light, int overlay) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        long worldTime = client.world.getTime();
        ItemRenderer itemRenderer = client.getItemRenderer();

        // —————————————————————————
        // Save current OpenGL state
        boolean wasBlendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean wasDepthTestEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean wasDepthMaskEnabled = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean wasCullFaceEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        
        // 1) Enable blending for transparency
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // 2) Disable depth testing & depth writes so items render on top of pipes
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        
        // 3) Disable face culling so items are visible from all angles
        GL11.glDisable(GL11.GL_CULL_FACE);

        // 4) Draw your cubes
        for (Animation anim : animations) {
            double t = (worldTime - anim.start()) / (double)anim.duration();
            t = MathHelper.clamp((float)t, 0f, 1f);
            
            // Skip rendering when animation is very close to completion to prevent flashing
            if (t >= 0.95) {
                continue;
            }

            Vec3d from = Vec3d.ofCenter(anim.from());
            Vec3d to   = Vec3d.ofCenter(anim.to());
            Vec3d pos  = from.lerp(to, t);

            ms.push();
            ms.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
            ms.scale(0.5f, 0.5f, 0.5f);
            itemRenderer.renderItem(
                anim.stack(),
                ItemDisplayContext.GROUND,
                light,
                overlay,
                ms, vc, client.world, 0
            );
            ms.pop();
        }

        // 5) ** Restore original OpenGL state **
        GL11.glDepthMask(wasDepthMaskEnabled);
        if (wasDepthTestEnabled) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        } else {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
        
        if (wasBlendEnabled) {
            GL11.glEnable(GL11.GL_BLEND);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
        }
        
        if (wasCullFaceEnabled) {
            GL11.glEnable(GL11.GL_CULL_FACE);
        } else {
            GL11.glDisable(GL11.GL_CULL_FACE);
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