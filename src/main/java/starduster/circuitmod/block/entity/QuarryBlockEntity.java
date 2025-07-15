package starduster.circuitmod.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.network.ModNetworking;
import starduster.circuitmod.power.EnergyNetwork;
import starduster.circuitmod.power.IEnergyConsumer;
import starduster.circuitmod.power.IPowerConnectable;
import starduster.circuitmod.screen.ModScreenHandlers;
import starduster.circuitmod.screen.QuarryScreenHandler;

import org.jetbrains.annotations.Nullable;

public class QuarryBlockEntity extends BlockEntity implements SidedInventory, NamedScreenHandlerFactory, IEnergyConsumer {
    // Energy properties
    private static final int MAX_ENERGY_DEMAND = 20; // Maximum energy demand per tick
    private int energyDemand = MAX_ENERGY_DEMAND; // Current energy demand per tick
    private int energyReceived = 0; // Energy received this tick
    private EnergyNetwork network;
    
    // Mining properties
    private int miningSpeed = 0; // Current mining speed (blocks per tick)
    private static final int TICKS_PER_SECOND = 20; // Minecraft runs at 20 ticks per second
    private int currentBlockEnergyCost = 1; // Energy cost for the current block being mined
    
    // Mining progress tracking
    private BlockPos currentMiningPos = null; // Current block being mined
    private int currentMiningProgress = 0; // Progress on current block (0-100)
    private int totalMiningTicks = 0; // Total ticks needed to mine current block
    private int currentMiningTicks = 0; // Current ticks spent mining
    
    // Area properties
    private static final int DEFAULT_AREA_SIZE = 16; // 16x16 square
    private BlockPos startPos; // Starting corner of the mining area
    private BlockPos currentPos; // Current mining position
    private int currentY; // Current mining Y level
    private Direction facingDirection; // Direction the quarry is facing
    
    // Networking properties
    private int lastSentSpeed = 0; // Last mining speed sent to clients
    private int packetCooldown = 0; // Cooldown to avoid sending too many packets
    private static final int PACKET_COOLDOWN_MAX = 10; // Only send packets every 10 ticks max (0.5 seconds)
    
    // Property delegate indices
    private static final int ENERGY_RECEIVED_INDEX = 0;
    private static final int MINING_SPEED_INDEX = 1;
    private static final int PROPERTY_COUNT = 2;
    
    // Inventory with chest size (27 slots)
    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(27, ItemStack.EMPTY);
    
    // Property delegate for GUI synchronization
    private final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        // Direct storage for client-side property values to avoid synchronization issues
        private int clientSideMiningSpeed = 0;
        
