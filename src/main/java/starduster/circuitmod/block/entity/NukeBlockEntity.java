package starduster.circuitmod.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import starduster.circuitmod.Circuitmod;



public class NukeBlockEntity extends BlockEntity {
    
    // Configuration
    private static final int DEFAULT_RADIUS = 80; // Default explosion radius
    private static final int DETONATION_TICKS = 60; // 3 seconds at 20 ticks/second
    private static final int EXPLOSION_DURATION_TICKS = 200; // 10 seconds at 20 ticks/second
    
    // State
    private boolean isPrimed = false; // Whether the nuke is ready to detonate
    private boolean isDetonating = false; // Whether the nuke is currently detonating
    private int detonationTimer = 0; // Timer for detonation sequence
    private int explosionRadius = DEFAULT_RADIUS; // Radius of the explosion
    
    // Detonation progress
    private int currentRadius = 0; // Current radius being processed
    private int explosionTimer = 0; // Timer for the explosion phase
    
    public NukeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NUKE_BLOCK_ENTITY, pos, state);
    }
    
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putBoolean("is_primed", isPrimed);
        nbt.putBoolean("is_detonating", isDetonating);
        nbt.putInt("detonation_timer", detonationTimer);
        nbt.putInt("explosion_radius", explosionRadius);
        nbt.putInt("current_radius", currentRadius);
        nbt.putInt("explosion_timer", explosionTimer);
    }
    
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        this.isPrimed = nbt.getBoolean("is_primed").orElse(false);
        this.isDetonating = nbt.getBoolean("is_detonating").orElse(false);
        this.detonationTimer = nbt.getInt("detonation_timer").orElse(0);
        this.explosionRadius = nbt.getInt("explosion_radius").orElse(DEFAULT_RADIUS);
        this.currentRadius = nbt.getInt("current_radius").orElse(0);
        this.explosionTimer = nbt.getInt("explosion_timer").orElse(0);
    }
    
    public static void tick(World world, BlockPos pos, BlockState state, NukeBlockEntity blockEntity) {
        if (world.isClient()) {
            return;
        }
        
        // Handle detonation sequence
        if (blockEntity.isDetonating) {
            Circuitmod.LOGGER.info("[NUKE-TICK] Detonating at " + pos);
            blockEntity.handleDetonation((ServerWorld) world);
        }
        // Handle priming sequence
        else if (blockEntity.isPrimed) {
            Circuitmod.LOGGER.info("[NUKE-TICK] Primed at " + pos);
            blockEntity.handlePriming((ServerWorld) world);
        }
    }
    
    private void handlePriming(ServerWorld world) {
        detonationTimer++;
        
        // Send warning messages to nearby players
        if (detonationTimer % 20 == 0) { // Every second
            int secondsLeft = (DETONATION_TICKS - detonationTimer) / 20;
            if (secondsLeft > 0) {
                world.getPlayers().stream()
                    .filter(player -> player.getBlockPos().getSquaredDistance(pos) <= 100) // Within 10 blocks
                    .forEach(player -> {
                        player.sendMessage(net.minecraft.text.Text.literal("§c§lWARNING: Nuclear detonation in " + secondsLeft + " seconds!"));
                    });
            }
        }
        
        // Start detonation
        if (detonationTimer >= DETONATION_TICKS) {
            startDetonation(world);
        }
    }
    
    private void handleDetonation(ServerWorld world) {
        explosionTimer++;
        
        Circuitmod.LOGGER.info("[NUKE] Processing radius " + currentRadius + "/" + explosionRadius);
        
        // Remove all blocks in the current radius layer
        int blocksRemoved = removeRadiusLayer(world, currentRadius);
        
        // Send progress message every 10 radius increments
        if (currentRadius % 10 == 0 && currentRadius <= explosionRadius) {
            world.getPlayers().stream()
                .filter(player -> player.getBlockPos().getSquaredDistance(pos) <= 400)
                .forEach(player -> {
                    player.sendMessage(net.minecraft.text.Text.literal("§c§lNuclear explosion radius: " + currentRadius + "/" + explosionRadius + " (removed " + blocksRemoved + " blocks)"));
                });
        }
        
        // Move to next radius
        currentRadius++;
        
        Circuitmod.LOGGER.info("[NUKE] Moved to radius " + currentRadius);
        
        // Check if explosion is complete
        if (currentRadius > explosionRadius) {
            completeDetonation(world);
        }
    }
    
    private int removeRadiusLayer(ServerWorld world, int radius) {
        int blocksRemoved = 0;
        
        // Remove all blocks at the specified radius from the center
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    // Check if this position is exactly at the specified radius
                    double distance = Math.sqrt(x * x + y * y + z * z);
                    if (Math.abs(distance - radius) < 0.5) { // Within 0.5 blocks of the radius
                        BlockPos blockPos = pos.add(x, y, z);
                        
                        // Don't remove the nuke block itself during the explosion
                        if (blockPos.equals(pos)) {
                            continue;
                        }
                        
                        // Remove all blocks regardless of hardness (except bedrock)
                        BlockState blockState = world.getBlockState(blockPos);
                        if (!blockState.isAir() && blockState.getBlock() != Blocks.BEDROCK) {
                            world.removeBlock(blockPos, false);
                            blocksRemoved++;
                        }
                    }
                }
            }
        }
        
        Circuitmod.LOGGER.info("[NUKE] Removed " + blocksRemoved + " blocks at radius " + radius);
        return blocksRemoved;
    }
    
    private void startDetonation(ServerWorld world) {
        isDetonating = true;
        detonationTimer = 0;
        
        // Send final warning
        world.getPlayers().stream()
            .filter(player -> player.getBlockPos().getSquaredDistance(pos) <= 200)
            .forEach(player -> {
                player.sendMessage(net.minecraft.text.Text.literal("§c§lNUCLEAR DETONATION INITIATED!"));
            });
        
        Circuitmod.LOGGER.info("[NUKE] Nuclear detonation started at " + pos);
        markDirty();
    }
    
    private void completeDetonation(ServerWorld world) {
        // Send completion message
        world.getPlayers().stream()
            .filter(player -> player.getBlockPos().getSquaredDistance(pos) <= 200)
            .forEach(player -> {
                player.sendMessage(net.minecraft.text.Text.literal("§c§lNuclear detonation complete. Explosion radius: " + explosionRadius + " blocks."));
            });
        
        Circuitmod.LOGGER.info("[NUKE] Nuclear detonation completed. Explosion radius: " + explosionRadius + " blocks at " + pos);
        
        // Remove the nuke block itself at the very end
        world.removeBlock(pos, false);
    }
    
    /**
     * Prime the nuke for detonation
     */
    public void prime() {
        if (!isPrimed && !isDetonating) {
            isPrimed = true;
            detonationTimer = 0;
            Circuitmod.LOGGER.info("[NUKE] Nuke primed at " + pos);
            markDirty();
        }
    }
    
    /**
     * Set the explosion radius
     */
    public void setExplosionRadius(int radius) {
        this.explosionRadius = Math.max(1, Math.min(50, radius)); // Clamp between 1 and 50
        markDirty();
    }
    
    /**
     * Get the explosion radius
     */
    public int getExplosionRadius() {
        return explosionRadius;
    }
    
    /**
     * Check if the nuke is primed
     */
    public boolean isPrimed() {
        return isPrimed;
    }
    
    /**
     * Check if the nuke is detonating
     */
    public boolean isDetonating() {
        return isDetonating;
    }
    
    /**
     * Get the detonation timer
     */
    public int getDetonationTimer() {
        return detonationTimer;
    }
    
    /**
     * Get the total detonation time
     */
    public int getTotalDetonationTime() {
        return DETONATION_TICKS;
    }
} 