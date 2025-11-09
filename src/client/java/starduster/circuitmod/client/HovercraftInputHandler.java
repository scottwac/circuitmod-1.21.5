package starduster.circuitmod.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.entity.Entity;
import org.lwjgl.glfw.GLFW;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.entity.HovercraftEntity;
import starduster.circuitmod.network.ClientNetworking;

@Environment(EnvType.CLIENT)
public class HovercraftInputHandler {
    private static boolean lastForward = false;
    private static boolean lastBackward = false;
    private static boolean lastLeft = false;
    private static boolean lastRight = false;
    private static boolean lastUp = false;
    private static boolean lastDown = false;
    private static boolean lastBoost = false;
    
    /**
     * Initialize the hovercraft input handler
     */
    public static void initialize() {
        // Register tick event to check for input changes
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            
            // Check if player is riding a hovercraft
            Entity vehicle = client.player.getVehicle();
            if (!(vehicle instanceof HovercraftEntity hovercraft)) {
                // Reset last states when not in hovercraft
                resetLastStates();
                return;
            }
            
            // Get current input states
            boolean forward = client.options.forwardKey.isPressed();
            boolean backward = client.options.backKey.isPressed();
            boolean left = client.options.leftKey.isPressed();
            boolean right = client.options.rightKey.isPressed();
            boolean up = client.options.jumpKey.isPressed(); // Space for up
            
            // Check for Alt key (left or right Alt) using GLFW
            long windowHandle = client.getWindow().getHandle();
            boolean altLeft = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS;
            boolean altRight = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
            boolean down = altLeft || altRight; // Alt for down
            boolean boost = client.options.sprintKey.isPressed(); // Ctrl for boost
            
            // Feed the current input back into the local hovercraft entity so it can predict motion immediately
            hovercraft.setInputs(forward, backward, left, right, up, down, boost);
            
            // Send packet every tick to ensure responsiveness
            // Only log when inputs change to avoid spam
            boolean inputsChanged = forward != lastForward || backward != lastBackward || 
                left != lastLeft || right != lastRight || 
                up != lastUp || down != lastDown || boost != lastBoost;
            
            if (inputsChanged) {
                Circuitmod.LOGGER.info("[HOVERCRAFT CLIENT] Input change: F={} B={} L={} R={} U={} D={} BOOST={}", 
                    forward, backward, left, right, up, down, boost);
            }
            
            // Always send packet to ensure server has current state
            ClientNetworking.sendHovercraftInput(
                hovercraft.getId(), 
                forward, backward, 
                left, right, 
                up, down,
                boost
            );
            
            // Update last states
            lastForward = forward;
            lastBackward = backward;
            lastLeft = left;
            lastRight = right;
            lastUp = up;
            lastDown = down;
            lastBoost = boost;
        });
    }
    
    private static void resetLastStates() {
        lastForward = false;
        lastBackward = false;
        lastLeft = false;
        lastRight = false;
        lastUp = false;
        lastDown = false;
        lastBoost = false;
    }
}
