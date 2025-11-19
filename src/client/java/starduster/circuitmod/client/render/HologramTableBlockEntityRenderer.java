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
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import starduster.circuitmod.block.ModBlocks;
import starduster.circuitmod.block.entity.HologramTableBlockEntity;

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

        // Determine render bounds from block entity state
        int minX = entity.getAreaMinX();
        int maxX = entity.getAreaMaxX();
        int minZ = entity.getAreaMinZ();
        int maxZ = entity.getAreaMaxZ();
        
        // Calculate center of render area for centering the hologram
        double chunkCenterX = (minX + maxX + 1) / 2.0;
        double chunkCenterZ = (minZ + maxZ + 1) / 2.0;
        
        matrices.push();
        
        MinecraftClient client = MinecraftClient.getInstance();
        ItemRenderer itemRenderer = client.getItemRenderer();
        var world = entity.getWorld();
        
        float worldToHologramScale = 1.0f / 16.0f;
        float hologramHeight = 1.0f; // Start half a block above table surface
        float blockScale = 1.0f / 4.0f;
        
        // Get entity render dispatcher for rendering entities (like players)
        var entityRenderDispatcher = client.getEntityRenderDispatcher();
        
        // Helper method to check if block should be filtered
        // Filter ground litter like leaves, grass, flowers, etc.
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
            // Filter flowers and other small plants
            if (block == Blocks.DANDELION || block == Blocks.POPPY || 
                block == Blocks.BLUE_ORCHID || block == Blocks.ALLIUM ||
                block == Blocks.AZURE_BLUET || block == Blocks.RED_TULIP ||
                block == Blocks.ORANGE_TULIP || block == Blocks.WHITE_TULIP ||
                block == Blocks.PINK_TULIP || block == Blocks.OXEYE_DAISY ||
                block == Blocks.CORNFLOWER || block == Blocks.LILY_OF_THE_VALLEY ||
                block == Blocks.WITHER_ROSE || block == Blocks.SUNFLOWER ||
                block == Blocks.LILAC || block == Blocks.ROSE_BUSH || block == Blocks.PEONY) {
                return true;
            }
            // Filter dead bushes and saplings
            if (block == Blocks.DEAD_BUSH || block.toString().contains("sapling")) {
                return true;
            }
            // Filter mushrooms
            if (block == Blocks.RED_MUSHROOM || block == Blocks.BROWN_MUSHROOM) {
                return true;
            }
            // Filter azalea (decorative ground plant)
            if (block == Blocks.AZALEA || block == Blocks.FLOWERING_AZALEA) {
                return true;
            }
            // Filter sweet berry bushes
            if (block == Blocks.SWEET_BERRY_BUSH) {
                return true;
            }
            // Filter leaf litter blocks (the actual LeafLitterBlock class)
            if (block instanceof net.minecraft.block.LeafLitterBlock) {
                return true;
            }
            // Note: Leaf BLOCKS (tree leaves) are kept - they're important for visualizing trees
            // LeafLitterBlock is the ground clutter, not tree canopy
            return false;
        };
        
        // Determine vertical scan bounds
        int scanStartY = Math.max(entity.getMinYLevel(), world.getBottomY());
        int scanEndY = 384; // cover entire chunk height so tall trees aren't clipped
        
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
        
        
        double minYOffset = 0.0;
        if (lowestYInHologram != Integer.MAX_VALUE && lowestYInHologram < referenceY) {
            // Blocks below sea level - ensure they don't clip into table
            // The lowest block should be at least at hologramHeight above table surface
            double lowestBlockDy = (lowestYInHologram - referenceY) * worldToHologramScale;
            
            if (lowestBlockDy < -0.5) {
                minYOffset = -0.5 - lowestBlockDy;
            }
        }
        
        // --- BLOCK RENDERING ---
        if (!blockEntries.isEmpty() && world.isClient()) {
            // For each block entry
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
                
                double dx = worldPos.getX() - chunkCenterX;
                double dz = worldPos.getZ() - chunkCenterZ;
                
               
                dx = dx * worldToHologramScale;
                
                double dy = (worldPos.getY() - referenceY) * worldToHologramScale;
                dz = dz * worldToHologramScale;
                
               
                matrices.translate(0.5 + dx, hologramHeight + 0.5 + dy + minYOffset, 0.5 + dz);
                
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
        }
        
        // --- ENTITY RENDERING ---
        // Render nearby entities (players and mobs) as mini entities on the hologram
        if (world.isClient() && client.player != null) {
            // Create a bounding box for the render area
            Box renderBox = new Box(minX, referenceY - 10, minZ, maxX + 1, referenceY + 100, maxZ + 1);
            
            // Get all entities in the render area
            var entities = world.getOtherEntities(null, renderBox);
            
            for (Entity livingEntity : entities) {
                // Skip invisible entities
                if (livingEntity == null || livingEntity.isInvisible()) {
                    continue;
                }
                
                // Get interpolated position for smooth movement
                Vec3d lerpedPos = livingEntity.getLerpedPos(tickDelta);
                
                // Check if entity is within the hologram rendering area using actual coordinates
                if (lerpedPos.x < minX || lerpedPos.x > (maxX + 1) ||
                    lerpedPos.z < minZ || lerpedPos.z > (maxZ + 1)) {
                    continue;
                }
                
                matrices.push();
                
                // Calculate entity's position relative to the center of the rendering area
                double entityOffsetX = lerpedPos.x - chunkCenterX;
                double entityOffsetY = lerpedPos.y - referenceY;
                double entityOffsetZ = lerpedPos.z - chunkCenterZ;
                
                // Debug logging every ~1 second (using world time % 20 to log once per second)
                if (world.getTime() % 20 == 0) {
                    System.out.println(String.format("[HOLOGRAM-DEBUG] Entity: %s (Type: %s)", 
                        livingEntity.getName().getString(), 
                        livingEntity.getType().toString()));
                    System.out.println(String.format("  Actual Position: X=%.2f Y=%.2f Z=%.2f Yaw=%.2f", 
                        lerpedPos.x, lerpedPos.y, lerpedPos.z, livingEntity.getLerpedYaw(tickDelta)));
                    System.out.println(String.format("  Hologram Center: X=%.2f Y=%d Z=%.2f", 
                        chunkCenterX, referenceY, chunkCenterZ));
                    System.out.println(String.format("  Offsets: X=%.2f Y=%.2f Z=%.2f", 
                        entityOffsetX, entityOffsetY, entityOffsetZ));
                    System.out.println(String.format("  Scaled Offsets: X=%.4f Y=%.4f Z=%.4f", 
                        entityOffsetX * worldToHologramScale, 
                        entityOffsetY * worldToHologramScale, 
                        entityOffsetZ * worldToHologramScale));
                }
                    
                    // Translate to center of hologram block
                    matrices.translate(0.5, hologramHeight + 0.5 + minYOffset, 0.5);
                    
                    // Add entity's scaled position offset from center
                    // Subtract worldToHologramScale from Y to fix the height offset
                    matrices.translate(
                        entityOffsetX * worldToHologramScale,
                        (entityOffsetY * worldToHologramScale) - worldToHologramScale,
                        entityOffsetZ * worldToHologramScale
                    );
                    
                    // Scale down the entity model
                    matrices.scale(worldToHologramScale, worldToHologramScale, worldToHologramScale);
                    
                    // Rotate to match entity's facing direction (interpolated for smooth rotation)
                    float lerpedYaw = livingEntity.getLerpedYaw(tickDelta);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - lerpedYaw));
                    
                    // Get block entity position for the render offset calculation
                    BlockPos bePos = entity.getPos();
                    
                    // Render the entity - offset by difference between block and entity position
                    // This tricks the renderer into placing the entity at our transformed position
                    entityRenderDispatcher.render(livingEntity, 
                        bePos.getX() + 0.5 + (entityOffsetX * worldToHologramScale) - lerpedPos.x, 
                        bePos.getY() + hologramHeight + 0.5 + minYOffset + (entityOffsetY * worldToHologramScale) - worldToHologramScale - lerpedPos.y, 
                        bePos.getZ() + 0.5 + (entityOffsetZ * worldToHologramScale) - lerpedPos.z, 
                        tickDelta, matrices, vertexConsumers, light);
                    
                    matrices.pop();
            }
        }
        
        matrices.pop();
    }

    /**
     * Render a small red dot/cube to mark a player's position
     */
    

    @Override
    public boolean rendersOutsideBoundingBox(HologramTableBlockEntity entity) {
        return true;
    }

    @Override
    public int getRenderDistance() {
        return 96;
    }
}