        @Override
        public int get(int index) {
            switch (index) {
                case ENERGY_RECEIVED_INDEX:
                    if (world != null && world.isClient()) {
                        
                    }
                    return energyReceived;
                case MINING_SPEED_INDEX:
                    // On client side, use our direct storage
                    if (world != null && world.isClient()) {
                        int value = clientSideMiningSpeed > 0 ? clientSideMiningSpeed : miningSpeed * TICKS_PER_SECOND;
                        return value;
                    } 
                    // On server side, calculate as normal
                    else {
                        int blocksPerSecond = miningSpeed * TICKS_PER_SECOND;
                        return blocksPerSecond;
                    }
                default:
                    return 0;
            }
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case ENERGY_RECEIVED_INDEX:
                    energyReceived = value;
                    if (world != null && world.isClient()) {
                        
                    }
                    break;
                case MINING_SPEED_INDEX:
                    // Convert back from blocks per second to blocks per tick
                    int newMiningSpeed = Math.max(0, value / TICKS_PER_SECOND);
                    
                    // Debug logging with client/server distinction
                    if (world != null && world.isClient()) {
                        
                        
                        // On client side, store the raw blocks/sec value directly
                        clientSideMiningSpeed = value;
                        
                        // Also update mining speed for consistency
                        if (miningSpeed != newMiningSpeed) {
                            miningSpeed = newMiningSpeed;
                            markDirty();
                        }
                    } else {
                        
                        
                        // Force a screen refresh by storing the blocks per second directly
                        if (value > 0) {
                            lastSentSpeed = value;
                        }
                        
                        if (miningSpeed != newMiningSpeed) {
                            miningSpeed = newMiningSpeed;
                            markDirty();
                        }
                    }
                    break;
            }
        }

        @Override
        public int size() {
            return PROPERTY_COUNT;
        }
    };

    public QuarryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QUARRY_BLOCK_ENTITY, pos, state);
    }

    // Save data to NBT when the block is saved
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        
        // Save mining and energy data
        nbt.putInt("energy_demand", this.energyDemand);
        nbt.putInt("energy_received", this.energyReceived);
        nbt.putInt("mining_speed", this.miningSpeed);
        nbt.putInt("current_block_energy_cost", this.currentBlockEnergyCost);
        
        // Save mining progress data
        if (currentMiningPos != null) {
            nbt.putInt("current_mining_x", currentMiningPos.getX());
            nbt.putInt("current_mining_y", currentMiningPos.getY());
            nbt.putInt("current_mining_z", currentMiningPos.getZ());
        }
        nbt.putInt("current_mining_progress", this.currentMiningProgress);
        nbt.putInt("total_mining_ticks", this.totalMiningTicks);
        nbt.putInt("current_mining_ticks", this.currentMiningTicks);
        
        // Save mining area data
        if (startPos != null) {
            nbt.putInt("start_x", startPos.getX());
            nbt.putInt("start_y", startPos.getY());
            nbt.putInt("start_z", startPos.getZ());
        }
        
        if (currentPos != null) {
            nbt.putInt("current_x", currentPos.getX());
            nbt.putInt("current_y", currentPos.getY());
            nbt.putInt("current_z", currentPos.getZ());
        }
        
        nbt.putInt("current_y_level", this.currentY);
        
        if (facingDirection != null) {
            nbt.putInt("facing_direction", facingDirection.ordinal());
        }
        
        // Save network data if we have a network
        if (network != null) {
            NbtCompound networkNbt = new NbtCompound();
            network.writeToNbt(networkNbt);
            nbt.put("energy_network", networkNbt);
        }
        
        // Save inventory
        Inventories.writeNbt(nbt, this.inventory, registries);
    }

    // Load data from NBT when the block is loaded
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        
        // Load mining and energy data
        this.energyDemand = nbt.getInt("energy_demand").orElse(MAX_ENERGY_DEMAND);
        this.energyReceived = nbt.getInt("energy_received").orElse(0);
        this.miningSpeed = nbt.getInt("mining_speed").orElse(0);
        this.currentBlockEnergyCost = nbt.getInt("current_block_energy_cost").orElse(1);
        
        // Load mining progress data
        if (nbt.contains("current_mining_x") && nbt.contains("current_mining_y") && nbt.contains("current_mining_z")) {
            int x = nbt.getInt("current_mining_x").orElse(0);
            int y = nbt.getInt("current_mining_y").orElse(0);
            int z = nbt.getInt("current_mining_z").orElse(0);
            this.currentMiningPos = new BlockPos(x, y, z);
        }
        this.currentMiningProgress = nbt.getInt("current_mining_progress").orElse(0);
        this.totalMiningTicks = nbt.getInt("total_mining_ticks").orElse(0);
        this.currentMiningTicks = nbt.getInt("current_mining_ticks").orElse(0);
        
        // Load mining area data
        if (nbt.contains("start_x") && nbt.contains("start_y") && nbt.contains("start_z")) {
            int startX = nbt.getInt("start_x").orElse(0);
            int startY = nbt.getInt("start_y").orElse(0);
            int startZ = nbt.getInt("start_z").orElse(0);
            this.startPos = new BlockPos(startX, startY, startZ);
        }
        
        if (nbt.contains("current_x") && nbt.contains("current_y") && nbt.contains("current_z")) {
            int currentX = nbt.getInt("current_x").orElse(0);
            int currentY = nbt.getInt("current_y").orElse(0);
            int currentZ = nbt.getInt("current_z").orElse(0);
            this.currentPos = new BlockPos(currentX, currentY, currentZ);
        }
        
        this.currentY = nbt.getInt("current_y_level").orElse(0);
        
        if (nbt.contains("facing_direction")) {
            int dirOrdinal = nbt.getInt("facing_direction").orElse(0);
            this.facingDirection = Direction.values()[dirOrdinal];
        }
        
        // Load network data
        if (nbt.contains("energy_network")) {
            this.network = new EnergyNetwork();
            NbtCompound networkNbt = nbt.getCompound("energy_network").orElse(new NbtCompound());
            network.readFromNbt(networkNbt);
        }
        
        // Load inventory
        Inventories.readNbt(nbt, this.inventory, registries);
    }

    // The tick method called by the ticker in QuarryBlock
    public static void tick(World world, BlockPos pos, BlockState state, QuarryBlockEntity blockEntity) {
        if (world.isClient()) {
            return;
        }

        // Initialize mining area if not set (this should happen first)
        if (blockEntity.startPos == null) {
            blockEntity.initializeMiningArea(pos, state);
        }
        
        // Safety check - validate that quarry's mining area exists
        if (blockEntity.currentPos == null) {
            blockEntity.initializeMiningArea(pos, state);
            return;
        }
        
        // If we're connected to a network, we'll get energy during the network's tick
        // Make sure to set our demand for the next tick
        blockEntity.energyDemand = MAX_ENERGY_DEMAND;
        
        boolean needsSync = false;
        
        // Debug log for diagnostics
        if (world.getTime() % 20 == 0) { // Only log every second
            String networkInfo = blockEntity.network != null ? blockEntity.network.getNetworkId() : "NO NETWORK";
            Circuitmod.LOGGER.info("[QUARRY-TICK] Energy received: " + blockEntity.energyReceived + ", mining speed: " + blockEntity.miningSpeed + ", network: " + networkInfo);
        }
        
        // Process mining operations based on energy available
        if (blockEntity.energyReceived > 0) {
            // Try to mine the current block (this will handle gradual mining)
            boolean mined = blockEntity.mineNextBlock(world);
            
            if (mined) {
                needsSync = true;
            }
            
            // Calculate mining speed for display (blocks per second)
            if (blockEntity.currentMiningPos != null) {
                // Calculate based on progress and remaining time
                int remainingTicks = blockEntity.totalMiningTicks - blockEntity.currentMiningTicks;
                if (remainingTicks > 0) {
                    // Estimate blocks per second based on current progress
                    float progressRatio = (float) blockEntity.currentMiningTicks / blockEntity.totalMiningTicks;
                    float estimatedBlocksPerSecond = (1.0f - progressRatio) * TICKS_PER_SECOND / remainingTicks;
                    blockEntity.miningSpeed = Math.max(1, (int) estimatedBlocksPerSecond);
                } else {
                    blockEntity.miningSpeed = 1; // At least 1 block per second
                }
            } else {
                blockEntity.miningSpeed = 0;
            }
        } else {
            // Set mining speed to 0 if no energy received
            blockEntity.miningSpeed = 0;
            needsSync = true;
        }
        
        // Reset energy received at the end of each tick
        blockEntity.energyReceived = 0;
        
        // Packet throttling logic to avoid network spam
        blockEntity.packetCooldown--;
        if (blockEntity.packetCooldown <= 0) {
            blockEntity.packetCooldown = 0;
        }
        
        // Mark dirty if anything changed
        if (needsSync) {
            blockEntity.markDirty();
            
            // Send explicit mining speed update packet to all tracking players
            if (world instanceof ServerWorld serverWorld) {
                // Convert from blocks/tick to blocks/second for display
                int blocksPerSecond = blockEntity.miningSpeed * TICKS_PER_SECOND;
                
                // Only send packet if it's different from the last one or cooldown is done
                if (blocksPerSecond != blockEntity.lastSentSpeed || blockEntity.packetCooldown <= 0) {
                    // Send to all players tracking the quarry block
                    for (ServerPlayerEntity player : PlayerLookup.tracking(serverWorld, pos)) {
                        ModNetworking.sendMiningSpeedUpdate(player, blocksPerSecond, pos);
                    }
                    
                    // Update the last sent speed and reset cooldown
                    blockEntity.lastSentSpeed = blocksPerSecond;
                    blockEntity.packetCooldown = PACKET_COOLDOWN_MAX;
                }
            }
            
            // Also do the normal block update
            world.updateListeners(pos, state, state, 3);
        }
        
        // Send mining progress updates to clients (every few ticks to avoid spam)
        if (world instanceof ServerWorld serverWorld && blockEntity.currentMiningPos != null && blockEntity.currentMiningProgress > 0) {
            // Send progress updates every 5 ticks (4 times per second) to avoid spam
            if (world.getTime() % 5 == 0) {
                for (ServerPlayerEntity player : PlayerLookup.tracking(serverWorld, pos)) {
                    ModNetworking.sendMiningProgressUpdate(player, blockEntity.currentMiningProgress, blockEntity.currentMiningPos, pos);
                }
            }
        }
    }
    
    // Initialize the mining area based on the quarry's position and facing direction
    private void initializeMiningArea(BlockPos pos, BlockState state) {
        // Get the facing direction from the block state using HorizontalFacingBlock.FACING
        Direction direction = null;
        try {
            // Get the facing direction directly from the block state
            direction = state.get(net.minecraft.block.HorizontalFacingBlock.FACING);
        } catch (Exception e) {
            direction = Direction.NORTH;
        }
        
        // If we couldn't get direction, default to NORTH
        if (direction == null) {
            direction = Direction.NORTH;
        }
        
        this.facingDirection = direction;
        
        // Set the starting corner as the quarry's position
        this.startPos = pos;
        
        // Start with a smaller 3x3 area for easier visualization
        int areaSize = 3;
        
        // Start two blocks away in the facing direction to ensure safety
        // (one block in front of the quarry, then start the area in front of that)
        BlockPos safeAreaStart = pos.offset(facingDirection, 2);
        
        // Calculate mining area bounds centered on the safe start position
        int minX = safeAreaStart.getX() - 1;
        int maxX = safeAreaStart.getX() + 1;
        int minZ = safeAreaStart.getZ() - 1;
        int maxZ = safeAreaStart.getZ() + 1;
        
        // Double-check that quarry is not in the mining area
        if (isPositionInArea(pos, minX, maxX, minZ, maxZ)) {
            // If somehow the quarry is still in the area, push it one more block away
            safeAreaStart = pos.offset(facingDirection, 3);
            minX = safeAreaStart.getX() - 1;
            maxX = safeAreaStart.getX() + 1;
            minZ = safeAreaStart.getZ() - 1;
            maxZ = safeAreaStart.getZ() + 1;
        }
        
        // Start at the corner of the mining area
        this.currentPos = new BlockPos(minX, pos.getY(), minZ);
        
        // Start mining at the quarry's Y level
        this.currentY = pos.getY();
    }
    
    // Helper method to check if a position is inside a rectangular area
    private boolean isPositionInArea(BlockPos pos, int minX, int maxX, int minZ, int maxZ) {
        return pos.getX() >= minX && pos.getX() <= maxX && 
               pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }
    
    // Mining logic to mine a block at the current position
    private boolean mineNextBlock(World world) {
        // If we don't have a current mining position, get the next one
        if (currentMiningPos == null) {
            currentMiningPos = getNextMiningPos();
            if (currentMiningPos == null) {
                Circuitmod.LOGGER.warn("[QUARRY-MINE] No mining position available");
                return false;
            }
            
            // Reset mining progress for new block
            currentMiningProgress = 0;
            currentMiningTicks = 0;
            
            Circuitmod.LOGGER.info("[QUARRY-MINE] Starting to mine at position: " + currentMiningPos);
        }
        
        // Skip if it's the quarry itself, a safe zone block, air or bedrock
        if (currentMiningPos.equals(pos) || isInSafeZone(currentMiningPos)) {
            Circuitmod.LOGGER.info("[QUARRY-MINE] Position is in safe zone, skipping");
            advanceToNextBlock();
            return false;
        }
        
        BlockState blockState = world.getBlockState(currentMiningPos);
        if (blockState.isAir()) {
            Circuitmod.LOGGER.info("[QUARRY-MINE] Block is air, skipping");
            advanceToNextBlock();
            return false;
        }
        
        if (blockState.getHardness(world, currentMiningPos) < 0) {
            Circuitmod.LOGGER.info("[QUARRY-MINE] Block is bedrock or unbreakable, skipping");
            advanceToNextBlock();
            return false;
        }
        
        // Skip if the block is a block entity that's part of our network
        BlockEntity targetEntity = world.getBlockEntity(currentMiningPos);
        if (targetEntity instanceof IPowerConnectable) {
            Circuitmod.LOGGER.info("[QUARRY-MINE] Block is part of power network, skipping");
            advanceToNextBlock();
            return false;
        }
        
        // Calculate energy cost based on block hardness
        float hardness = blockState.getHardness(world, currentMiningPos);
        // Convert hardness to energy cost: hardness * 2 + 1 (minimum 1 energy)
        int energyCost = Math.max(1, (int)(hardness * 2.0f) + 1);
        this.currentBlockEnergyCost = energyCost;
        
        // Calculate total ticks needed to mine this block (based on hardness and mining speed)
        if (totalMiningTicks == 0) {
            // Base mining time: 20 ticks per hardness point, minimum 10 ticks
            totalMiningTicks = Math.max(10, (int)(hardness * 20.0f));
            Circuitmod.LOGGER.info("[QUARRY-MINE] Block requires " + totalMiningTicks + " ticks to mine (hardness: " + hardness + ")");
        }
        
        // Check if we have enough energy to continue mining this block
        if (this.energyReceived < 1) {
            if (world.getTime() % 20 == 0) { // Only log every second
                Circuitmod.LOGGER.info("[QUARRY-MINE] Not enough energy to continue mining. Required: 1, Available: " + this.energyReceived);
            }
            return false; // Don't advance position, just wait for more energy
        }
        
        // Consume 1 energy per tick of mining
        this.energyReceived -= 1;
        currentMiningTicks++;
        
        // Calculate progress percentage
        currentMiningProgress = (currentMiningTicks * 100) / totalMiningTicks;
        currentMiningProgress = Math.min(100, currentMiningProgress);
        
        // Only log progress every 10 ticks to reduce spam
        if (world.getTime() % 10 == 0) {
            Circuitmod.LOGGER.info("[QUARRY-MINE] Mining progress: " + currentMiningProgress + "% (" + currentMiningTicks + "/" + totalMiningTicks + " ticks)");
        }
        
        // Check if we've finished mining this block
        if (currentMiningTicks >= totalMiningTicks) {
            // Get drops from the block
            ItemStack minedItem = new ItemStack(blockState.getBlock().asItem());
            Circuitmod.LOGGER.info("[QUARRY-MINE] Finished mining block: " + blockState.getBlock().getName().getString() + " (hardness: " + hardness + ", energy cost: " + energyCost + ")");
            
            // Add to inventory if there's space
            boolean addedToInventory = false;
            for (int i = 0; i < inventory.size(); i++) {
                ItemStack stack = inventory.get(i);
                if (stack.isEmpty()) {
                    inventory.set(i, minedItem);
                    addedToInventory = true;
                    Circuitmod.LOGGER.info("[QUARRY-MINE] Added to empty slot " + i);
                    break;
                } else if (ItemStack.areItemsEqual(stack, minedItem) && stack.getCount() < stack.getMaxCount()) {
                    stack.increment(1);
                    addedToInventory = true;
                    Circuitmod.LOGGER.info("[QUARRY-MINE] Added to existing stack in slot " + i);
                    break;
                }
            }
            
            // If we successfully added to the inventory, remove the block
            if (addedToInventory) {
                world.removeBlock(currentMiningPos, false);
                Circuitmod.LOGGER.info("[QUARRY-SUCCESS] Successfully mined block at " + currentMiningPos);
            } else {
                Circuitmod.LOGGER.info("[QUARRY-FAIL] Inventory full, could not mine block");
            }
            
            // Advance to next block
            advanceToNextBlock();
            return true;
        }
        
        return false; // Still mining, not finished yet
    }
    
    // Helper method to advance to the next block
    private void advanceToNextBlock() {
        currentMiningPos = null;
        currentMiningProgress = 0;
        totalMiningTicks = 0;
        currentMiningTicks = 0;
        
        // Advance the position for the next block
        if (currentPos != null && startPos != null && facingDirection != null) {
            // Calculate the safe starting position (2 blocks in front of quarry)
            BlockPos safeAreaStart = startPos.offset(facingDirection, 2);
            
            // Calculate 3x3 mining area bounds centered on the safe start position
            int minX = safeAreaStart.getX() - 1;
            int maxX = safeAreaStart.getX() + 1;
            int minZ = safeAreaStart.getZ() - 1;
            int maxZ = safeAreaStart.getZ() + 1;
            
            advanceToNextPosition(minX, maxX, minZ, maxZ);
        }
    }
    
    // Mining logic to get the next position to mine
    private BlockPos getNextMiningPos() {
        if (currentPos == null || startPos == null || facingDirection == null) {
            return null;
        }
        
        // Create the position at the current Y level
        BlockPos miningPos = new BlockPos(currentPos.getX(), currentY, currentPos.getZ());
        
        // Calculate the safe starting position (2 blocks in front of quarry)
        BlockPos safeAreaStart = startPos.offset(facingDirection, 2);
        
        // Calculate 3x3 mining area bounds centered on the safe start position
        int minX = safeAreaStart.getX() - 1;
        int maxX = safeAreaStart.getX() + 1;
        int minZ = safeAreaStart.getZ() - 1;
        int maxZ = safeAreaStart.getZ() + 1;
        
        // Check if the current position is somehow equal to the quarry or in safe zone
        if (miningPos.equals(pos) || isInSafeZone(miningPos)) {
            // Move to the next position 
            advanceToNextPosition(minX, maxX, minZ, maxZ);
            
            // Try again with the new position, but do it in a loop to avoid stack overflow
            int attempts = 0;
            while ((currentPos.equals(pos) || isInSafeZone(new BlockPos(currentPos.getX(), currentY, currentPos.getZ()))) 
                   && attempts < 10) {
                attempts++;
                advanceToNextPosition(minX, maxX, minZ, maxZ);
            }
            
            // Return the new position after advancing
            miningPos = new BlockPos(currentPos.getX(), currentY, currentPos.getZ());
        }
        
        return miningPos;
    }
    
    // Helper method to advance to the next position
    private void advanceToNextPosition(int minX, int maxX, int minZ, int maxZ) {
        int nextX = currentPos.getX();
        int nextZ = currentPos.getZ();
        int nextY = currentY; // Start with the current Y level
        
        // Use a counter to prevent infinite loops
        int safetyCounter = 0;
        boolean foundSafePosition = false;
        
        // Keep trying positions until we find a safe one or hit the safety limit
        while (!foundSafePosition && safetyCounter < 25) {
            safetyCounter++;
            
            // Move right (increment X)
            nextX++;
            
            // If we've reached the right edge, move to the left edge and down one Z row
            if (nextX > maxX) {
                nextX = minX;
                nextZ++;
                
                // If we've completed the entire layer, move down one Y level
                if (nextZ > maxZ) {
                    nextZ = minZ;
                    nextY--; // Decrement Y to move down one level
                }
            }
            
            // Check if this position is safe
            BlockPos nextPos = new BlockPos(nextX, nextY, nextZ);
            if (!nextPos.equals(pos) && !isInSafeZone(nextPos)) {
                // Found a safe position
                foundSafePosition = true;
            }
        }
        
        // Always update the position, whether we found a safe position or not
        currentPos = new BlockPos(nextX, currentPos.getY(), nextZ);
        currentY = nextY; // Make sure to update the Y level separately
        
        if (!foundSafePosition) {
            Circuitmod.LOGGER.warn("[QUARRY-POSITION] Couldn't find safe mining position after " + safetyCounter + " attempts");
        } else {
            Circuitmod.LOGGER.info("[QUARRY-POSITION] Advanced to position: " + currentPos + " at Y level " + currentY);
        }
    }
    
    // Check if the position is in the safe zone around the quarry
    private boolean isInSafeZone(BlockPos targetPos) {
        // First, check if it's the quarry itself
        if (targetPos.equals(pos)) {
            return true;
        }
        
        // Define a safe zone that includes the quarry itself and adjacent blocks
        for (Direction dir : Direction.values()) {
            BlockPos adjacentPos = pos.offset(dir);
            if (targetPos.equals(adjacentPos)) {
                return true; // Position is adjacent to the quarry
            }
            
            // Also check diagonals by combining horizontal directions
            if (dir.getAxis().isHorizontal()) {
                for (Direction dir2 : Direction.values()) {
                    if (dir2.getAxis().isHorizontal() && dir != dir2 && dir != dir2.getOpposite()) {
                        BlockPos diagonalPos = pos.offset(dir).offset(dir2);
                        if (targetPos.equals(diagonalPos)) {
                            return true; // Position is diagonally adjacent
                        }
                    }
                }
            }
        }
        
        // Also consider the block the quarry is placed on and the block above
        if (targetPos.equals(pos.up()) || targetPos.equals(pos.down())) {
            return true;
        }
        
        // Additional safety: consider blocks immediately in front of the quarry in its facing direction
        if (facingDirection != null) {
            BlockPos inFront = pos.offset(facingDirection);
            if (targetPos.equals(inFront)) {
                return true;
            }
        }
        
        // Also check if it's a power cable or other network component
        if (world != null && !world.isClient()) {
            BlockEntity targetEntity = world.getBlockEntity(targetPos);
            if (targetEntity instanceof IPowerConnectable) {
                return true;
            }
        }
        
        return false;
    }
    
    // IEnergyConsumer implementation
    @Override
    public boolean canConnectPower(Direction side) {
        return true; // Can connect from any side
    }
    
    @Override
    public EnergyNetwork getNetwork() {
        return network;
    }
    
    @Override
    public void setNetwork(EnergyNetwork network) {
        if (world != null && world.isClient()) {
            return;
        }
        
        // If we're changing networks, log it
        if (this.network != null && network != null && this.network != network) {
            Circuitmod.LOGGER.info("[QUARRY-NETWORK] Quarry at " + pos + " changing networks: " + this.network.getNetworkId() + " -> " + network.getNetworkId());
        } else if (network != null && this.network == null) {
            Circuitmod.LOGGER.info("[QUARRY-NETWORK] Quarry at " + pos + " connecting to network: " + network.getNetworkId());
        } else if (this.network != null && network == null) {
            Circuitmod.LOGGER.info("[QUARRY-NETWORK] Quarry at " + pos + " disconnecting from network: " + this.network.getNetworkId());
        }
        
        this.network = network;
        
        // Initialize mining area if not already done
        if (startPos == null && world != null) {
            initializeMiningArea(pos, world.getBlockState(pos));
        }
    }
    
    @Override
    public int consumeEnergy(int energyOffered) {
        if (world == null || world.isClient()) {
            return 0;
        }

        // Calculate how much energy we can actually consume
        int energyToConsume = Math.min(energyOffered, energyDemand);
        
        // Track the received energy for this tick
        if (energyToConsume > 0) {
            this.energyReceived += energyToConsume; // ACCUMULATE energy instead of setting directly
            
            // Debug logs to diagnose the issue (only log occasionally to avoid spam)
            if (world.getTime() % 20 == 0) { // Only log every second
                Circuitmod.LOGGER.info("[QUARRY-ENERGY] Energy offered: " + energyOffered + ", consumed: " + energyToConsume + ", accumulated: " + this.energyReceived);
            }
        }
        
        return energyToConsume;
    }
    
    @Override
    public int getEnergyDemand() {
        return energyDemand;
    }
    
    @Override
    public Direction[] getInputSides() {
        return Direction.values(); // Can receive from all sides
    }
    
    public PropertyDelegate getPropertyDelegate() {
        return propertyDelegate;
    }
    
    // Inventory implementation
    @Override
    public int size() {
        return inventory.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getStack(int slot) {
        return inventory.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        return Inventories.splitStack(inventory, slot, amount);
    }

    @Override
    public ItemStack removeStack(int slot) {
        return Inventories.removeStack(inventory, slot);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        inventory.set(slot, stack);
        if (stack.getCount() > getMaxCountPerStack()) {
            stack.setCount(getMaxCountPerStack());
        }
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        if (world.getBlockEntity(pos) != this) {
            return false;
        }
        return player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clear() {
        inventory.clear();
    }
    
    // SidedInventory implementation for automation compatibility
    @Override
    public int[] getAvailableSlots(Direction side) {
        // All slots are available from all sides
        int[] slots = new int[inventory.size()];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = i;
        }
        return slots;
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        return true; // Allow insertion from any side
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        return true; // Allow extraction from any side
    }
    
    // Synchronize block entity data to client when changes happen
    @Override
    public boolean onSyncedBlockEvent(int type, int data) {
        // Mark as needing sync on the client
        if (world != null && world.isClient()) {
            markDirty();
            return true;
        }
        return super.onSyncedBlockEvent(type, data);
    }
    
    /**
     * Sets the mining speed directly from a network packet
     * This is called on the client side when a packet is received
     * 
     * @param blocksPerSecond The mining speed in blocks per second
     */
    public void setMiningSpeedFromNetwork(int blocksPerSecond) {
        propertyDelegate.set(MINING_SPEED_INDEX, blocksPerSecond);
    }
    
    /**
     * Sets the mining progress directly from a network packet
     * This is called on the client side when a packet is received
     * 
     * @param miningProgress The mining progress (0-100)
     * @param miningPos The position being mined
     */
    public void setMiningProgressFromNetwork(int miningProgress, BlockPos miningPos) {
        // Only update on client side
        if (world != null && world.isClient()) {
            this.currentMiningProgress = miningProgress;
            this.currentMiningPos = miningPos;
            
            // Mark dirty to trigger a re-render
            markDirty();
            
            Circuitmod.LOGGER.info("[CLIENT] Updated mining progress: " + miningProgress + "% at " + miningPos);
        }
    }
    
    /**
     * Gets the current mining speed in blocks per second
     * @return the mining speed
     */
    public int getMiningSpeed() {
        return propertyDelegate.get(MINING_SPEED_INDEX);
    }
    
    /**
     * Gets the current mining position
     * @return the current mining position, or null if not mining
     */
    public BlockPos getCurrentMiningPos() {
        return currentMiningPos;
    }
    
    /**
     * Gets the current mining progress percentage (0-100)
     * @return the mining progress percentage
     */
    public int getCurrentMiningProgress() {
        return currentMiningProgress;
    }
    
    /**
     * Gets the total ticks needed to mine the current block
     * @return the total mining ticks
     */
    public int getTotalMiningTicks() {
        return totalMiningTicks;
    }
    
    /**
     * Gets the current ticks spent mining the current block
     * @return the current mining ticks
     */
    public int getCurrentMiningTicks() {
        return currentMiningTicks;
    }
    
    // NamedScreenHandlerFactory implementation
    @Override
    public Text getDisplayName() {
        return Text.translatable("block.circuitmod.quarry_block");
    }
    
    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new QuarryScreenHandler(syncId, playerInventory, this, this.propertyDelegate);
    }
} 