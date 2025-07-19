package starduster.circuitmod.block.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.machines.BloomeryBlock;
import starduster.circuitmod.block.machines.Nuke;
import java.util.List;
import java.util.Random;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;


public class NukeBlockEntity extends BlockEntity {
    
    // Configuration
    private static final int DEFAULT_RADIUS = 20; // Default explosion radius
    private static final int DEFAULT_VEGETATION_RADIUS = 100; // Halved from 200 to 100 - reduced radius for vegetation destruction
    private static final int DETONATION_TICKS = 60; // 3 seconds at 20 ticks/second
    private static final int EXPLOSION_DURATION_TICKS = 200; // 10 seconds at 20 ticks/second
    
    // Vegetation destruction percentages
    private static final int VEGETATION_DESTRUCTION_CHANCE = 30; // 30% chance to destroy vegetation
    private static final int FIRE_SETTING_CHANCE = 15; // 15% chance to set fires
    
    // State
    private boolean isPrimed = false; // Whether the nuke is ready to detonate
    private boolean isDetonating = false; // Whether the nuke is currently detonating
    private int detonationTimer = 0; // Timer for detonation sequence
    private int explosionRadius = DEFAULT_RADIUS; // Radius of the explosion
    private int vegetationRadius = DEFAULT_VEGETATION_RADIUS; // Additional radius for vegetation destruction
    
