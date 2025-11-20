package starduster.circuitmod.satellite;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.entity.SatelliteControlBlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Mining satellite capable of resource extraction from orbit.
 */
public class MiningSatellite extends Satellite {
    private static final int BASE_COOLDOWN_TICKS = 20 * 60 * 2; // 2 minutes
    private static final int COOLDOWN_MULTIPLIER = 5;
    private static final int MAX_COOLDOWN_TICKS = BASE_COOLDOWN_TICKS * COOLDOWN_MULTIPLIER;
    private static final long DEFAULT_LIFETIME_MILLIS = TimeUnit.DAYS.toMillis(3);
    private static final Map<String, Integer> TASK_COOLDOWNS = Map.of(
        "drill", MAX_COOLDOWN_TICKS,
        "extract", MAX_COOLDOWN_TICKS
    );
    private static final int DRILL_BATCH_SIZE = 9;
    private static final float DRILL_BEAM_RADIUS = 1.5f;
    private static final int MINING_DELAY_PER_BLOCK = 20; // Delay between mining each block (1 second)

    public MiningSatellite(String accessCode) {
        this(UUID.randomUUID(), accessCode, MAX_COOLDOWN_TICKS, 0, true, System.currentTimeMillis(), DEFAULT_LIFETIME_MILLIS);
    }

    private MiningSatellite(UUID id, String accessCode, int maxCooldown, int rechargeTicks, boolean active,
                            long createdAtMillis, long lifetimeMillis) {
        super(id, accessCode, maxCooldown, rechargeTicks, active, createdAtMillis, lifetimeMillis);
    }

