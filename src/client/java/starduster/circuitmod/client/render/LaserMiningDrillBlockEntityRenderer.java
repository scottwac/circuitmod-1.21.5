package starduster.circuitmod.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import starduster.circuitmod.block.entity.LaserMiningDrillBlockEntity;

@Environment(EnvType.CLIENT)
public class LaserMiningDrillBlockEntityRenderer implements BlockEntityRenderer<LaserMiningDrillBlockEntity> {

    // Use vanilla end crystal beam texture for the laser effect
    private static final Identifier LASER_BEAM_TEXTURE = Identifier.ofVanilla("textures/entity/end_crystal/end_crystal_beam.png");
    private static final RenderLayer LASER_BEAM_LAYER = RenderLayer.getEntitySmoothCutout(LASER_BEAM_TEXTURE);

    public LaserMiningDrillBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
    }

    @Override
    public void render(LaserMiningDrillBlockEntity entity,
                       float tickDelta,
                       MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers,
                       int light,
                       int overlay,
                       Vec3d cameraPos) {

        // Only render when mining is enabled
        if (!entity.isMiningEnabled()) {
            return;
        }

        // Calculate beam start position (center of the drill)
        float startX = 0.5f, startY = 0.5f, startZ = 0.5f;
        
        // Calculate beam end position
        float endX, endY, endZ;
        
        // Get the current mining position if available, otherwise use a default direction
        BlockPos currentMiningPos = entity.getCurrentMiningPos();
        if (currentMiningPos != null) {
            // Beam goes to the actual mining target
            endX = currentMiningPos.getX() - entity.getPos().getX() + 0.5f;
            endY = currentMiningPos.getY() - entity.getPos().getY() + 0.5f;
            endZ = currentMiningPos.getZ() - entity.getPos().getZ() + 0.5f;
        } else {
            // Beam goes in the mining direction (using configured depth)
            Direction miningDirection = entity.getMiningDirection();
            if (miningDirection == null) {
                return;
            }
            
            float beamLength = entity.getMiningDepth();
            endX = startX;
            endY = startY;
            endZ = startZ;
            
            switch (miningDirection) {
                case NORTH:
                    endZ = startZ - beamLength;
                    break;
                case SOUTH:
                    endZ = startZ + beamLength;
                    break;
                case EAST:
                    endX = startX + beamLength;
                    break;
                case WEST:
                    endX = startX - beamLength;
                    break;
                case UP:
                    endY = startY + beamLength;
                    break;
                case DOWN:
                    endY = startY - beamLength;
                    break;
            }
        }

        // Render the thick cylindrical beam
        renderLaserBeam(
            startX, startY, startZ,
            endX, endY, endZ,
            entity.getWorld().getTime() + tickDelta, // Animation time
            matrices,
            vertexConsumers,
            light
        );
    }

    /**
     * Render a thick cylindrical laser beam between two points
     */
    private void renderLaserBeam(float startX, float startY, float startZ,
                                 float endX, float endY, float endZ,
                                 float tickProgress,
                                 MatrixStack matrices,
                                 VertexConsumerProvider vertexConsumers,
                                 int light) {
        
        // Calculate beam direction vector
        float dx = endX - startX;
        float dy = endY - startY;
        float dz = endZ - startZ;
        
        // Calculate beam length
        float beamLength = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
        
        matrices.push();
        
        // Move to start position
        matrices.translate(startX, startY, startZ);
        
        // Calculate rotation angles for the beam
        float horizontalDistance = MathHelper.sqrt(dx * dx + dz * dz);
        
        // Rotate around Y axis (horizontal rotation)
        if (horizontalDistance > 0.001f) {
            float yaw = (float)(-Math.atan2(dz, dx)) - (float)(Math.PI / 2);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation(yaw));
        }
        
        // Rotate around X axis (vertical rotation)
        if (beamLength > 0.001f) {
            float pitch = (float)(-Math.atan2(horizontalDistance, dy)) - (float)(Math.PI / 2);
            matrices.multiply(RotationAxis.POSITIVE_X.rotation(pitch));
        }
        
        // Get vertex consumer for the beam - use a render layer that's less affected by fog
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getLightning());
        
        // Color cycling animation (changes every 2 seconds)
        int colorIndex = (int)(tickProgress / 40) % 6; // 40 ticks = 2 seconds (20 ticks per second)
        int[] colors = {
            0xFF0000, // Red
            0x00FF00, // Green  
            0x0000FF, // Blue
            0xFFFF00, // Yellow
            0xFF00FF, // Magenta
            0x00FFFF  // Cyan
        };
        
        int currentColor = colors[colorIndex];
        int r = (currentColor >> 16) & 0xFF;
        int g = (currentColor >> 8) & 0xFF;
        int b = currentColor & 0xFF;
        
        MatrixStack.Entry entry = matrices.peek();
        
        // Create a thick cylindrical beam using multiple layers of lines
        float beamRadius = 0.25f; // Half block thickness
        int numLayers = 40; // Number of concentric circles (5x more)
        int linesPerLayer = 80; // Lines per circle (5x more)
        
        for (int layer = 0; layer < numLayers; layer++) {
            float currentRadius = (layer + 1) * (beamRadius / numLayers);
            
            for (int i = 0; i < linesPerLayer; i++) {
                float angle = (i * 2 * (float)Math.PI) / linesPerLayer;
                float nextAngle = ((i + 1) * 2 * (float)Math.PI) / linesPerLayer;
                
                // Calculate points on the circle
                float x1 = MathHelper.cos(angle) * currentRadius;
                float y1 = MathHelper.sin(angle) * currentRadius;
                float x2 = MathHelper.cos(nextAngle) * currentRadius;
                float y2 = MathHelper.sin(nextAngle) * currentRadius;
                
                // Draw lines from start to end of the beam with full brightness
                vertexConsumer.vertex(entry, x1, y1, 0.0f)
                    .color(r, g, b, 255) // Current cycling color
                    .normal(entry, 0.0f, 0.0f, 1.0f)
                    .light(15728880); // Full brightness (15,15,15,15)
                vertexConsumer.vertex(entry, x1, y1, beamLength)
                    .color(255, 255, 255, 255) // White at the end
                    .normal(entry, 0.0f, 0.0f, 1.0f)
                    .light(15728880); // Full brightness (15,15,15,15)
                
                // Draw connecting lines around the circle with full brightness
                vertexConsumer.vertex(entry, x1, y1, 0.0f)
                    .color(r, g, b, 255)
                    .normal(entry, 0.0f, 0.0f, 1.0f)
                    .light(15728880); // Full brightness
                vertexConsumer.vertex(entry, x2, y2, 0.0f)
                    .color(r, g, b, 255)
                    .normal(entry, 0.0f, 0.0f, 1.0f)
                    .light(15728880); // Full brightness
                
                vertexConsumer.vertex(entry, x1, y1, beamLength)
                    .color(255, 255, 255, 255)
                    .normal(entry, 0.0f, 0.0f, 1.0f)
                    .light(15728880); // Full brightness
                vertexConsumer.vertex(entry, x2, y2, beamLength)
                    .color(255, 255, 255, 255)
                    .normal(entry, 0.0f, 0.0f, 1.0f)
                    .light(15728880); // Full brightness
            }
        }
        
        // Add some cross-sectional lines for more density
        for (int i = 0; i < 20; i++) { // 5x more cross-sections
            float z = (i + 1) * (beamLength / 21);
            for (int j = 0; j < 40; j++) { // 5x more lines per cross-section
                float angle = (j * 2 * (float)Math.PI) / 40;
                float x = MathHelper.cos(angle) * beamRadius;
                float y = MathHelper.sin(angle) * beamRadius;
                
                vertexConsumer.vertex(entry, x, y, z)
                    .color(r, g, b, 255)
                    .normal(entry, 0.0f, 0.0f, 1.0f)
                    .light(15728880); // Full brightness
                vertexConsumer.vertex(entry, -x, -y, z)
                    .color(r, g, b, 255)
                    .normal(entry, 0.0f, 0.0f, 1.0f)
                    .light(15728880); // Full brightness
            }
        }
        
        matrices.pop();
    }

    @Override
    public boolean rendersOutsideBoundingBox(LaserMiningDrillBlockEntity blockEntity) {
        return true; // Laser beam extends beyond block bounds
    }

    @Override
    public int getRenderDistance() {
        return 4096; // Increased render distance for longer laser beams
    }
} 