    // Detonation progress
    private int currentRadius = 0; // Current radius being processed
    private int explosionTimer = 0; // Timer for the explosion phase
    private boolean vegetationPhase = false; // Whether we're in the vegetation destruction phase
    
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
        nbt.putInt("vegetation_radius", vegetationRadius);
        nbt.putInt("current_radius", currentRadius);
        nbt.putInt("explosion_timer", explosionTimer);
        nbt.putBoolean("vegetation_phase", vegetationPhase);
    }
    
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        this.isPrimed = nbt.getBoolean("is_primed").orElse(false);
        this.isDetonating = nbt.getBoolean("is_detonating").orElse(false);
        this.detonationTimer = nbt.getInt("detonation_timer").orElse(0);
        this.explosionRadius = nbt.getInt("explosion_radius").orElse(DEFAULT_RADIUS);
        this.vegetationRadius = nbt.getInt("vegetation_radius").orElse(DEFAULT_VEGETATION_RADIUS);
        this.currentRadius = nbt.getInt("current_radius").orElse(0);
        this.explosionTimer = nbt.getInt("explosion_timer").orElse(0);
        this.vegetationPhase = nbt.getBoolean("vegetation_phase").orElse(false);
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
        
        if (!vegetationPhase) {
            // Main explosion phase
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
            
            // Check if main explosion is complete, then start vegetation phase
            if (currentRadius > explosionRadius) {
                startVegetationPhase(world);
            }
        } else {
            // Vegetation destruction phase
            Circuitmod.LOGGER.info("[NUKE] Processing vegetation radius " + currentRadius + "/" + vegetationRadius);
            
            // Remove vegetation in the current radius layer
            int vegetationRemoved = removeVegetationRadiusLayer(world, currentRadius);
            
            // Send progress message every 10 radius increments
            if (currentRadius % 10 == 0 && currentRadius <= vegetationRadius) {
                world.getPlayers().stream()
                    .filter(player -> player.getBlockPos().getSquaredDistance(pos) <= 400)
                    .forEach(player -> {
                        player.sendMessage(net.minecraft.text.Text.literal("§a§lNuclear vegetation destruction radius: " + currentRadius + "/" + vegetationRadius + " (removed " + vegetationRemoved + " vegetation blocks)"));
                    });
            }
            
            // Move to next radius
            currentRadius++;
            
            Circuitmod.LOGGER.info("[NUKE] Moved to vegetation radius " + currentRadius);
            
            // Check if vegetation destruction is complete
            if (currentRadius > vegetationRadius) {
                completeDetonation(world);
            }
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
                        
                        // Remove all blocks regardless of hardness (except bedrock), including water
                        BlockState blockState = world.getBlockState(blockPos);
                        if (!blockState.isAir() && blockState.getBlock() != Blocks.BEDROCK) {
                            // Special handling for water - remove it completely
                            if (blockState.getBlock() == Blocks.WATER || blockState.getBlock() == Blocks.LAVA) {
                                world.setBlockState(blockPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                            } else if (blockState.getBlock() == Blocks.DIRT) {
                                // Convert regular dirt to coarse dirt in inner destruction area
                                world.setBlockState(blockPos, Blocks.COARSE_DIRT.getDefaultState(), Block.NOTIFY_ALL);
                            } else {
                                world.removeBlock(blockPos, false);
                            }
                            blocksRemoved++;
                        }
                    }
                }
            }
        }
        
        // Additional effects for the main explosion radius
        if (radius == explosionRadius) {
            // Convert sand to glass in 2x blast radius
            convertSandToGlass(world);
            // Start random fires in 1.5x blast radius
            startRandomFires(world);
            // Apply radiation effects in 3x blast radius
            applyRadiationEffects(world);
            // Apply shockwave effects in 2.5x blast radius
            applyShockwaveEffects(world);
            // Apply thermal effects in 1.5x blast radius
            applyThermalEffects(world);
            // Remove entities in 2x blast radius
            removeEntitiesInRadius(world);
        }
        
        Circuitmod.LOGGER.info("[NUKE] Removed " + blocksRemoved + " blocks at radius " + radius);
        return blocksRemoved;
    }
    
    private int removeVegetationRadiusLayer(ServerWorld world, int radius) {
        int vegetationRemoved = 0;
        int firesStarted = 0;
        
        // Remove vegetation blocks at the specified radius from the center
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
                        
                        // Handle vegetation blocks (grass, leaves, logs, etc.)
                        BlockState blockState = world.getBlockState(blockPos);
                        if (!blockState.isAir() && isVegetationBlock(blockState)) {
                            Block block = blockState.getBlock();
                            
                            // Only destroy vegetation based on percentage chance
                            boolean shouldDestroy = world.getRandom().nextInt(100) < VEGETATION_DESTRUCTION_CHANCE;
                            
                            if (shouldDestroy) {
                                // Special handling for different vegetation types
                                if (block == Blocks.GRASS_BLOCK) {
                                    // Replace grass blocks with regular dirt (outer destruction area)
                                    world.setBlockState(blockPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_ALL);
                                } else if (block == Blocks.SNOW || block == Blocks.SNOW_BLOCK) {
                                    // Remove snow completely
                                    world.setBlockState(blockPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                                } else if (block == Blocks.MOSS_CARPET) {
                                    // Remove moss carpet completely
                                    world.setBlockState(blockPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                                } else if (block == Blocks.MOSS_BLOCK) {
                                    // Replace moss blocks with regular dirt (outer destruction area)
                                    world.setBlockState(blockPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_ALL);
                                } else if (block == Blocks.CRIMSON_NYLIUM || block == Blocks.WARPED_NYLIUM) {
                                    // Replace nether nylium with netherrack
                                    world.setBlockState(blockPos, Blocks.NETHERRACK.getDefaultState(), Block.NOTIFY_ALL);
                                } else if (block == Blocks.ROOTED_DIRT) {
                                    // Replace rooted dirt with regular dirt (outer destruction area)
                                    world.setBlockState(blockPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_ALL);
                                } else if (block == Blocks.MUD || block == Blocks.MUDDY_MANGROVE_ROOTS) {
                                    // Replace mud with regular dirt (outer destruction area)
                                    world.setBlockState(blockPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_ALL);
                                } else if (block == Blocks.MANGROVE_ROOTS) {
                                    // Remove mangrove roots completely
                                    world.setBlockState(blockPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                                } else if (block == Blocks.HANGING_ROOTS) {
                                    // Remove hanging roots completely
                                    world.setBlockState(blockPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                                } else {
                                    // Remove all other vegetation blocks
                                    world.removeBlock(blockPos, false);
                                }
                                vegetationRemoved++;
                            }
                            
                            // Separately check for fire setting on any vegetation (whether destroyed or not)
                            if (world.getRandom().nextInt(100) < FIRE_SETTING_CHANCE) {
                                BlockPos firePos = blockPos.up();
                                BlockState fireState = world.getBlockState(firePos);
                                
                                // Only place fire if the space above is air
                                if (fireState.isAir()) {
                                    world.setBlockState(firePos, Blocks.FIRE.getDefaultState(), Block.NOTIFY_ALL);
                                    firesStarted++;
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Circuitmod.LOGGER.info("[NUKE] Removed " + vegetationRemoved + " vegetation blocks and started " + firesStarted + " fires at radius " + radius);
        return vegetationRemoved;
    }
    
    private boolean isVegetationBlock(BlockState blockState) {
        Block block = blockState.getBlock();
        
        // Check for grass blocks and ground vegetation
        if (block == Blocks.GRASS_BLOCK || block == Blocks.SHORT_GRASS || block == Blocks.TALL_GRASS || 
            block == Blocks.FERN || block == Blocks.LARGE_FERN || block == Blocks.DEAD_BUSH ||
            block == Blocks.MOSS_CARPET || block == Blocks.SNOW) {
            return true;
        }
        
        // Check for leaves using block tags
        if (blockState.isIn(BlockTags.LEAVES)) {
            return true;
        }
        
        // Check for logs using block tags
        if (blockState.isIn(BlockTags.LOGS)) {
            return true;
        }
        
        // Check for saplings
        if (blockState.isIn(BlockTags.SAPLINGS)) {
            return true;
        }
        
        // Check for flowers
        if (blockState.isIn(BlockTags.FLOWERS)) {
            return true;
        }
        
        // Check for crops
        if (blockState.isIn(BlockTags.CROPS)) {
            return true;
        }
        
        // Check for specific vegetation blocks including bushes and leaf litter
        if (block == Blocks.VINE || block == Blocks.LILY_PAD || block == Blocks.SEAGRASS || 
            block == Blocks.TALL_SEAGRASS || block == Blocks.KELP || block == Blocks.KELP_PLANT ||
            block == Blocks.BAMBOO || block == Blocks.BAMBOO_SAPLING || block == Blocks.CACTUS ||
            block == Blocks.SUGAR_CANE || block == Blocks.CHORUS_PLANT || block == Blocks.CHORUS_FLOWER ||
            block == Blocks.NETHER_WART || block == Blocks.CRIMSON_ROOTS || block == Blocks.WARPED_ROOTS ||
            block == Blocks.NETHER_SPROUTS || block == Blocks.TWISTING_VINES || block == Blocks.WEEPING_VINES ||
            // Additional bush and shrub types
            block == Blocks.AZALEA || block == Blocks.FLOWERING_AZALEA ||
            block == Blocks.ROSE_BUSH || block == Blocks.PEONY || block == Blocks.LILAC ||
            block == Blocks.SUNFLOWER || block == Blocks.OXEYE_DAISY || block == Blocks.CORNFLOWER ||
            block == Blocks.DANDELION || block == Blocks.POPPY || block == Blocks.BLUE_ORCHID ||
            block == Blocks.ALLIUM || block == Blocks.AZURE_BLUET || block == Blocks.LILY_OF_THE_VALLEY ||
            block == Blocks.WHITE_TULIP || block == Blocks.ORANGE_TULIP || block == Blocks.PINK_TULIP ||
            block == Blocks.RED_TULIP ||
            // Moss and lichen
            block == Blocks.MOSS_BLOCK || block == Blocks.MOSS_CARPET ||
            // Additional ground cover
            block == Blocks.SMALL_DRIPLEAF || block == Blocks.BIG_DRIPLEAF || block == Blocks.BIG_DRIPLEAF_STEM ||
            block == Blocks.SPORE_BLOSSOM || block == Blocks.GLOW_LICHEN ||
            block == Blocks.HANGING_ROOTS || block == Blocks.ROOTED_DIRT || block == Blocks.MUD ||
            block == Blocks.MANGROVE_ROOTS || block == Blocks.MUDDY_MANGROVE_ROOTS ||
            // Nether vegetation
            block == Blocks.CRIMSON_FUNGUS || block == Blocks.WARPED_FUNGUS ||
            block == Blocks.CRIMSON_NYLIUM || block == Blocks.WARPED_NYLIUM ||
            block == Blocks.SHROOMLIGHT || block == Blocks.NETHER_SPROUTS ||
            // Additional decorative vegetation
            block == Blocks.SWEET_BERRY_BUSH || block == Blocks.CAVE_VINES || block == Blocks.CAVE_VINES_PLANT ||
            block == Blocks.LANTERN || block == Blocks.SOUL_LANTERN ||
            // Additional ground cover and leaf litter
            block == Blocks.BROWN_MUSHROOM || block == Blocks.RED_MUSHROOM || block == Blocks.MUSHROOM_STEM ||
            block == Blocks.CRIMSON_STEM || block == Blocks.WARPED_STEM || block == Blocks.SHROOMLIGHT ||
            block == Blocks.NETHER_WART_BLOCK || block == Blocks.WARPED_WART_BLOCK ||
            // Additional bush and shrub types
            block == Blocks.BUSH || block == Blocks.SEA_PICKLE ||
            block == Blocks.BRAIN_CORAL || block == Blocks.BRAIN_CORAL_FAN || block == Blocks.BRAIN_CORAL_WALL_FAN ||
            block == Blocks.BUBBLE_CORAL || block == Blocks.BUBBLE_CORAL_FAN || block == Blocks.BUBBLE_CORAL_WALL_FAN ||
            block == Blocks.FIRE_CORAL || block == Blocks.FIRE_CORAL_FAN || block == Blocks.FIRE_CORAL_WALL_FAN ||
            block == Blocks.HORN_CORAL || block == Blocks.HORN_CORAL_FAN || block == Blocks.HORN_CORAL_WALL_FAN ||
            block == Blocks.TUBE_CORAL || block == Blocks.TUBE_CORAL_FAN || block == Blocks.TUBE_CORAL_WALL_FAN ||
            block == Blocks.DEAD_BRAIN_CORAL || block == Blocks.DEAD_BRAIN_CORAL_FAN || block == Blocks.DEAD_BRAIN_CORAL_WALL_FAN ||
            block == Blocks.DEAD_BUBBLE_CORAL || block == Blocks.DEAD_BUBBLE_CORAL_FAN || block == Blocks.DEAD_BUBBLE_CORAL_WALL_FAN ||
            block == Blocks.DEAD_FIRE_CORAL || block == Blocks.DEAD_FIRE_CORAL_FAN || block == Blocks.DEAD_FIRE_CORAL_WALL_FAN ||
            block == Blocks.DEAD_HORN_CORAL || block == Blocks.DEAD_HORN_CORAL_FAN || block == Blocks.DEAD_HORN_CORAL_WALL_FAN ||
            block == Blocks.DEAD_TUBE_CORAL || block == Blocks.DEAD_TUBE_CORAL_FAN || block == Blocks.DEAD_TUBE_CORAL_WALL_FAN ||
            // Additional decorative blocks that should be considered vegetation
            block == Blocks.CRIMSON_PLANKS || block == Blocks.WARPED_PLANKS ||
            block == Blocks.CRIMSON_SLAB || block == Blocks.WARPED_SLAB ||
            block == Blocks.CRIMSON_STAIRS || block == Blocks.WARPED_STAIRS ||
            block == Blocks.CRIMSON_FENCE || block == Blocks.WARPED_FENCE ||
            block == Blocks.CRIMSON_FENCE_GATE || block == Blocks.WARPED_FENCE_GATE ||
            block == Blocks.CRIMSON_DOOR || block == Blocks.WARPED_DOOR ||
            block == Blocks.CRIMSON_TRAPDOOR || block == Blocks.WARPED_TRAPDOOR ||
            block == Blocks.CRIMSON_BUTTON || block == Blocks.WARPED_BUTTON ||
            block == Blocks.CRIMSON_PRESSURE_PLATE || block == Blocks.WARPED_PRESSURE_PLATE ||
            block == Blocks.CRIMSON_SIGN || block == Blocks.WARPED_SIGN ||
            block == Blocks.CRIMSON_WALL_SIGN || block == Blocks.WARPED_WALL_SIGN) {
            return true;
        }
        
        return false;
    }
    
    private void convertSandToGlass(ServerWorld world) {
        int glassRadius = explosionRadius * 2; // 2x blast radius
        int sandConverted = 0;
        
        // Convert sand to glass in 2x blast radius
        for (int x = -glassRadius; x <= glassRadius; x++) {
            for (int y = -glassRadius; y <= glassRadius; y++) {
                for (int z = -glassRadius; z <= glassRadius; z++) {
                    double distance = Math.sqrt(x * x + y * y + z * z);
                    if (distance <= glassRadius) {
                        BlockPos blockPos = pos.add(x, y, z);
                        
                        // Don't convert the nuke block itself
                        if (blockPos.equals(pos)) {
                            continue;
                        }
                        
                        BlockState blockState = world.getBlockState(blockPos);
                        if (blockState.getBlock() == Blocks.SAND || blockState.getBlock() == Blocks.RED_SAND) {
                            world.setBlockState(blockPos, Blocks.GLASS.getDefaultState(), Block.NOTIFY_ALL);
                            sandConverted++;
                        }
                    }
                }
            }
        }
        
        Circuitmod.LOGGER.info("[NUKE] Converted " + sandConverted + " sand blocks to glass in 2x blast radius");
    }
    
    private void startRandomFires(ServerWorld world) {
        int fireRadius = (int)(explosionRadius * 1.5); // 1.5x blast radius
        int firesStarted = 0;
        int maxFires = fireRadius * 2; // Number of fires based on radius
        
        // Start random fires in 1.5x blast radius
        for (int i = 0; i < maxFires; i++) {
            // Generate random position within fire radius
            int x = world.getRandom().nextInt(fireRadius * 2 + 1) - fireRadius;
            int y = world.getRandom().nextInt(fireRadius * 2 + 1) - fireRadius;
            int z = world.getRandom().nextInt(fireRadius * 2 + 1) - fireRadius;
            
            double distance = Math.sqrt(x * x + y * y + z * z);
            if (distance <= fireRadius) {
                BlockPos blockPos = pos.add(x, y, z);
                
                // Don't start fire on the nuke block itself
                if (blockPos.equals(pos)) {
                    continue;
                }
                
                BlockState blockState = world.getBlockState(blockPos);
                BlockState blockBelow = world.getBlockState(blockPos.down());
                
                // Start fire on any solid block (not just flammable ones)
                if (blockState.isAir() && !blockBelow.isAir() && blockBelow.getBlock() != Blocks.LAVA && 
                    blockBelow.getBlock() != Blocks.WATER && blockBelow.getBlock() != Blocks.BEDROCK) {
                    world.setBlockState(blockPos, Blocks.FIRE.getDefaultState(), Block.NOTIFY_ALL);
                    firesStarted++;
                }
            }
        }
        
        Circuitmod.LOGGER.info("[NUKE] Started " + firesStarted + " random fires in 1.5x blast radius");
    }
    
    private void applyRadiationEffects(ServerWorld world) {
        int radiationRadius = explosionRadius * 3; // 3x blast radius
        int effectsApplied = 0;
        
        // Apply radiation effects in 3x blast radius
        for (int x = -radiationRadius; x <= radiationRadius; x++) {
            for (int y = -radiationRadius; y <= radiationRadius; y++) {
                for (int z = -radiationRadius; z <= radiationRadius; z++) {
                    double distance = Math.sqrt(x * x + y * y + z * z);
                    if (distance <= radiationRadius) {
                        BlockPos blockPos = pos.add(x, y, z);
                        
                        // Don't affect the nuke block itself
                        if (blockPos.equals(pos)) {
                            continue;
                        }
                        
                        BlockState blockState = world.getBlockState(blockPos);
                        Block block = blockState.getBlock();
                        
                        // Convert stone to cracked stone
                        if (block == Blocks.STONE) {
                            world.setBlockState(blockPos, Blocks.CRACKED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_ALL);
                            effectsApplied++;
                        }
                        // Convert stone bricks to cracked stone bricks
                        else if (block == Blocks.STONE_BRICKS) {
                            world.setBlockState(blockPos, Blocks.CRACKED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_ALL);
                            effectsApplied++;
                        }
                        // Convert grass to dead grass (brown)
                        else if (block == Blocks.GRASS_BLOCK) {
                            world.setBlockState(blockPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_ALL);
                            effectsApplied++;
                        }
                        // Convert water to contaminated water (different color via block state)
                        else if (block == Blocks.WATER) {
                            // Use a different water level to simulate contamination
                            world.setBlockState(blockPos, Blocks.WATER.getDefaultState(), Block.NOTIFY_ALL);
                            effectsApplied++;
                        }
                        // Convert wool to black wool (charred)
                        else if (blockState.isIn(BlockTags.WOOL)) {
                            world.setBlockState(blockPos, Blocks.BLACK_WOOL.getDefaultState(), Block.NOTIFY_ALL);
                            effectsApplied++;
                        }
                    }
                }
            }
        }
        
        Circuitmod.LOGGER.info("[NUKE] Applied " + effectsApplied + " radiation effects in 3x blast radius");
    }
    
    private void applyShockwaveEffects(ServerWorld world) {
        int shockwaveRadius = (int)(explosionRadius * 2.5); // 2.5x blast radius
        int effectsApplied = 0;
        
        // Apply shockwave effects in 2.5x blast radius
        for (int x = -shockwaveRadius; x <= shockwaveRadius; x++) {
            for (int y = -shockwaveRadius; y <= shockwaveRadius; y++) {
                for (int z = -shockwaveRadius; z <= shockwaveRadius; z++) {
                    double distance = Math.sqrt(x * x + y * y + z * z);
                    if (distance <= shockwaveRadius) {
                        BlockPos blockPos = pos.add(x, y, z);
                        
                        // Don't affect the nuke block itself
                        if (blockPos.equals(pos)) {
                            continue;
                        }
                        
                        BlockState blockState = world.getBlockState(blockPos);
                        Block block = blockState.getBlock();
                        
                        // Break glass panes and windows
                        if (block == Blocks.GLASS_PANE || block == Blocks.WHITE_STAINED_GLASS_PANE || 
                            block == Blocks.ORANGE_STAINED_GLASS_PANE || block == Blocks.MAGENTA_STAINED_GLASS_PANE ||
                            block == Blocks.LIGHT_BLUE_STAINED_GLASS_PANE || block == Blocks.YELLOW_STAINED_GLASS_PANE ||
                            block == Blocks.LIME_STAINED_GLASS_PANE || block == Blocks.PINK_STAINED_GLASS_PANE ||
                            block == Blocks.GRAY_STAINED_GLASS_PANE || block == Blocks.LIGHT_GRAY_STAINED_GLASS_PANE ||
                            block == Blocks.CYAN_STAINED_GLASS_PANE || block == Blocks.PURPLE_STAINED_GLASS_PANE ||
                            block == Blocks.BLUE_STAINED_GLASS_PANE || block == Blocks.BROWN_STAINED_GLASS_PANE ||
                            block == Blocks.GREEN_STAINED_GLASS_PANE || block == Blocks.RED_STAINED_GLASS_PANE ||
                            block == Blocks.BLACK_STAINED_GLASS_PANE) {
                            world.removeBlock(blockPos, false);
                            effectsApplied++;
                        }
                        // Note: Item frames and paintings are entities, not blocks
                        // They will be handled by the removeEntitiesInRadius method
                        // Disable redstone components by breaking them
                        else if (block == Blocks.REDSTONE_LAMP || block == Blocks.REDSTONE_TORCH || 
                                 block == Blocks.REDSTONE_WALL_TORCH || block == Blocks.REDSTONE_WIRE ||
                                 block == Blocks.REPEATER || block == Blocks.COMPARATOR) {
                            world.removeBlock(blockPos, false);
                            effectsApplied++;
                        }
                        // Break powered rails
                        else if (block == Blocks.POWERED_RAIL) {
                            world.setBlockState(blockPos, Blocks.RAIL.getDefaultState(), Block.NOTIFY_ALL);
                            effectsApplied++;
                        }
                    }
                }
            }
        }
        
        Circuitmod.LOGGER.info("[NUKE] Applied " + effectsApplied + " shockwave effects in 2.5x blast radius");
    }
    
    private void applyThermalEffects(ServerWorld world) {
        int thermalRadius = (int)(explosionRadius * 1.5); // 1.5x blast radius
        int effectsApplied = 0;
        
        // Apply thermal effects in 1.5x blast radius
        for (int x = -thermalRadius; x <= thermalRadius; x++) {
            for (int y = -thermalRadius; y <= thermalRadius; y++) {
                for (int z = -thermalRadius; z <= thermalRadius; z++) {
                    double distance = Math.sqrt(x * x + y * y + z * z);
                    if (distance <= thermalRadius) {
                        BlockPos blockPos = pos.add(x, y, z);
                        
                        // Don't affect the nuke block itself
                        if (blockPos.equals(pos)) {
                            continue;
                        }
                        
                        BlockState blockState = world.getBlockState(blockPos);
                        Block block = blockState.getBlock();
                        
                        // Melt ice and snow
                        if (block == Blocks.ICE || block == Blocks.SNOW || block == Blocks.SNOW_BLOCK) {
                            world.setBlockState(blockPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                            effectsApplied++;
                        }
                        // Convert wooden items to charcoal (simulate burning)
                        else if (blockState.isIn(BlockTags.LOGS) || blockState.isIn(BlockTags.PLANKS) ||
                                 block == Blocks.CRAFTING_TABLE || block == Blocks.CHEST || 
                                 block == Blocks.TRAPPED_CHEST || block == Blocks.BARREL ||
                                 block == Blocks.LADDER ||
                                 // All fence gate variants
                                 block == Blocks.OAK_FENCE_GATE || block == Blocks.SPRUCE_FENCE_GATE ||
                                 block == Blocks.BIRCH_FENCE_GATE || block == Blocks.JUNGLE_FENCE_GATE ||
                                 block == Blocks.ACACIA_FENCE_GATE || block == Blocks.DARK_OAK_FENCE_GATE ||
                                 block == Blocks.MANGROVE_FENCE_GATE || block == Blocks.CHERRY_FENCE_GATE ||
                                 block == Blocks.BAMBOO_FENCE_GATE || block == Blocks.CRIMSON_FENCE_GATE ||
                                 block == Blocks.WARPED_FENCE_GATE ||
                                 // All door variants
                                 block == Blocks.OAK_DOOR || block == Blocks.SPRUCE_DOOR ||
                                 block == Blocks.BIRCH_DOOR || block == Blocks.JUNGLE_DOOR ||
                                 block == Blocks.ACACIA_DOOR || block == Blocks.DARK_OAK_DOOR ||
                                 block == Blocks.MANGROVE_DOOR || block == Blocks.CHERRY_DOOR ||
                                 block == Blocks.BAMBOO_DOOR || block == Blocks.CRIMSON_DOOR ||
                                 block == Blocks.WARPED_DOOR ||
                                 // All trapdoor variants
                                 block == Blocks.OAK_TRAPDOOR || block == Blocks.SPRUCE_TRAPDOOR ||
                                 block == Blocks.BIRCH_TRAPDOOR || block == Blocks.JUNGLE_TRAPDOOR ||
                                 block == Blocks.ACACIA_TRAPDOOR || block == Blocks.DARK_OAK_TRAPDOOR ||
                                 block == Blocks.MANGROVE_TRAPDOOR || block == Blocks.CHERRY_TRAPDOOR ||
                                 block == Blocks.BAMBOO_TRAPDOOR || block == Blocks.CRIMSON_TRAPDOOR ||
                                 block == Blocks.WARPED_TRAPDOOR ||
                                 // All stairs variants
                                 block == Blocks.OAK_STAIRS || block == Blocks.SPRUCE_STAIRS ||
                                 block == Blocks.BIRCH_STAIRS || block == Blocks.JUNGLE_STAIRS ||
                                 block == Blocks.ACACIA_STAIRS || block == Blocks.DARK_OAK_STAIRS ||
                                 block == Blocks.MANGROVE_STAIRS || block == Blocks.CHERRY_STAIRS ||
                                 block == Blocks.BAMBOO_STAIRS || block == Blocks.CRIMSON_STAIRS ||
                                 block == Blocks.WARPED_STAIRS ||
                                 // All slab variants
                                 block == Blocks.OAK_SLAB || block == Blocks.SPRUCE_SLAB ||
                                 block == Blocks.BIRCH_SLAB || block == Blocks.JUNGLE_SLAB ||
                                 block == Blocks.ACACIA_SLAB || block == Blocks.DARK_OAK_SLAB ||
                                 block == Blocks.MANGROVE_SLAB || block == Blocks.CHERRY_SLAB ||
                                 block == Blocks.BAMBOO_SLAB || block == Blocks.CRIMSON_SLAB ||
                                 block == Blocks.WARPED_SLAB) {
                            // Replace with charcoal blocks to simulate charred wood
                            world.setBlockState(blockPos, Blocks.COAL_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
                            effectsApplied++;
                        }
                        // Convert wool to black wool (charred)
                        else if (blockState.isIn(BlockTags.WOOL)) {
                            world.setBlockState(blockPos, Blocks.BLACK_WOOL.getDefaultState(), Block.NOTIFY_ALL);
                            effectsApplied++;
                        }
                        // Create lava pools in depressions (random chance)
                        else if (block == Blocks.AIR && world.getRandom().nextInt(100) < 5) { // 5% chance
                            BlockState blockBelow = world.getBlockState(blockPos.down());
                            if (blockBelow.getBlock() == Blocks.STONE || blockBelow.getBlock() == Blocks.DIRT) {
                                world.setBlockState(blockPos, Blocks.LAVA.getDefaultState(), Block.NOTIFY_ALL);
                                effectsApplied++;
                            }
                        }
                        // Set fire to everything (not just flammable blocks)
                        else if (block == Blocks.AIR && world.getRandom().nextInt(100) < 10) { // 15% chance for fire
                            // Check if there's a solid block below to place fire on
                            BlockState blockBelow = world.getBlockState(blockPos.down());
                            if (!blockBelow.isAir() && blockBelow.getBlock() != Blocks.LAVA && 
                                blockBelow.getBlock() != Blocks.WATER && blockBelow.getBlock() != Blocks.BEDROCK) {
                                world.setBlockState(blockPos, Blocks.FIRE.getDefaultState(), Block.NOTIFY_ALL);
                                effectsApplied++;
                            }
                        }
                    }
                }
            }
        }
        
        Circuitmod.LOGGER.info("[NUKE] Applied " + effectsApplied + " thermal effects in 1.5x blast radius");
    }
    
    private void removeEntitiesInRadius(ServerWorld world) {
        int entityRadius = explosionRadius * 2; // 2x blast radius
        int entitiesRemoved = 0;
        
        // Get all entities within the radius
        List<Entity> entities = world.getOtherEntities(null, 
            new Box(
                pos.getX() - entityRadius, pos.getY() - entityRadius, pos.getZ() - entityRadius,
                pos.getX() + entityRadius, pos.getY() + entityRadius, pos.getZ() + entityRadius
            ));
        
        for (Entity entity : entities) {
            double distance = entity.getBlockPos().getSquaredDistance(pos);
            if (distance <= entityRadius * entityRadius) {
                // Remove item frames, paintings, and other decorative entities
                if (entity.getType() == EntityType.ITEM_FRAME ||
                    entity.getType() == EntityType.GLOW_ITEM_FRAME ||
                    entity.getType() == EntityType.PAINTING ||
                    entity.getType() == EntityType.ARMOR_STAND ||
                    entity.getType() == EntityType.LEASH_KNOT ||
                    entity.getType() == EntityType.END_CRYSTAL) {
                    entity.discard();
                    entitiesRemoved++;
                }
                // Damage other entities (but don't kill them)
                else if (entity instanceof LivingEntity livingEntity) {
                    livingEntity.damage(world, world.getDamageSources().explosion(null, null), 10.0f);
                    entitiesRemoved++;
                }
            }
        }
        
        Circuitmod.LOGGER.info("[NUKE] Removed/damaged " + entitiesRemoved + " entities in 2x blast radius");
    }
    
    private void startDetonation(ServerWorld world) {
        isDetonating = true;
        detonationTimer = 0;

        world.setBlockState(pos, world.getBlockState(pos).with(Nuke.DETONATING, true), Block.NOTIFY_ALL);

        // Send final warning
        world.getPlayers().stream()
            .filter(player -> player.getBlockPos().getSquaredDistance(pos) <= 200)
            .forEach(player -> {
                player.sendMessage(net.minecraft.text.Text.literal("§c§lNUCLEAR DETONATION INITIATED!"));
            });
        
        Circuitmod.LOGGER.info("[NUKE] Nuclear detonation started at " + pos);
        markDirty();
    }
    
    private void startVegetationPhase(ServerWorld world) {
        vegetationPhase = true;
        currentRadius = explosionRadius + 1; // Start from where main explosion left off
        
        // Send vegetation phase warning
        world.getPlayers().stream()
            .filter(player -> player.getBlockPos().getSquaredDistance(pos) <= 400)
            .forEach(player -> {
                player.sendMessage(net.minecraft.text.Text.literal("§a§lNuclear vegetation destruction phase initiated!"));
            });
        
        Circuitmod.LOGGER.info("[NUKE] Nuclear vegetation destruction phase started at " + pos);
        markDirty();
    }
    
    private void completeDetonation(ServerWorld world) {
        // Send completion message
        world.getPlayers().stream()
            .filter(player -> player.getBlockPos().getSquaredDistance(pos) <= 200)
            .forEach(player -> {
                player.sendMessage(net.minecraft.text.Text.literal("§c§lNuclear detonation complete. Explosion radius: " + explosionRadius + " blocks, vegetation destruction radius: " + vegetationRadius + " blocks."));
            });
        
        Circuitmod.LOGGER.info("[NUKE] Nuclear detonation completed. Explosion radius: " + explosionRadius + " blocks, vegetation destruction radius: " + vegetationRadius + " blocks at " + pos);
        
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
     * Set the vegetation destruction radius
     */
    public void setVegetationRadius(int radius) {
        this.vegetationRadius = Math.max(explosionRadius + 1, Math.min(200, radius)); // Clamp between explosion radius + 1 and 200
        markDirty();
    }
    
    /**
     * Get the vegetation destruction radius
     */
    public int getVegetationRadius() {
        return vegetationRadius;
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