    public static MiningSatellite fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        UUID id = readUuid(nbt, "Id");
        String code = readString(nbt, "AccessCode", "UNKNOWN");
        int recharge = readInt(nbt, "RechargeTicks", 0);
        int maxRecharge = readInt(nbt, "MaxRecharge", MAX_COOLDOWN_TICKS);
        boolean active = readBoolean(nbt, "Active", true);
        long createdAt = readLong(nbt, "CreatedAtMillis", System.currentTimeMillis());
        long lifetime = readLong(nbt, "LifetimeMillis", DEFAULT_LIFETIME_MILLIS);
        return new MiningSatellite(id, code, maxRecharge, recharge, active, createdAt, lifetime);
    }

    @Override
    public String getTypeId() {
        return "mining";
    }

    @Override
    public SatelliteResult executeSatelliteCommand(String command, String[] args, ServerWorld world, BlockPos terminalPos) {
        return switch (command.toLowerCase()) {
            case "drill" -> executeDrill(args, world, terminalPos);
            case "extract" -> executeExtract(args, world, terminalPos);
            default -> SatelliteResult.failure("Mining satellite does not recognize command '" + command + "'");
        };
    }

    private SatelliteResult executeDrill(String[] args, ServerWorld world, BlockPos terminalPos) {
        if (!isReady()) {
            int seconds = Math.max(1, getRechargeTicks() / 20);
            return SatelliteResult.failure("Satellite charging. Ready in " + seconds + "s.");
        }

        if (args.length < 2) {
            return SatelliteResult.failure("Usage: drill <x> <z>\nDrills a 3x3 area from surface down to bedrock");
        }

        BlockPos targetPos;
        try {
            int x = Integer.parseInt(args[0]);
            int z = Integer.parseInt(args[1]);
            // Find the surface Y at this position
            int surfaceY = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, x, z);
            targetPos = new BlockPos(x, surfaceY, z);
        } catch (NumberFormatException ex) {
            return SatelliteResult.failure("Coordinates must be integers.");
        }

        // Drill a 3x3 area down to bedrock
        int totalMined = drillArea(world, targetPos, terminalPos);
        
        startRecharge("drill", TASK_COOLDOWNS);
        if (totalMined <= 0) {
            return SatelliteResult.success("Drill area contains no minable blocks at " + targetPos.getX() + ", " + targetPos.getZ());
        }
        return SatelliteResult.success("Orbital drill initiated at " + targetPos.getX() + ", " + targetPos.getZ() + 
            ". Mining " + totalMined + " blocks. Progress updates incoming.");
    }

    private SatelliteResult executeExtract(String[] args, ServerWorld world, BlockPos terminalPos) {
        if (!isReady()) {
            int seconds = Math.max(1, getRechargeTicks() / 20);
            return SatelliteResult.failure("Satellite charging. Ready in " + seconds + "s.");
        }

        if (args.length < 3) {
            return SatelliteResult.failure("Usage: extract <x> <y> <z>\nExtracts ores in a 3x3 area at target height");
        }

        BlockPos targetPos;
        try {
            int x = Integer.parseInt(args[0]);
            int y = Integer.parseInt(args[1]);
            int z = Integer.parseInt(args[2]);
            targetPos = new BlockPos(x, y, z);
        } catch (NumberFormatException ex) {
            return SatelliteResult.failure("Coordinates must be integers.");
        }

        // Extract ores in a 3x3 area (horizontal only)
        int extracted = extractOres(world, targetPos, terminalPos);
        
        startRecharge("extract", TASK_COOLDOWNS);
        if (extracted <= 0) {
            return SatelliteResult.success("No ore detected in 3x3 area centered at " + targetPos.getX() + ", " + targetPos.getY() + ", " + targetPos.getZ());
        }
        return SatelliteResult.success("Ore extraction initiated at " + targetPos.getX() + ", " + targetPos.getY() + ", " + targetPos.getZ() +
            ". Targeting " + extracted + " ore blocks. Progress updates incoming.");
    }

    /**
     * Drill a 3x3 area down to bedrock with beam effects
     */
    private int drillArea(ServerWorld world, BlockPos topCenter, BlockPos terminalPos) {
        int bedrockY = world.getBottomY();
        
        // Collect all blocks to mine in order (top to bottom, column by column)
        List<BlockPos> blocksToMine = new ArrayList<>();
        
        int startY = topCenter.getY();
        for (int y = startY; y > bedrockY; y--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = new BlockPos(topCenter.getX() + dx, y, topCenter.getZ() + dz);
                    BlockState state = world.getBlockState(pos);
                    
                    if (!state.isAir() && canMineBlock(state, pos)) {
                        blocksToMine.add(pos);
                    }
                }
            }
        }

        int totalBlocks = blocksToMine.size();
        Circuitmod.LOGGER.info("[MINING-SATELLITE] Starting drill operation at {} drilling {} blocks to Y={}", 
            topCenter, totalBlocks, bedrockY);

        // Schedule mining with delays
        scheduleMiningOperations(world, blocksToMine, terminalPos, "Drill", topCenter, DRILL_BATCH_SIZE, true);

        return totalBlocks;
    }

    /**
     * Extract ores from a 3x3 area at a specific height (horizontal only)
     */
    private int extractOres(ServerWorld world, BlockPos center, BlockPos terminalPos) {
        List<BlockPos> oreBlocks = new ArrayList<>();
        
        // Collect all ore blocks in 3x3 area
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = center.add(dx, 0, dz);
                BlockState state = world.getBlockState(pos);
                if (isOreBlock(state) && canMineBlock(state, pos)) {
                    oreBlocks.add(pos);
                }
            }
        }

        int oreCount = oreBlocks.size();
        Circuitmod.LOGGER.info("[MINING-SATELLITE] Extracting {} ore blocks from 3x3 area at {}", oreCount, center);

        // Schedule mining with delays
        scheduleMiningOperations(world, oreBlocks, terminalPos, "Extraction", center, DRILL_BATCH_SIZE, false);

        return oreCount;
    }

    /**
     * Schedule mining operations with delays for visual effect
     */
    private void scheduleMiningOperations(ServerWorld world, List<BlockPos> blocks, BlockPos terminalPos, String operationLabel, BlockPos targetPos, int batchSize, boolean spawnBeam) {
        if (blocks.isEmpty()) {
            return;
        }

        Circuitmod.LOGGER.info("[MINING-SATELLITE] Starting to mine {} blocks over {} seconds", 
            blocks.size(), blocks.size() * MINING_DELAY_PER_BLOCK / 20.0);
        
        // Set beam on controller if requested
        if (spawnBeam) {
            BlockEntity blockEntity = world.getBlockEntity(terminalPos);
            if (blockEntity instanceof starduster.circuitmod.block.entity.SatelliteControlBlockEntity controller) {
                float beamHeight = Math.max(32f, targetPos.getY() - world.getBottomY() + 1);
                controller.setActiveBeam(targetPos, beamHeight, DRILL_BEAM_RADIUS);
                Circuitmod.LOGGER.info("[MINING-SATELLITE] Activated beam on controller at {} targeting {} with height {} and radius {}", 
                    terminalPos, targetPos, beamHeight, DRILL_BEAM_RADIUS);
            }
        }
        
        // Use the mining operation manager to handle timed mining
        MiningOperationManager.startMiningOperation(
            world,
            blocks,
            batchSize,
            pos -> spawnBeamAndMine(world, pos),
            new SatelliteMiningProgressListener(world, terminalPos, operationLabel, targetPos, spawnBeam)
        );
    }

    /**
     * Spawn beam and mine the block
     */
    private void spawnBeamAndMine(ServerWorld world, BlockPos pos) {
        // Check if block still exists and can be mined
        BlockState state = world.getBlockState(pos);
        if (!state.isAir() && canMineBlock(state, pos)) {
            // Mine the block
            mineBlock(world, pos);
        }
    }

    /**
     * Check if a block is an ore
     */
    private boolean isOreBlock(BlockState state) {
        return state.isIn(net.minecraft.registry.tag.BlockTags.COAL_ORES) ||
               state.isIn(net.minecraft.registry.tag.BlockTags.IRON_ORES) ||
               state.isIn(net.minecraft.registry.tag.BlockTags.COPPER_ORES) ||
               state.isIn(net.minecraft.registry.tag.BlockTags.GOLD_ORES) ||
               state.isIn(net.minecraft.registry.tag.BlockTags.DIAMOND_ORES) ||
               state.isIn(net.minecraft.registry.tag.BlockTags.EMERALD_ORES) ||
               state.isIn(net.minecraft.registry.tag.BlockTags.LAPIS_ORES) ||
               state.isIn(net.minecraft.registry.tag.BlockTags.REDSTONE_ORES) ||
               state.getBlock() == net.minecraft.block.Blocks.ANCIENT_DEBRIS ||
               state.getBlock() == net.minecraft.block.Blocks.NETHER_QUARTZ_ORE;
    }

    /**
     * Mine a single block (based on quarry logic)
     */
    private boolean mineBlock(ServerWorld world, BlockPos miningPos) {
        BlockState blockState = world.getBlockState(miningPos);
        BlockEntity blockEntity = world.getBlockEntity(miningPos);
        
        // Create mock tool with Fortune
        ItemStack mockTool = new ItemStack(Items.NETHERITE_PICKAXE);
        Registry<Enchantment> reg = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        Enchantment fortune = reg.get(Identifier.ofVanilla("fortune"));
        if (fortune != null) {
            int raw = reg.getRawId(fortune);
            var ref = reg.getEntry(raw);
            ref.ifPresent(entry -> mockTool.addEnchantment(entry, 3)); // Fortune III
        }

        // Get the drops
        List<ItemStack> drops = Block.getDroppedStacks(
            blockState,
            world,
            miningPos,
            blockEntity,
            null,
            mockTool
        );
        
        // Spawn drops at the location
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                Block.dropStack(world, miningPos, drop);
            }
        }
        
        // Remove the block
        world.removeBlock(miningPos, false);
        
        return true;
    }

    /**
     * Check if a block can be mined
     */
    private boolean canMineBlock(BlockState state, BlockPos pos) {
        // Don't mine bedrock or air
        if (state.isAir() || state.getBlock() == net.minecraft.block.Blocks.BEDROCK) {
            return false;
        }
        return true;
    }
    
    /**
     * Sends mining progress updates back to the originating terminal
     */
    private static class SatelliteMiningProgressListener implements MiningOperationManager.MiningProgressListener {
        private final ServerWorld world;
        private final BlockPos terminalPos;
        private final String operationLabel;
        private final BlockPos targetPos;
        private final boolean hasBeam;
        private int lastPercentBroadcast = -1;
        private int currentLowestY;
        private int lastBeamUpdateY = Integer.MAX_VALUE;
        
        public SatelliteMiningProgressListener(ServerWorld world, BlockPos terminalPos, String operationLabel, BlockPos targetPos, boolean hasBeam) {
            this.world = world;
            this.terminalPos = terminalPos;
            this.operationLabel = operationLabel;
            this.targetPos = targetPos;
            this.hasBeam = hasBeam;
            this.currentLowestY = targetPos.getY();
        }
        
        @Override
        public void onStart(int totalBlocks) {
            sendLine(String.format("[%s] Target locked at %d, %d. %d blocks queued.", 
                operationLabel, targetPos.getX(), targetPos.getZ(), totalBlocks));
        }
        
        @Override
        public void onBlockMined(BlockPos pos, int completedBlocks, int totalBlocks) {
            // Update beam position to track the current mining depth
            if (hasBeam && pos != null) {
                // Track the lowest Y we've mined to
                if (pos.getY() < currentLowestY) {
                    currentLowestY = pos.getY();
                }
                
                // Update beam every few blocks to reduce network traffic
                if (Math.abs(currentLowestY - lastBeamUpdateY) >= 3) {
                    BlockEntity blockEntity = world.getBlockEntity(terminalPos);
                    if (blockEntity instanceof starduster.circuitmod.block.entity.SatelliteControlBlockEntity controller) {
                        // Update beam to point at the current lowest mining position
                        BlockPos beamTarget = new BlockPos(targetPos.getX(), currentLowestY, targetPos.getZ());
                        controller.updateBeamTarget(beamTarget);
                        lastBeamUpdateY = currentLowestY;
                    }
                }
            }
            
            // Progress reporting
            int percent = (int)Math.floor((completedBlocks / (double) totalBlocks) * 100);
            boolean shouldBroadcast = totalBlocks <= 20 || completedBlocks == totalBlocks;
            if (!shouldBroadcast) {
                if (percent >= lastPercentBroadcast + 5) {
                    shouldBroadcast = true;
                }
            }
            if (shouldBroadcast) {
                lastPercentBroadcast = percent;
                sendLine(String.format("[%s] Progress: %d/%d blocks (%d%%) - Depth: Y=%d", 
                    operationLabel, completedBlocks, totalBlocks, percent, currentLowestY));
            }
        }
        
        @Override
        public void onComplete(int totalBlocks) {
            sendLine(String.format("[%s] Operation complete. Processed %d blocks.", operationLabel, totalBlocks));
            // Clear the beam from the controller
            if (hasBeam) {
                BlockEntity blockEntity = world.getBlockEntity(terminalPos);
                if (blockEntity instanceof starduster.circuitmod.block.entity.SatelliteControlBlockEntity controller) {
                    controller.clearActiveBeam();
                    Circuitmod.LOGGER.info("[MINING-SATELLITE] Cleared beam from controller at {}", terminalPos);
                }
            }
        }
        
        private void sendLine(String line) {
            if (line == null || line.isEmpty()) {
                return;
            }
            BlockEntity blockEntity = world.getBlockEntity(terminalPos);
            if (blockEntity instanceof SatelliteControlBlockEntity terminal) {
                terminal.appendOutputLine(line);
            }
        }
    }

    @Override
    protected void writeCustomData(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        // No custom data yet
    }

    @Override
    public String describe() {
        return "MINING Satellite\n" +
            "  Type    : Resource Extraction\n" +
            "  Drill   : 3x3 column to bedrock\n" +
            "  Extract : 3x3 ore extraction\n" +
            "  Fortune : Level III\n" +
            "  " + getStatusLine();
    }
}


