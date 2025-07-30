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
    // DEPRECATED: Keeping continuous animations for backwards compatibility but they won't be used
    private static final List<ContinuousPathAnimation> continuousAnimations = new ArrayList<>();
    
    public record Animation(ItemStack stack, BlockPos from, BlockPos to, long start, int duration) {}
    
    public record ContinuousPathAnimation(ItemStack stack, List<BlockPos> path, long start, int duration) {}
    
    /**
     * Add an animation for an item moving from one position to another.
     * This is now the primary method - called from ItemPipeBlockEntity after successful transfers.
     */
    public static void addAnimation(ItemStack stack, BlockPos from, BlockPos to, long startTick, int duration) {
        // Create a deep copy of the ItemStack to ensure each animation has its own independent item
        ItemStack stackCopy = stack.copy();
        Animation anim = new Animation(stackCopy, from, to, startTick, duration);
        
        synchronized (animations) {
            animations.add(anim);
        }
        
        Circuitmod.LOGGER.debug("[ANIMATOR] Added hop animation: {} from {} to {} (duration: {} ticks)", 
            stackCopy.getItem().getName().getString(), from, to, duration);
    }
    
    /**
     * DEPRECATED: Add a continuous path animation for an item following a complete path.
     * This is kept for backwards compatibility but converted to simple hop animation.
     */
    @Deprecated
    public static void addContinuousPathAnimation(ItemStack stack, List<BlockPos> path, long startTick, int duration) {
        if (path.size() < 2) return;
        
        Circuitmod.LOGGER.warn("[ANIMATOR] addContinuousPathAnimation is deprecated in hop-by-hop system. " +
            "Converting to simple hop animation. Path size: {}", path.size());
        
        // Convert to simple hop animation for the first segment only
        addAnimation(stack, path.get(0), path.get(1), startTick, 8);
    }
    
    /**
     * Initialize the animator with render and tick callbacks
     */
    public static void initialize() {
        // Register tick callback to clean up finished animations
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) return;
            
            long worldTime = client.world.getTime();
            
            // Clean up finished hop animations
            synchronized (animations) {
                animations.removeIf(anim -> {
                    double t = (worldTime - anim.start()) / (double)anim.duration();
                    if (t >= 1.0) {
                        Circuitmod.LOGGER.debug("[ANIMATOR] Removed completed hop animation: {} from {} to {}", 
                            anim.stack().getItem().getName().getString(), anim.from(), anim.to());
                        return true;  // done
                    }
                    return false;
                });
            }
            
            // Clean up any remaining continuous animations (deprecated)
            synchronized (continuousAnimations) {
                continuousAnimations.removeIf(anim -> {
                    double t = (worldTime - anim.start()) / (double)anim.duration();
                    if (t >= 1.0) {
                        Circuitmod.LOGGER.debug("[ANIMATOR] Removed deprecated continuous animation: {} with {} waypoints", 
                            anim.stack().getItem().getName().getString(), anim.path().size());
                        return true;  // done
                    }
                    return false;
                });
            }
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

        Circuitmod.LOGGER.info("[ANIMATOR] ClientNetworkAnimator initialized for hop-by-hop system");
    }
    
    /**
     * Render all active animations - updated for hop-by-hop system
     */
    private static void renderAll(MatrixStack ms, Vec3d cam, 
                                  VertexConsumerProvider vc, int light, int overlay) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        long worldTime = client.world.getTime();
        ItemRenderer itemRenderer = client.getItemRenderer();

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

        // 4) Render hop-by-hop animations
        synchronized (animations) {
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
                
                try {
                    itemRenderer.renderItem(
                        anim.stack(),
                        ItemDisplayContext.GROUND,
                        light,
                        overlay,
                        ms, vc, client.world, 0
                    );
                } catch (Exception e) {
                    Circuitmod.LOGGER.warn("[ANIMATOR] Error rendering hop animation {}: {}", 
                        anim.stack().getItem().getName().getString(), e.getMessage());
                }
                
                ms.pop();
            }
        }
        
        // Render any remaining continuous path animations (deprecated but kept for compatibility)
        synchronized (continuousAnimations) {
            for (ContinuousPathAnimation anim : continuousAnimations) {
                double t = (worldTime - anim.start()) / (double)anim.duration();
                t = MathHelper.clamp((float)t, 0f, 1f);
                
                // Skip rendering when animation is very close to completion to prevent flashing
                if (t >= 0.95) {
                    continue;
                }
                
                // Calculate position along the path
                Vec3d pos = calculatePositionAlongPath(anim.path(), t);

                ms.push();
                ms.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
                ms.scale(0.5f, 0.5f, 0.5f);
                
                try {
                    itemRenderer.renderItem(
                        anim.stack(),
                        ItemDisplayContext.GROUND,
                        light,
                        overlay,
                        ms, vc, client.world, 0
                    );
                } catch (Exception e) {
                    Circuitmod.LOGGER.warn("[ANIMATOR] Error rendering continuous animation {}: {}", 
                        anim.stack().getItem().getName().getString(), e.getMessage());
                }
                
                ms.pop();
            }
        }

        // 5) Restore original OpenGL state
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
        synchronized (animations) {
            return animations.size() + continuousAnimations.size();
        }
    }
    
    /**
     * Clear all animations (for debugging)
     */
    public static void clearAllAnimations() {
        synchronized (animations) {
            animations.clear();
        }
        synchronized (continuousAnimations) {
            continuousAnimations.clear();
        }
        Circuitmod.LOGGER.info("[ANIMATOR] Cleared all animations");
    }
    
    /**
     * Calculate the position along a path at a given time (0.0 to 1.0)
     * Kept for backwards compatibility with continuous animations
     */
    private static Vec3d calculatePositionAlongPath(List<BlockPos> path, double t) {
        if (path.size() < 2) {
            return Vec3d.ofCenter(path.get(0));
        }
        
        // Calculate total path length
        double totalLength = 0.0;
        for (int i = 0; i < path.size() - 1; i++) {
            Vec3d from = Vec3d.ofCenter(path.get(i));
            Vec3d to = Vec3d.ofCenter(path.get(i + 1));
            totalLength += from.distanceTo(to);
        }
        
        // Find the segment we're currently in
        double targetDistance = totalLength * t;
        double currentDistance = 0.0;
        
        for (int i = 0; i < path.size() - 1; i++) {
            Vec3d from = Vec3d.ofCenter(path.get(i));
            Vec3d to = Vec3d.ofCenter(path.get(i + 1));
            double segmentLength = from.distanceTo(to);
            
            if (currentDistance + segmentLength >= targetDistance) {
                // We're in this segment
                double segmentT = (targetDistance - currentDistance) / segmentLength;
                return from.lerp(to, segmentT);
            }
            
            currentDistance += segmentLength;
        }
        
        // If we get here, we're at the end
        return Vec3d.ofCenter(path.get(path.size() - 1));
    }
    
    /**
     * Debug method to log current animation status
     */
    public static void logAnimationStatus() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        
        long worldTime = client.world.getTime();
        synchronized (animations) {
            Circuitmod.LOGGER.info("[ANIMATOR] Active hop animations: {}", animations.size());
            for (int i = 0; i < Math.min(animations.size(), 5); i++) {
                Animation anim = animations.get(i);
                double t = (worldTime - anim.start()) / (double)anim.duration();
                Circuitmod.LOGGER.info("[ANIMATOR] Hop Animation {}: {} from {} to {} (progress: {:.2f})", 
                    i, anim.stack().getItem().getName().getString(), anim.from(), anim.to(), t);
            }
            if (animations.size() > 5) {
                Circuitmod.LOGGER.info("[ANIMATOR] ... and {} more hop animations", animations.size() - 5);
            }
        }
        
        synchronized (continuousAnimations) {
            if (!continuousAnimations.isEmpty()) {
                Circuitmod.LOGGER.info("[ANIMATOR] Active continuous animations (deprecated): {}", continuousAnimations.size());
            }
        }
    }
}