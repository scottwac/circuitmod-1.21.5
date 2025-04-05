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
    private static final int MINING_COST = 1; // Energy cost to mine 1 block
    private int miningSpeed = 0; // Current mining speed (blocks per tick)
    private static final int TICKS_PER_SECOND = 20; // Minecraft runs at 20 ticks per second
    
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
                        Circuitmod.LOGGER.info("[CLIENT-DELEGATE] Reading energy received: " + energyReceived);
                    }
                    return energyReceived;
                case MINING_SPEED_INDEX:
                    // On client side, use our direct storage
                    if (world != null && world.isClient()) {
                        int value = clientSideMiningSpeed > 0 ? clientSideMiningSpeed : miningSpeed * TICKS_PER_SECOND;
                        Circuitmod.LOGGER.info("[CLIENT-DELEGATE] Reading mining speed: " + value + 
                            " blocks/sec (direct storage: " + clientSideMiningSpeed + ", calculated: " + 
                            (miningSpeed * TICKS_PER_SECOND) + ")");
                        return value;
                    } 
                    // On server side, calculate as normal
                    else {
                        int blocksPerSecond = miningSpeed * TICKS_PER_SECOND;
                        Circuitmod.LOGGER.info("[SERVER-DELEGATE] Reading mining speed: " + blocksPerSecond + 
                            " blocks/sec (from " + miningSpeed + " blocks/tick)");
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
                        Circuitmod.LOGGER.info("[CLIENT-DELEGATE] Setting energy received to: " + energyReceived);
                    }
                    break;
                case MINING_SPEED_INDEX:
                    // Convert back from blocks per second to blocks per tick
                    int newMiningSpeed = Math.max(0, value / TICKS_PER_SECOND);
                    
                    // Debug logging with client/server distinction
                    if (world != null && world.isClient()) {
                        Circuitmod.LOGGER.info("[CLIENT-DELEGATE] Setting mining speed: " + value + 
                            " blocks/sec -> " + newMiningSpeed + " blocks/tick (direct set)");
                        
                        // On client side, store the raw blocks/sec value directly
                        clientSideMiningSpeed = value;
                        
                        // Also update mining speed for consistency
                        if (miningSpeed != newMiningSpeed) {
                            miningSpeed = newMiningSpeed;
                            markDirty();
                        }
                    } else {
                        Circuitmod.LOGGER.info("[SERVER-DELEGATE] Setting mining speed: " + value + 
                            " blocks/sec -> " + newMiningSpeed + " blocks/tick");
                        
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
            Circuitmod.LOGGER.info("Initialized quarry mining area at " + pos);
        }
        
        // Safety check - validate that quarry's mining area exists
        if (blockEntity.currentPos == null) {
            Circuitmod.LOGGER.error("Quarry at " + pos + " has null mining position. Reinitializing mining area.");
            blockEntity.initializeMiningArea(pos, state);
            return;
        }
        
        // Debug current state
        Circuitmod.LOGGER.info("[SERVER] Quarry tick at " + pos + 
            " - Current mining speed: " + blockEntity.miningSpeed + 
            " blocks/tick, energy received: " + blockEntity.energyReceived);
        
        // If we're connected to a network, we'll get energy during the network's tick
        // Make sure to set our demand for the next tick
        blockEntity.energyDemand = MAX_ENERGY_DEMAND;
        
        boolean needsSync = false;
        
        // Process mining operations based on energy available
        if (blockEntity.energyReceived > 0) {
            // Calculate mining speed - each energy unit allows mining one block
            int newMiningSpeed = blockEntity.energyReceived / MINING_COST;
            if (newMiningSpeed != blockEntity.miningSpeed) {
                Circuitmod.LOGGER.info("[SERVER] Updating mining speed from " + blockEntity.miningSpeed + 
                    " to " + newMiningSpeed + " blocks/tick");
                blockEntity.miningSpeed = newMiningSpeed;
                needsSync = true;
            }
            
            // Log mining activity
            Circuitmod.LOGGER.info("[SERVER] Quarry at " + pos + " mining at speed: " + blockEntity.miningSpeed + 
                " blocks/tick, current position: " + blockEntity.currentPos);
            
            // Mine blocks based on mining speed
            int blocksMined = 0;
            for (int i = 0; i < blockEntity.miningSpeed; i++) {
                boolean mined = blockEntity.mineNextBlock(world);
                if (mined) {
                    blocksMined++;
                }
            }
            
            if (blocksMined > 0) {
                Circuitmod.LOGGER.info("[SERVER] Quarry at " + pos + " successfully mined " + blocksMined + " blocks");
                needsSync = true;
            }
        } else if (blockEntity.miningSpeed > 0) {
            // Set mining speed to 0 if no energy received
            Circuitmod.LOGGER.info("[SERVER] Resetting mining speed from " + blockEntity.miningSpeed + " to 0 (no energy)");
            blockEntity.miningSpeed = 0;
            needsSync = true;
        }
        
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
                    Circuitmod.LOGGER.info("[SERVER] Sending mining speed update packet: " + 
                        blocksPerSecond + " blocks/sec to clients");
                    
                    // Send to all players tracking the quarry block
                    for (ServerPlayerEntity player : PlayerLookup.tracking(serverWorld, pos)) {
                        ModNetworking.sendMiningSpeedUpdate(
                            player, blocksPerSecond, pos);
                    }
                    
                    // Update the last sent speed and reset cooldown
                    blockEntity.lastSentSpeed = blocksPerSecond;
                    blockEntity.packetCooldown = PACKET_COOLDOWN_MAX;
                } else {
                    Circuitmod.LOGGER.debug("[SERVER] Skipping packet send (same speed or cooldown): " + 
                        blocksPerSecond + " blocks/sec, cooldown: " + blockEntity.packetCooldown);
                }
            }
            
            // Also do the normal block update
            world.updateListeners(pos, state, state, 3);
        }
        
        // Reset energy received for next tick - energy will be provided by the network
        // during its tick cycle via the consumeEnergy method
        blockEntity.energyReceived = 0;
    }
    
    // Initialize the mining area based on the quarry's position and facing direction
    private void initializeMiningArea(BlockPos pos, BlockState state) {
        // Get the facing direction from the block state using HorizontalFacingBlock.FACING
        Direction direction = null;
        try {
            // Get the facing direction directly from the block state
            direction = state.get(net.minecraft.block.HorizontalFacingBlock.FACING);
            Circuitmod.LOGGER.info("Quarry at " + pos + " got facing direction from state: " + direction);
        } catch (Exception e) {
            Circuitmod.LOGGER.warn("Could not get facing direction from state, defaulting to NORTH: " + e.getMessage());
        }
        
        // If we couldn't get direction, default to NORTH
        if (direction == null) {
            direction = Direction.NORTH;
            Circuitmod.LOGGER.warn("Defaulting quarry direction to NORTH");
        }
        
        this.facingDirection = direction;
        
        Circuitmod.LOGGER.info("Quarry at " + pos + " initialized with facing direction: " + facingDirection);
        
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
            Circuitmod.LOGGER.warn("Mining area would include quarry - adjusting to be safer");
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
        
        // This logging is just for debugging
        Circuitmod.LOGGER.info("Initialized mining area for quarry at " + pos);
        Circuitmod.LOGGER.info("Mining direction: " + facingDirection);
        Circuitmod.LOGGER.info("Mining area size: " + areaSize + "x" + areaSize);
        Circuitmod.LOGGER.info("Mining area bounds: X[" + minX + "-" + maxX + "], Z[" + minZ + "-" + maxZ + "]");
        Circuitmod.LOGGER.info("Mining starting Y level: " + currentY);
        Circuitmod.LOGGER.info("Initial mining position: " + currentPos);
    }
    
    // Helper method to check if a position is inside a rectangular area
    private boolean isPositionInArea(BlockPos pos, int minX, int maxX, int minZ, int maxZ) {
        return pos.getX() >= minX && pos.getX() <= maxX && 
               pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }
    
    // Mining logic to get the next position to mine
    private BlockPos getNextMiningPos() {
        if (currentPos == null || startPos == null || facingDirection == null) {
            Circuitmod.LOGGER.warn("Missing position data for mining");
            return null;
        }
        
        // Create the new position at the current Y level
        BlockPos miningPos = new BlockPos(currentPos.getX(), currentY, currentPos.getZ());
        
        // Calculate the safe starting position (2 blocks in front of quarry)
        BlockPos safeAreaStart = startPos.offset(facingDirection, 2);
        
        // Calculate 3x3 mining area bounds centered on the safe start position
        int minX = safeAreaStart.getX() - 1;
        int maxX = safeAreaStart.getX() + 1;
        int minZ = safeAreaStart.getZ() - 1;
        int maxZ = safeAreaStart.getZ() + 1;
        
        // Debug log of current mining position and bounds
        Circuitmod.LOGGER.debug("Quarry at " + pos + " checking mining position: " + miningPos);
        
        // Check if the current position is somehow equal to the quarry or in safe zone
        if (miningPos.equals(pos) || isInSafeZone(miningPos)) {
            Circuitmod.LOGGER.info("Current position " + miningPos + " is in safe zone, advancing to next position");
            
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
        } else {
            // Store the current mining position before advancing
            BlockPos returnPos = miningPos;
            
            // Advance to the next position for the next mining operation
            advanceToNextPosition(minX, maxX, minZ, maxZ);
            
            return returnPos;
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
                    Circuitmod.LOGGER.info("Quarry at " + pos + " moving down to Y level: " + nextY);
                }
            }
            
            // Check if this position is safe
            BlockPos nextPos = new BlockPos(nextX, nextY, nextZ);
            if (!nextPos.equals(pos) && !isInSafeZone(nextPos)) {
                // Found a safe position
                foundSafePosition = true;
            } else {
                Circuitmod.LOGGER.debug("Position " + nextPos + " is in safe zone, trying next position");
            }
        }
        
        // If we couldn't find a safe position after multiple attempts, log warning
        if (!foundSafePosition) {
            Circuitmod.LOGGER.warn("Could not find a safe mining position after " + safetyCounter + 
                " attempts. Using last computed position anyway.");
        }
        
        // Update the current position and Y level
        currentPos = new BlockPos(nextX, currentPos.getY(), nextZ);
        currentY = nextY; // Make sure to update the Y level separately
    }
    
    // Mining logic to mine a block at the current position
    private boolean mineNextBlock(World world) {
        // Get the next block position to mine
        BlockPos miningPos = getNextMiningPos();
        if (miningPos == null) {
            return false;
        }
        
        // Skip if it's the quarry itself, a safe zone block, air or bedrock
        if (miningPos.equals(pos) || isInSafeZone(miningPos)) {
            Circuitmod.LOGGER.info("Skipping mining at " + miningPos + " (quarry or safe zone)");
            return false;
        }
        
        BlockState blockState = world.getBlockState(miningPos);
        if (blockState.isAir() || blockState.getHardness(world, miningPos) < 0) {
            Circuitmod.LOGGER.debug("Skipping air or bedrock at " + miningPos);
            return false;
        }
        
        // Skip if the block is a block entity that's part of our network
        BlockEntity targetEntity = world.getBlockEntity(miningPos);
        if (targetEntity instanceof IPowerConnectable) {
            Circuitmod.LOGGER.info("Skipping power network component at " + miningPos);
            return false;
        }
        
        // Get drops from the block
        ItemStack minedItem = new ItemStack(blockState.getBlock().asItem());
        
        // Add to inventory if there's space
        boolean addedToInventory = false;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.get(i);
            if (stack.isEmpty()) {
                inventory.set(i, minedItem);
                addedToInventory = true;
                break;
            } else if (ItemStack.areItemsEqual(stack, minedItem) && stack.getCount() < stack.getMaxCount()) {
                stack.increment(1);
                addedToInventory = true;
                break;
            }
        }
        
        // If we successfully added to the inventory, remove the block
        if (addedToInventory) {
            world.removeBlock(miningPos, false);
            return true;
        }
        
        return false;
    }
    
    // Check if the position is in the safe zone around the quarry
    private boolean isInSafeZone(BlockPos targetPos) {
        // First, check if it's the quarry itself
        if (targetPos.equals(pos)) {
            Circuitmod.LOGGER.error("Position " + targetPos + " is the quarry itself!");
            return true;
        }
        
        // Define a safe zone that includes the quarry itself and adjacent blocks
        for (Direction dir : Direction.values()) {
            BlockPos adjacentPos = pos.offset(dir);
            if (targetPos.equals(adjacentPos)) {
                Circuitmod.LOGGER.debug("Position " + targetPos + " is adjacent to quarry in direction " + dir);
                return true; // Position is adjacent to the quarry
            }
            
            // Also check diagonals by combining horizontal directions
            if (dir.getAxis().isHorizontal()) {
                for (Direction dir2 : Direction.values()) {
                    if (dir2.getAxis().isHorizontal() && dir != dir2 && dir != dir2.getOpposite()) {
                        BlockPos diagonalPos = pos.offset(dir).offset(dir2);
                        if (targetPos.equals(diagonalPos)) {
                            Circuitmod.LOGGER.debug("Position " + targetPos + " is diagonally adjacent to quarry");
                            return true; // Position is diagonally adjacent
                        }
                    }
                }
            }
        }
        
        // Also consider the block the quarry is placed on and the block above
        if (targetPos.equals(pos.up()) || targetPos.equals(pos.down())) {
            Circuitmod.LOGGER.debug("Position " + targetPos + " is above or below quarry");
            return true;
        }
        
        // Additional safety: consider blocks immediately in front of the quarry in its facing direction
        if (facingDirection != null) {
            BlockPos inFront = pos.offset(facingDirection);
            if (targetPos.equals(inFront)) {
                Circuitmod.LOGGER.debug("Position " + targetPos + " is immediately in front of quarry");
                return true;
            }
        }
        
        // Also check if it's a power cable or other network component
        if (world != null && !world.isClient()) {
            BlockEntity targetEntity = world.getBlockEntity(targetPos);
            if (targetEntity instanceof IPowerConnectable) {
                Circuitmod.LOGGER.debug("Position " + targetPos + " contains a power network component");
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
        // Safety check - don't allow setting null network if we already have one
        if (network == null && this.network != null) {
            Circuitmod.LOGGER.warn("Quarry at " + pos + " - Attempt to set null network when already connected to " + 
                this.network.getNetworkId() + ". This might indicate a network validation issue.");
            return;
        }
        
        // If we're changing networks, log it
        if (this.network != null && network != null && this.network != network) {
            Circuitmod.LOGGER.info("Quarry at " + pos + " changing networks from " + 
                this.network.getNetworkId() + " to " + network.getNetworkId());
        } else if (network != null && this.network == null) {
            Circuitmod.LOGGER.info("Quarry at " + pos + " connected to network " + network.getNetworkId());
        }
        
        this.network = network;
        
        // Initialize mining area if not already done
        if (startPos == null && world != null) {
            initializeMiningArea(pos, world.getBlockState(pos));
        }
    }
    
    @Override
    public int consumeEnergy(int energyOffered) {
        // Only consume energy up to our demand
        int energyToConsume = Math.min(energyDemand, energyOffered);
        
        // Track the received energy for this tick
        if (energyToConsume > 0) {
            Circuitmod.LOGGER.info("[SERVER] Quarry at " + pos + " offered " + energyOffered + 
                " energy, consuming " + energyToConsume + " (demand: " + energyDemand + ")");
                
            this.energyReceived += energyToConsume;
            
            // Calculate mining speed immediately - each energy unit allows mining one block
            int oldMiningSpeed = this.miningSpeed;
            this.miningSpeed = this.energyReceived / MINING_COST;
            
            if (oldMiningSpeed != this.miningSpeed) {
                Circuitmod.LOGGER.info("[SERVER] Energy consumption updated mining speed from " + 
                    oldMiningSpeed + " to " + this.miningSpeed + " blocks/tick");
            }
            
            // Mark dirty to ensure state is saved and client is updated
            markDirty();
            
            // Force a block update to clients if mining speed changed
            if (oldMiningSpeed != this.miningSpeed && world != null && !world.isClient()) {
                // Calculate blocks per second for network updates
                int blocksPerSecond = this.miningSpeed * TICKS_PER_SECOND;
                
                // Debug packet throttling
                int packetSendDelay = Math.max(0, packetCooldown);
                
                // Only send packet if it's different from the last one or cooldown is done
                if (blocksPerSecond != lastSentSpeed || packetCooldown <= 0) {
                    Circuitmod.LOGGER.info("[SERVER] Energy update sending speed: " + blocksPerSecond + 
                        " blocks/sec to clients (last: " + lastSentSpeed + ", cooldown: " + packetCooldown + ")");
                
                    // Send mining speed updates to all tracking players
                    if (world instanceof ServerWorld serverWorld) {
                        for (ServerPlayerEntity player : PlayerLookup.tracking(serverWorld, pos)) {
                            ModNetworking.sendMiningSpeedUpdate(player, blocksPerSecond, pos);
                        }
                    }
                    
                    // Update the last sent speed and reset cooldown
                    lastSentSpeed = blocksPerSecond;
                    packetCooldown = PACKET_COOLDOWN_MAX;
                } else {
                    Circuitmod.LOGGER.debug("[SERVER] Skipping packet in energy update (cooldown: " + 
                        packetCooldown + ")");
                }
                
                // Also do the normal block update for other data
                BlockState state = world.getBlockState(pos);
                world.updateListeners(pos, state, state, 3);
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
            Circuitmod.LOGGER.debug("Quarry at " + pos + " received sync event type " + type + " with data " + data);
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
        Circuitmod.LOGGER.info("[CLIENT] Setting mining speed from network packet: " + blocksPerSecond + " blocks/sec");
        // Store the blocks per second value directly
        propertyDelegate.set(MINING_SPEED_INDEX, blocksPerSecond);
    }
    
    /**
     * Gets the current mining speed in blocks per second
     * @return the mining speed
     */
    public int getMiningSpeed() {
        return propertyDelegate.get(MINING_SPEED_INDEX);
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