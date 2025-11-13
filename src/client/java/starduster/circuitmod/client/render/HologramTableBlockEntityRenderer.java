package starduster.circuitmod.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;
import starduster.circuitmod.block.entity.HologramTableBlockEntity;
import starduster.circuitmod.block.ModBlocks;

@Environment(EnvType.CLIENT)
public class HologramTableBlockEntityRenderer implements net.minecraft.client.render.block.entity.BlockEntityRenderer<HologramTableBlockEntity> {

    public HologramTableBlockEntityRenderer(net.minecraft.client.render.block.entity.BlockEntityRendererFactory.Context ctx) { }

    @Override
    public void render(HologramTableBlockEntity entity,
                       float tickDelta,
                       MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers,
                       int light,
                       int overlay,
                       Vec3d cameraPos) {
        
        if (entity.getWorld() == null || !entity.getWorld().isClient()) {
            return;
        }

        // Get target chunk coordinates (16x16 chunk) - uses offset for chaining
        int targetChunkX = entity.getTargetChunkX();
        int targetChunkZ = entity.getTargetChunkZ();
        
        // Debug: Log what chunk we're rendering
        if (entity.getChunkOffsetX() != 0 || entity.getChunkOffsetZ() != 0) {
            System.out.println("[HOLOGRAM-TABLE-RENDER] Entity at " + entity.getPos() + 
                " has offset (" + entity.getChunkOffsetX() + ", " + entity.getChunkOffsetZ() + 
                "), rendering chunk (" + targetChunkX + ", " + targetChunkZ + ")");
        }
        
        // Calculate chunk bounds (16x16 area) for the target chunk
        // Chunk X goes from targetChunkX*16 to (targetChunkX+1)*16 - 1
        int minX = targetChunkX * 16;
        int maxX = (targetChunkX + 1) * 16 - 1;
        int minZ = targetChunkZ * 16;
        int maxZ = (targetChunkZ + 1) * 16 - 1;
        
        // Calculate center of chunk for centering the hologram
        double chunkCenterX = (minX + maxX + 1) / 2.0;
        double chunkCenterZ = (minZ + maxZ + 1) / 2.0;
        
        matrices.push();
        
        MinecraftClient client = MinecraftClient.getInstance();
        ItemRenderer itemRenderer = client.getItemRenderer();
        var world = entity.getWorld();
        
        // Height above the table to render the hologram
        // Set so the lowest block starts half a block above the table surface
        float hologramHeight = 0.5f; // Start half a block above table surface
        
        // Scale factor for the hologram blocks (make them smaller and prevent overlap)
        float blockScale = 0.3f * 0.85f; // Smaller than constructor's 0.5f, scaled down to 0.85 to prevent overlap
        
        // Helper method to check if block should be filtered
        // Filter only fallen leaves and tall grass, but allow tree leaves and grass blocks
        java.util.function.Predicate<BlockState> shouldFilter = (blockState) -> {
            Block block = blockState.getBlock();
            // Filter the hologram table block itself
            if (block == ModBlocks.HOLOGRAM_TABLE) {
                return true;
            }
            // Filter tall grass and short grass (but allow grass blocks)
            if (block == Blocks.SHORT_GRASS || block == Blocks.TALL_GRASS) {
                return true;
            }
            // Filter ferns
            if (block == Blocks.FERN || block == Blocks.LARGE_FERN) {
                return true;
            }
            // Filter vines
            if (block == Blocks.VINE) {
                return true;
            }
            // Note: We allow tree leaves (BlockTags.LEAVES) and grass blocks (Blocks.GRASS_BLOCK)
            // Only filter fallen leaves if they exist as a separate block type
            // Fallen leaves are typically represented as leaf blocks with specific properties
            // For now, we'll allow all leaves - if needed, we can filter based on block properties later
            return false;
        };
        
        // Find the lowest Y to scan from (only scan surface blocks exposed to air)
        int scanEndY = 320; // Scan to world top
        // Only scan a limited depth below surface for surface blocks
        int maxDepthBelowSurface = 5; // Only scan 5 blocks below surface
        
        // Check if we can use cached blocks
        java.util.List<java.util.Map.Entry<BlockPos, BlockState>> blockEntries;
        if (!entity.needsRescan() && !entity.getCachedBlocks().isEmpty()) {
            // Use cached blocks
            blockEntries = new java.util.ArrayList<>();
            for (starduster.circuitmod.block.entity.HologramTableBlockEntity.BlockEntry entry : entity.getCachedBlocks()) {
                blockEntries.add(new java.util.AbstractMap.SimpleEntry<>(entry.pos, entry.state));
            }
        } else {
            // Need to scan - collect blocks
            blockEntries = new java.util.ArrayList<>();
        
        // Helper method to check if a block is exposed to air (surface block)
        // Only consider blocks exposed from top or sides, not from bottom
        java.util.function.BiFunction<BlockPos, BlockState, Boolean> isSurfaceBlock = (pos, state) -> {
            // Check top face first - surface blocks must have air or filtered block above
            BlockPos topPos = pos.up();
            if (topPos.getY() <= scanEndY) {
                if (topPos.getX() < minX || topPos.getX() > maxX || 
                    topPos.getZ() < minZ || topPos.getZ() > maxZ) {
                    // Outside bounds - consider as exposed (edge of chunk)
                    return true;
                }
                BlockState topState = world.getBlockState(topPos);
                if (topState.isAir() || shouldFilter.test(topState)) {
                    return true; // Has air or filtered block above - it's a surface block
                }
            }
            
            // Also check horizontal sides - if any side is exposed, it's a surface block
            BlockPos[] sideNeighbors = {
                pos.north(), pos.south(), pos.east(), pos.west()
            };
            for (BlockPos neighbor : sideNeighbors) {
                if (neighbor.getX() < minX || neighbor.getX() > maxX || 
                    neighbor.getZ() < minZ || neighbor.getZ() > maxZ) {
                    // Outside bounds - consider as exposed (edge of chunk)
                    return true;
                }
                BlockState neighborState = world.getBlockState(neighbor);
                if (neighborState.isAir() || shouldFilter.test(neighborState)) {
                    return true; // Has air or filtered block on side - it's a surface block
                }
            }
            return false; // Not exposed from top or sides
        };
        
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    // Get surface Y for this column
                    int surfaceY = entity.getSurfaceY(x, z);
                    // Only scan from surface down a limited depth, and up to sky
                    int scanStartY = Math.max(world.getBottomY(), surfaceY - maxDepthBelowSurface);
                    
                    // Scan from limited depth below surface all the way to sky
                    for (int y = scanStartY; y <= scanEndY; y++) {
                        BlockPos scanPos = new BlockPos(x, y, z);
                        BlockState scanBlock = world.getBlockState(scanPos);
                        
                        if (!scanBlock.isAir()) {
                            if (shouldFilter.test(scanBlock)) {
                                // Found filtered block (short grass, etc.) - skip it
                                continue;
                            } else {
                                // Found a non-filtered block - check if it's a surface block (exposed from top or sides)
                                if (isSurfaceBlock.apply(scanPos, scanBlock)) {
                                    blockEntries.add(new java.util.AbstractMap.SimpleEntry<>(scanPos, scanBlock));
                                }
                            }
                        }
                    }
                }
            }
            
            // Cache the scanned blocks
            java.util.List<starduster.circuitmod.block.entity.HologramTableBlockEntity.BlockEntry> cacheEntries = new java.util.ArrayList<>();
            for (java.util.Map.Entry<BlockPos, BlockState> entry : blockEntries) {
                cacheEntries.add(new starduster.circuitmod.block.entity.HologramTableBlockEntity.BlockEntry(entry.getKey(), entry.getValue()));
            }
            entity.setCachedBlocks(cacheEntries);
        }
        
        // Use a fixed Y reference point (sea level) so chained holograms align properly
        // All holograms will use Y=64 as the reference, so blocks at the same world Y
        // will appear at the same height in adjacent holograms
        int referenceY = 64; // Sea level - fixed reference for all holograms
        
        // Find the lowest Y in the blocks being rendered to ensure nothing clips below table
        int lowestYInHologram = Integer.MAX_VALUE;
        if (!blockEntries.isEmpty()) {
            for (java.util.Map.Entry<BlockPos, BlockState> entry : blockEntries) {
                int y = entry.getKey().getY();
                if (y < lowestYInHologram) {
                    lowestYInHologram = y;
                }
            }
        }
        
        // Calculate minimum Y offset to ensure lowest block is at least at table surface
        // If lowest block is below referenceY, we need to offset up
        double minYOffset = 0.0;
        if (lowestYInHologram != Integer.MAX_VALUE && lowestYInHologram < referenceY) {
            // Blocks below sea level - ensure they don't clip into table
            // The lowest block should be at least at hologramHeight above table surface
            double lowestBlockDy = (lowestYInHologram - referenceY) * (1.0f / 16.0f);
            // hologramHeight + 0.5 + lowestBlockDy should be >= hologramHeight
            // So we need: lowestBlockDy >= -0.5
            // If lowestBlockDy < -0.5, we need to add offset
            if (lowestBlockDy < -0.5) {
                minYOffset = -0.5 - lowestBlockDy;
            }
        }
        
        // --- BLOCK RENDERING (exactly like constructor) ---
        if (!blockEntries.isEmpty() && world.isClient()) {
            // Save OpenGL state (exactly like constructor)
            boolean wasBlendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
            boolean wasDepthTestEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            boolean wasDepthMaskEnabled = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            boolean wasCullFaceEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            // Enable glowing effect (exactly like constructor)
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_CULL_FACE);
            
            // For each block entry (exactly like constructor's loop)
            for (java.util.Map.Entry<BlockPos, BlockState> entry : blockEntries) {
                BlockPos worldPos = entry.getKey();
                BlockState blockState = entry.getValue();
                
                // Get the item form of the block
                var blockItem = blockState.getBlock().asItem();
                if (blockItem == null || blockItem == net.minecraft.item.Items.AIR) {
                    continue;
                }
                
                ItemStack stack = new ItemStack(blockItem);
                if (stack.isEmpty()) {
                    continue;
                }
                
                matrices.push();
                // Calculate position relative to chunk center (for proper centering)
                double dx = worldPos.getX() - chunkCenterX;
                double dz = worldPos.getZ() - chunkCenterZ;
                
                // Scale down the world coordinates to fit in hologram space
                // 16 blocks = 1 block in hologram (chunk is 16x16)
                float worldToHologramScale = 1.0f / 16.0f;
                dx = dx * worldToHologramScale;
                // Scale Y the same as X/Z to maintain proportions (can be as tall as needed)
                // Use fixed reference Y (sea level) so chained holograms align properly
                double dy = (worldPos.getY() - referenceY) * worldToHologramScale;
                dz = dz * worldToHologramScale;
                
                // Translate to center of table (0.5, 0.5, 0.5) plus hologram height
                // Add hologramHeight (0.5) so lowest block starts half a block above table surface
                // Add minYOffset to ensure nothing clips below table
                matrices.translate(0.5 + dx, hologramHeight + 0.5 + dy + minYOffset, 0.5 + dz);
                // Scale down the block (0.98 size to prevent overlap)
                matrices.scale(blockScale, blockScale, blockScale);
                
                // Render exactly like constructor (using ItemRenderer)
                itemRenderer.renderItem(
                    stack,
                    net.minecraft.item.ItemDisplayContext.GROUND,
                    15728880, // full-bright
                    OverlayTexture.DEFAULT_UV,
                    matrices,
                    vertexConsumers,
                    world,
                    0
                );
                
                matrices.pop();
            }
            
            // Restore OpenGL state (exactly like constructor)
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
        
        matrices.pop();
    }

    @Override
    public boolean rendersOutsideBoundingBox(HologramTableBlockEntity entity) {
        return true;
    }

    @Override
    public int getRenderDistance() {
        return 96;
    }
}

