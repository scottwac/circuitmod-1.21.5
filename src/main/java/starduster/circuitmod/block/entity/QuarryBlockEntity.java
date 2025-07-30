package starduster.circuitmod.block.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.machines.BloomeryBlock;
import starduster.circuitmod.block.machines.QuarryBlock;
import starduster.circuitmod.network.ModNetworking;
import starduster.circuitmod.power.EnergyNetwork;
import starduster.circuitmod.power.IEnergyConsumer;
import starduster.circuitmod.power.IPowerConnectable;
import starduster.circuitmod.screen.ModScreenHandlers;
import starduster.circuitmod.screen.QuarryScreenHandler;
import net.minecraft.block.Blocks;

import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.sound.ModSounds;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class QuarryBlockEntity extends BlockEntity implements SidedInventory, NamedScreenHandlerFactory, ExtendedScreenHandlerFactory<ModScreenHandlers.QuarryData>, IEnergyConsumer {
    // Energy properties
    private static final int MAX_ENERGY_DEMAND = 3600; // Maximum energy demand per tick (allows instant mining of most blocks)
    private int energyDemand = MAX_ENERGY_DEMAND; // Current energy demand per tick
    private int energyReceived = 0; // Energy received this tick
    private EnergyNetwork network;
    
    // Mining properties
    private int currentBlockEnergyCost = 1; // Energy cost for the current block being mined
    private boolean miningEnabled = false; // Whether mining is enabled or disabled
    
    // Mining progress tracking
    private BlockPos currentMiningPos = null; // Current block being mined
    private int currentMiningProgress = 0; // Progress on current block (0-100)
    private int totalMiningTicks = 0; // Total ticks needed to mine current block
    private int currentMiningTicks = 0; // Current ticks spent mining
    
    // Area properties
    
    private BlockPos startPos; // Starting corner of the mining area
    private BlockPos currentPos; // Current mining position
    private int currentY; // Current mining Y level
    private Direction facingDirection; // Direction the quarry mines (same as visual facing)
    private int miningWidth = 16; // Width of the mining area (X direction)
    private int miningLength = 16; // Length of the mining area (Z direction)
    
    // Mining area bounds (calculated once and reused)
    private int miningAreaMinX;
    private int miningAreaMaxX;
    private int miningAreaMinZ;
    private int miningAreaMaxZ;
    
    // Track recently mined positions to prevent getting stuck on fluid-created blocks
    private final Set<BlockPos> recentlyMinedPositions = new HashSet<>();
    private static final int RECENTLY_MINED_TIMEOUT = 20; // Skip recently mined positions for 20 ticks (1 second)
    private int recentlyMinedCleanupCounter = 0;
    
    // Debug logging control - set to true only when debugging
    private static final boolean DEBUG_LOGGING = true;
    
    // Networking properties
    private int packetCooldown = 0; // Cooldown to avoid sending too many packets
    private static final int PACKET_COOLDOWN_MAX = 10; // Only send packets every 10 ticks max (0.5 seconds)
    
    // Property delegate indices
    private static final int ENERGY_RECEIVED_INDEX = 0;
    private static final int MINING_ENABLED_INDEX = 1;
    private static final int PROPERTY_COUNT = 2;
    
    // Inventory with custom size (12 slots - 3x4 grid)
    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(12, ItemStack.EMPTY);
    
    // Property delegate for GUI synchronization
    private final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            switch (index) {
                case ENERGY_RECEIVED_INDEX:
                    return energyReceived;
                case MINING_ENABLED_INDEX:
                    return miningEnabled ? 1 : 0;
                default:
                    return 0;
            }
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case ENERGY_RECEIVED_INDEX:
                    energyReceived = value;
                    break;
                case MINING_ENABLED_INDEX:
                    boolean newMiningEnabled = value == 1;
                    if (miningEnabled != newMiningEnabled) {
                        miningEnabled = newMiningEnabled;
                        markDirty();
                    }
                    break;
            }
        }

        @Override
        public int size() {
            return PROPERTY_COUNT;
        }
    };

    // Add this field to the class
    private boolean needsNetworkRefresh = false;

    public QuarryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QUARRY_BLOCK_ENTITY, pos, state);
        
        // Initialize mining area immediately if we have the necessary data
        if (pos != null && state != null) {
            // Circuitmod.LOGGER.info("[CONSTRUCTOR] Initializing mining area at {}", pos);
            initializeMiningArea(pos, state);
        }
    }

    // Save data to NBT when the block is saved
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        
        // Save mining and energy data
        nbt.putInt("energy_demand", this.energyDemand);
        nbt.putInt("energy_received", this.energyReceived);
        nbt.putInt("current_block_energy_cost", this.currentBlockEnergyCost);
        nbt.putBoolean("mining_enabled", this.miningEnabled);
        nbt.putInt("mining_width", this.miningWidth);
        nbt.putInt("mining_length", this.miningLength);
        
        // Save mining area bounds
        nbt.putInt("mining_area_min_x", this.miningAreaMinX);
        nbt.putInt("mining_area_max_x", this.miningAreaMaxX);
        nbt.putInt("mining_area_min_z", this.miningAreaMinZ);
        nbt.putInt("mining_area_max_z", this.miningAreaMaxZ);
        
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
        this.currentBlockEnergyCost = nbt.getInt("current_block_energy_cost").orElse(1);
        this.miningEnabled = nbt.getBoolean("mining_enabled").orElse(false);
        this.miningWidth = nbt.getInt("mining_width").orElse(16);
        this.miningLength = nbt.getInt("mining_length").orElse(16);
        
        // Load mining area data
        if (nbt.contains("mining_area_min_x") && nbt.contains("mining_area_max_x") && nbt.contains("mining_area_min_z") && nbt.contains("mining_area_max_z")) {
            this.miningAreaMinX = nbt.getInt("mining_area_min_x").orElse(0);
            this.miningAreaMaxX = nbt.getInt("mining_area_max_x").orElse(0);
            this.miningAreaMinZ = nbt.getInt("mining_area_min_z").orElse(0);
            this.miningAreaMaxZ = nbt.getInt("mining_area_max_z").orElse(0);
        }
        
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
        needsNetworkRefresh = true;
        
        // Initialize mining area on client side if not already done
        if (world != null && world.isClient() && startPos == null) {
            // Circuitmod.LOGGER.info("[CLIENT-NBT] Initializing mining area from NBT at {}", pos);
            initializeMiningArea(pos, world.getBlockState(pos));
        }
        
        // Ensure mining area is properly initialized on both client and server
        if (startPos == null && pos != null && world != null) {
            // Circuitmod.LOGGER.info("[SERVER-NBT] Initializing mining area from NBT at {}", pos);
            initializeMiningArea(pos, world.getBlockState(pos));
        }
    }

    private int soundClock = 0;
    // The tick method called by the ticker in QuarryBlock
    public void tick(World world, BlockPos pos, BlockState state, QuarryBlockEntity blockEntity) {
        boolean runningBefore = blockEntity.isMiningEnabled();

//        if(soundClock > 0 && isMiningEnabled() == false) {
//
//        }
        if(isMiningEnabled()) {
            if(soundClock <= 0){
                soundClock = 160;
            }
            if(soundClock == 160) {
                world.playSound(null, pos, ModSounds.MINER_MACHINE_RUN, SoundCategory.BLOCKS, 1F, 1F);
            }
            soundClock = soundClock - 1;
            world.setBlockState(pos, world.getBlockState(pos).with(QuarryBlock.RUNNING, true), Block.NOTIFY_ALL);
        }
        if(!isMiningEnabled()) {
            world.setBlockState(pos, world.getBlockState(pos).with(QuarryBlock.RUNNING, false), Block.NOTIFY_ALL);
            soundClock = 160;
        }
        
        // Condense inventory every 20 ticks (once per second) to prevent scattered items
        if (world.getTime() % 20 == 0) {
            blockEntity.condenseInventory();
        }
        
        // Clean up recently mined positions every 20 ticks to prevent memory buildup
        if (world.getTime() % 20 == 0) {
            blockEntity.recentlyMinedPositions.clear();
        }


        // Handle network refresh with retry logic for world reload scenarios
        if (blockEntity.needsNetworkRefresh) {
            boolean networkFound = blockEntity.findAndJoinNetwork();
            if (networkFound) {
                blockEntity.needsNetworkRefresh = false;
                Circuitmod.LOGGER.info("[QUARRY-NETWORK] Successfully joined network at " + pos);
            } else {
                // If we couldn't find a network, try again in a few ticks
                // This helps with world reload scenarios where power cables might not be loaded yet
                if (world.getTime() % 20 == 0) { // Log every second
                    Circuitmod.LOGGER.info("[QUARRY-NETWORK] No network found at " + pos + ", will retry. Mining enabled: " + blockEntity.miningEnabled);
                }
            }
        }
        if (world.isClient()) {
            return;
        }

        // Initialize mining area if not set (this should happen first)
        if (blockEntity.startPos == null) {
            // Circuitmod.LOGGER.info("[TICK] Initializing mining area at {}", pos);
            blockEntity.initializeMiningArea(pos, state);
        }
        
        // Safety check - validate that quarry's mining area exists
        if (blockEntity.currentPos == null) {
            // Circuitmod.LOGGER.info("[TICK] Re-initializing mining area at {} (currentPos was null)", pos);
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
            // Circuitmod.LOGGER.info("[QUARRY-TICK] Energy received: " + blockEntity.energyReceived + ", network: " + networkInfo);
        }
        
        // Process mining operations based on energy available and if mining is enabled
        if (blockEntity.energyReceived > 0 && blockEntity.miningEnabled) {
            // Try to mine the current block (this will handle gradual mining)
            boolean mined = blockEntity.mineNextBlock(world);
            
            if (mined) {
                needsSync = true;
            }
        } else {
            // No energy received or mining is disabled
            needsSync = true;
            
            // If mining is enabled but we're not receiving power, try to refresh network connection
            // This helps with world reload scenarios where network connections might be lost
            if (blockEntity.miningEnabled && blockEntity.energyReceived == 0 && blockEntity.network == null) {
                if (world.getTime() % 40 == 0) { // Try every 2 seconds
                    Circuitmod.LOGGER.info("[QUARRY-NETWORK-RETRY] Mining enabled but no power at " + pos + ", attempting network refresh");
                    blockEntity.needsNetworkRefresh = true;
                }
            }
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
            
            // Do the normal block update
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
        
        // Send mining area bounds to clients for rendering (every 20 ticks to avoid spam)
        if (world instanceof ServerWorld serverWorld && world.getTime() % 20 == 0) {
            for (ServerPlayerEntity player : PlayerLookup.tracking(serverWorld, pos)) {
                ModNetworking.sendQuarryDimensionsSync(player, blockEntity.miningWidth, blockEntity.miningLength, pos);
            }
        }
        
        // Send initial sync to new players (every 100 ticks to avoid spam)
        if (world instanceof ServerWorld serverWorld && world.getTime() % 100 == 0) {
            for (ServerPlayerEntity player : PlayerLookup.tracking(serverWorld, pos)) {
                // Send current mining enabled status
                ModNetworking.sendMiningEnabledStatus(player, blockEntity.miningEnabled, pos);
                // Send current dimensions
                ModNetworking.sendQuarryDimensionsSync(player, blockEntity.miningWidth, blockEntity.miningLength, pos);
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
        
        // INVERT: Use the opposite direction for mining
        this.facingDirection = direction.getOpposite();
        
        // Set start position to the block in front of the quarry to exclude the quarry itself
        this.startPos = pos.offset(this.facingDirection);
        
        // Calculate the mining area bounds
        // The quarry should be positioned at the corner of the mining area
        int minX, maxX, minZ, maxZ;
        
        // Calculate mining area bounds based on the facing direction
        // The area starts from the front position (one block in front of quarry)
        if (this.facingDirection == Direction.NORTH) {
            // Forward=north, right=east: extend forward(north) and right(east) from startPos
            minX = this.startPos.getX();
            maxX = this.startPos.getX() + miningWidth - 1;
            minZ = this.startPos.getZ() - miningLength + 1;
            maxZ = this.startPos.getZ();
        } else if (this.facingDirection == Direction.SOUTH) {
            // Forward=south, right=west: extend forward(south) and right(west) from startPos  
            minX = this.startPos.getX() - miningWidth + 1;
            maxX = this.startPos.getX();
            minZ = this.startPos.getZ();
            maxZ = this.startPos.getZ() + miningLength - 1;
        } else if (this.facingDirection == Direction.EAST) {
            // Forward=east, right=south: extend forward(east) and right(south) from startPos
            minX = this.startPos.getX();
            maxX = this.startPos.getX() + miningLength - 1;
            minZ = this.startPos.getZ();
            maxZ = this.startPos.getZ() + miningWidth - 1;
        } else { // WEST
            // Forward=west, right=north: extend forward(west) and right(north) from startPos
            minX = this.startPos.getX() - miningLength + 1;
            maxX = this.startPos.getX();
            minZ = this.startPos.getZ() - miningWidth + 1;
            maxZ = this.startPos.getZ();
        }
        
        // Store the calculated bounds for reuse
        this.miningAreaMinX = minX;
        this.miningAreaMaxX = maxX;
        this.miningAreaMinZ = minZ;
        this.miningAreaMaxZ = maxZ;
        
        // Debug logging to see the actual mining area
        Circuitmod.LOGGER.info("[QUARRY-AREA] Calculated mining area: {}x{} starting from front position {} (quarry at {}) with bounds X:{} to {}, Z:{} to {} (facing: {})", 
            miningWidth, miningLength, this.startPos, pos, minX, maxX, minZ, maxZ, this.facingDirection);
        
        // Start at the corner of the mining area (furthest from front position)
        if (this.facingDirection == Direction.NORTH) {
            this.currentPos = new BlockPos(minX, this.startPos.getY(), minZ);
        } else if (this.facingDirection == Direction.SOUTH) {
            this.currentPos = new BlockPos(minX, this.startPos.getY(), maxZ);
        } else if (this.facingDirection == Direction.EAST) {
            this.currentPos = new BlockPos(maxX, this.startPos.getY(), minZ);
        } else { // WEST
            this.currentPos = new BlockPos(minX, this.startPos.getY(), minZ);
        }
        
        // Start mining at the front position's Y level
        this.currentY = this.startPos.getY();
        
        // Mark dirty to trigger client sync
        markDirty();
        
        // Circuitmod.LOGGER.info("[QUARRY-AREA] Initialized mining area: {}x{} at position {} with bounds X:{} to {}, Z:{} to {} (facing: {})", 
        //     miningWidth, miningLength, pos, minX, maxX, minZ, maxZ, this.facingDirection);
    }
    
    // Helper method to check if a position is inside a rectangular area
    private boolean isPositionInArea(BlockPos pos, int minX, int maxX, int minZ, int maxZ) {
        return pos.getX() >= minX && pos.getX() <= maxX && 
               pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }
    
    // Mining logic to mine a block at the current position
    private boolean mineNextBlock(World world) {
        // If mining is disabled, do nothing
        if (!miningEnabled) {
            advanceToNextBlock();
            return false;
        }

        // Get the next mining position
        if (currentMiningPos == null) {
            currentMiningPos = getNextMiningPos();
            if (currentMiningPos == null) {
                // No more positions to mine - quarry has completed its area
                if (DEBUG_LOGGING && world.getTime() % 20 == 0) {
                    Circuitmod.LOGGER.info("[QUARRY-MINING] No more positions to mine - quarry completed");
                }
                return false;
            }
            currentMiningProgress = 0;
            currentMiningTicks = 0;
            totalMiningTicks = 0;
            
            // Only log if debug logging is enabled
            if (DEBUG_LOGGING && world.getTime() % 20 == 0) {
                Circuitmod.LOGGER.info("[QUARRY-MINING] Starting to mine at {}", currentMiningPos);
            }
        }
        
        // Proactively find the next mineable block
        BlockPos mineablePos = findNextMineableBlock(world);
        if (mineablePos == null) {
            // No mineable blocks found in the entire area
            if (DEBUG_LOGGING && world.getTime() % 20 == 0) {
                Circuitmod.LOGGER.info("[QUARRY-MINING] No mineable blocks found in area - quarry completed");
            }
            return false;
        }
        
        // Update currentMiningPos to the mineable position we found
        currentMiningPos = mineablePos;
        
        // Check if we can mine this block
        BlockState blockState = world.getBlockState(currentMiningPos);
        
        // Calculate energy cost for this block based on hardness
        float hardness = blockState.getBlock().getHardness();
        int energyCost = Math.max(1, (int)(hardness * 100)); // 100 energy per hardness point
        
        // Check if we have enough energy to mine this block
        if (energyReceived >= energyCost) {
            // Mine the block instantly
            boolean success = mineBlock(world, currentMiningPos);
            if (success) {
                // Consume the energy cost
                energyReceived -= energyCost;
                
                // Only log if debug logging is enabled
                if (DEBUG_LOGGING && world.getTime() % 20 == 0) {
                    Circuitmod.LOGGER.info("[QUARRY-MINING] Successfully mined block at {} (cost: {} energy, remaining: {} energy)", 
                        currentMiningPos, energyCost, energyReceived);
                }
                advanceToNextBlock();
                return true;
            } else {
                // Mining failed (likely inventory full), try next block
                advanceToNextBlock();
                return mineNextBlock(world); // Recursively try the next block
            }
        } else {
            // Not enough energy to mine this block yet
            // Only log if debug logging is enabled
            if (DEBUG_LOGGING && world.getTime() % 20 == 0) {
                Circuitmod.LOGGER.info("[QUARRY-MINING] Not enough energy to mine block at {} (need: {} energy, have: {} energy)", 
                    currentMiningPos, energyCost, energyReceived);
            }
            return false; // Wait for more energy
        }
    }
    
    /**
     * Proactively finds the next mineable block in the mining area.
     * This method searches ahead to find a block that can actually be mined,
     * skipping air, fluids, and unmineable blocks.
     */
    private BlockPos findNextMineableBlock(World world) {
        if (currentPos == null || startPos == null || facingDirection == null) {
            return null;
        }
        
        // Use the stored mining area bounds
        int minX = this.miningAreaMinX;
        int maxX = this.miningAreaMaxX;
        int minZ = this.miningAreaMinZ;
        int maxZ = this.miningAreaMaxZ;
        
        // Check if we've reached the minimum Y level (bedrock level)
        if (currentY < -64) {
            Circuitmod.LOGGER.info("[QUARRY-MINING] Reached minimum Y level ({}), quarry completed", currentY);
            return null; // Quarry has completed mining the entire area
        }
        
        // Start from the current position and search for a mineable block
        BlockPos searchPos = new BlockPos(currentPos.getX(), currentY, currentPos.getZ());
        int searchX = searchPos.getX();
        int searchZ = searchPos.getZ();
        int searchY = currentY;
        
        // Search through the entire mining area
        while (searchY >= -64) {
            while (searchZ <= maxZ) {
                while (searchX <= maxX) {
                    searchPos = new BlockPos(searchX, searchY, searchZ);
                    
                    // Skip the quarry position itself
                    if (searchPos.equals(pos)) {
                        searchX++;
                        continue;
                    }
                    
                    // Check if this position is within the mining area
                    if (!isPositionInArea(searchPos, minX, maxX, minZ, maxZ)) {
                        searchX++;
                        continue;
                    }
                    
                    // Check if the position has been recently mined
                    if (recentlyMinedPositions.contains(searchPos)) {
                        if (DEBUG_LOGGING && world.getTime() % 20 == 0) {
                            Circuitmod.LOGGER.info("[QUARRY-MINING] Skipping recently mined position at {}", searchPos);
                        }
                        searchX++;
                        continue;
                    }

                    // Check the block at this position
                    BlockState blockState = world.getBlockState(searchPos);
                    
                    // Skip air blocks
                    if (blockState.isAir()) {
                        searchX++;
                        continue;
                    }
                    
                    // Handle fluid blocks (water, lava, etc.)
                    if (blockState.getBlock() instanceof FluidBlock) {
                        // Remove water and lava blocks
                        removeFluidBlocks(world, searchPos);
                        searchX++;
                        continue;
                    }
                    
                    // Check if we can mine this block
                    if (canMineBlock(blockState)) {
                        // Found a mineable block!
                        return searchPos;
                    }
                    
                    // Cannot mine this block, try next position
                    searchX++;
                }
                
                // Move to next Z row
                searchX = minX;
                searchZ++;
            }
            
            // Move to next Y level
            searchZ = minZ;
            searchY--;
            
            // Log when we move down to a new layer
            Circuitmod.LOGGER.info("[QUARRY-MINING] Completed layer at Y={}, moving down to Y={}", searchY + 1, searchY);
        }
        
        // No mineable blocks found in the entire area
        return null;
    }
    
    // Helper method to advance to the next block
    private void advanceToNextBlock() {
        currentMiningPos = null;
        currentMiningProgress = 0;
        totalMiningTicks = 0;
        currentMiningTicks = 0;
        
        // Advance the position for the next block
        if (currentPos != null && startPos != null && facingDirection != null) {
            // Use the stored mining area bounds instead of hardcoded 3x3
            advanceToNextPosition(miningAreaMinX, miningAreaMaxX, miningAreaMinZ, miningAreaMaxZ);
        }
    }
    
    // Mining logic to get the next position to mine
    private BlockPos getNextMiningPos() {
        if (currentPos == null || startPos == null || facingDirection == null) {
            return null;
        }
        
        // Use the stored mining area bounds instead of recalculating
        int minX = this.miningAreaMinX;
        int maxX = this.miningAreaMaxX;
        int minZ = this.miningAreaMinZ;
        int maxZ = this.miningAreaMaxZ;
        
        // Check if we've reached the minimum Y level (bedrock level)
        // Bedrock is typically at Y=0, but we'll use Y=-64 for safety
        if (currentY < -64) {
            Circuitmod.LOGGER.info("[QUARRY-MINING] Reached minimum Y level ({}), quarry completed", currentY);
            return null; // Quarry has completed mining the entire area
        }
        
        // Create the position at the current Y level
        BlockPos miningPos = new BlockPos(currentPos.getX(), currentY, currentPos.getZ());
        
        // Check if the current position is somehow equal to the quarry
        if (miningPos.equals(pos)) {
            // Move to the next position 
            advanceToNextPosition(minX, maxX, minZ, maxZ);
            
            // Try again with the new position, but do it in a loop to avoid stack overflow
            int attempts = 0;
            while (currentPos.equals(pos) && attempts < 10) {
                attempts++;
                advanceToNextPosition(minX, maxX, minZ, maxZ);
            }
            
            // Return the new position after advancing
            miningPos = new BlockPos(currentPos.getX(), currentY, currentPos.getZ());
        }
        
        // Additional check: if we're still at the quarry position after advancing, skip it
        if (miningPos.equals(pos)) {
            // Skip the quarry position entirely
            advanceToNextPosition(minX, maxX, minZ, maxZ);
            miningPos = new BlockPos(currentPos.getX(), currentY, currentPos.getZ());
        }
        
        // Final check: if we've advanced past the mining area bounds, we're done
        if (currentPos.getX() > maxX || currentPos.getZ() > maxZ) {
            Circuitmod.LOGGER.info("[QUARRY-MINING] Advanced past mining area bounds, quarry completed");
            return null; // Quarry has completed mining the entire area
        }
        
        return miningPos;
    }
    
    // Helper method to advance to the next position
    private void advanceToNextPosition(int minX, int maxX, int minZ, int maxZ) {
        int nextX = currentPos.getX();
        int nextZ = currentPos.getZ();
        int nextY = currentY;
        
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
                
                // Log when we move down to a new layer
                Circuitmod.LOGGER.info("[QUARRY-MINING] Completed layer at Y={}, moving down to Y={}", currentY, nextY);
            }
        }
        
        // Update the position
        currentPos = new BlockPos(nextX, currentPos.getY(), nextZ);
        currentY = nextY;
    }
    

    
    // Check if the inventory is full
    private boolean isInventoryFull() {
        for (ItemStack stack : inventory) {
            if (stack.isEmpty()) {
                return false; // Found an empty slot
            }
        }
        return true; // All slots are occupied
    }
    
    /**
     * Condenses the inventory by combining multiple slots with the same item type.
     * This prevents the inventory from filling up with scattered items.
     */
    private void condenseInventory() {
        boolean inventoryChanged = false;
        
        // First pass: try to combine items into existing stacks
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack currentStack = inventory.get(i);
            if (currentStack.isEmpty()) continue;
            
            // Try to add this stack to any existing stack of the same type
            for (int j = 0; j < inventory.size(); j++) {
                if (i == j) continue; // Don't combine with itself
                
                ItemStack targetStack = inventory.get(j);
                if (targetStack.isEmpty()) continue;
                
                // Check if we can combine these stacks
                if (ItemStack.areItemsEqual(currentStack, targetStack)) {
                    int maxCount = Math.min(currentStack.getMaxCount(), getMaxCountPerStack());
                    int spaceInTarget = maxCount - targetStack.getCount();
                    
                    if (spaceInTarget > 0) {
                        int transferAmount = Math.min(spaceInTarget, currentStack.getCount());
                        targetStack.increment(transferAmount);
                        currentStack.decrement(transferAmount);
                        
                        // Update the inventory
                        inventory.set(j, targetStack);
                        if (currentStack.isEmpty()) {
                            inventory.set(i, ItemStack.EMPTY);
                        } else {
                            inventory.set(i, currentStack);
                        }
                        
                        inventoryChanged = true;
                        
                        // If we've moved all items from current stack, break
                        if (currentStack.isEmpty()) {
                            break;
                        }
                    }
                }
            }
        }
        
        // Second pass: move items to fill empty slots at the beginning
        if (inventoryChanged) {
            compactInventory();
        }
    }
    
    /**
     * Compacts the inventory by moving all non-empty stacks to the front,
     * leaving empty slots at the end.
     */
    private void compactInventory() {
        int writeIndex = 0;
        
        // Move all non-empty stacks to the front
        for (int readIndex = 0; readIndex < inventory.size(); readIndex++) {
            ItemStack stack = inventory.get(readIndex);
            if (!stack.isEmpty()) {
                if (readIndex != writeIndex) {
                    inventory.set(writeIndex, stack);
                    inventory.set(readIndex, ItemStack.EMPTY);
                }
                writeIndex++;
            }
        }
    }
    
    /**
     * Removes water and lava source or flowing blocks encountered in the mining area.
     * This method is called when the quarry encounters fluid blocks during mining.
     */
    private void removeFluidBlocks(World world, BlockPos miningPos) {
        BlockState blockState = world.getBlockState(miningPos);
        
        // Check if this is a water block (source or flowing)
        if (blockState.getBlock() == Blocks.WATER || blockState.getBlock() == Blocks.BUBBLE_COLUMN) {
            // Remove the water block without adding it to inventory
            world.removeBlock(miningPos, false);
            
            // Log the water removal for debugging
            Circuitmod.LOGGER.info("[QUARRY-FLUID] Removed water block at {}", miningPos);
        }
        
        // Check if this is a lava block (source or flowing)
        if (blockState.getBlock() == Blocks.LAVA) {
            // Remove the lava block without adding it to inventory
            world.removeBlock(miningPos, false);
            
            // Log the lava removal for debugging
            Circuitmod.LOGGER.info("[QUARRY-FLUID] Removed lava block at {}", miningPos);
        }
    }
    
    // Check if a block can be mined
    private boolean canMineBlock(BlockState blockState) {
        // Skip if it's the quarry itself
        if (currentMiningPos != null && currentMiningPos.equals(pos)) {
            return false;
        }
        
        // Skip bedrock or unbreakable blocks
        if (blockState.getHardness(world, currentMiningPos) < 0) {
            return false;
        }
        
        // Skip if the block is a block entity that's part of our network
        BlockEntity targetEntity = world.getBlockEntity(currentMiningPos);
        if (targetEntity instanceof IPowerConnectable) {
            return false;
        }
        
        return true;
    }
    
    // Mine a block at the specified position
    private boolean mineBlock(World world, BlockPos miningPos) {
        BlockState blockState = world.getBlockState(miningPos);
        ItemStack minedItem;
        
        // Debug logging to see what we're mining
        // Circuitmod.LOGGER.info("[QUARRY-MINE] Mining block {} at {}", blockState.getBlock().getName().getString(), miningPos);
        
        // Special handling for powdered snow - give snowballs instead of powdered snow bucket
        if (blockState.getBlock() == net.minecraft.block.Blocks.POWDER_SNOW) {
            minedItem = new ItemStack(net.minecraft.item.Items.SNOWBALL);
        } else {
            // Check if the block has a valid item - use a more robust check
            Item item = blockState.getBlock().asItem();
            if (item == net.minecraft.item.Items.AIR || item == null) {
                // This block doesn't have a valid item, skip it
                //   Circuitmod.LOGGER.info("[QUARRY-MINE] Block {} has no valid item, skipping", blockState.getBlock().getName().getString());
                world.removeBlock(miningPos, false);
                return true; // Successfully removed the block
            } else {
                minedItem = new ItemStack(item);
            }
        }
        
        // Debug logging to see what item we created
        Circuitmod.LOGGER.info("[QUARRY-MINE] Created item {} from block {}", minedItem.getItem().getName().getString(), blockState.getBlock().getName().getString());
        
        // Don't add Air items to inventory
        if (minedItem.getItem() == net.minecraft.item.Items.AIR) {
            Circuitmod.LOGGER.info("[QUARRY-MINE] Skipping Air item, not adding to inventory");
            world.removeBlock(miningPos, false);
            return true;
        }
        
        // Check if we have space in inventory
        boolean addedToInventory = false;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.get(i);
            if (stack.isEmpty()) {
                inventory.set(i, minedItem);
                addedToInventory = true;
                Circuitmod.LOGGER.info("[QUARRY-MINE] Added {} to inventory slot {}", minedItem.getItem().getName().getString(), i);
                break;
            } else if (ItemStack.areItemsEqual(stack, minedItem) && stack.getCount() < stack.getMaxCount()) {
                stack.increment(1);
                addedToInventory = true;
                Circuitmod.LOGGER.info("[QUARRY-MINE] Incremented {} in inventory slot {}", minedItem.getItem().getName().getString(), i);
                break;
            }
        }
        
        if (addedToInventory) {
            world.removeBlock(miningPos, false);
            // Add this position to recently mined to prevent getting stuck on fluid-created blocks
            recentlyMinedPositions.add(miningPos);
            return true;
        } else {
            // Inventory full - don't mine the block, pause position advancement and re-attempt later
            Circuitmod.LOGGER.info("[QUARRY-MINE] Inventory full, pausing mining of block {} - will re-attempt when space available", minedItem.getItem().getName().getString());
            return false; // Don't advance position, re-attempt the same block later
        }
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
            // Circuitmod.LOGGER.info("[QUARRY-NETWORK] Quarry at " + pos + " changing networks: " + this.network.getNetworkId() + " -> " + network.getNetworkId());
        } else if (network != null && this.network == null) {
            // Circuitmod.LOGGER.info("[QUARRY-NETWORK] Quarry at " + pos + " connecting to network: " + network.getNetworkId());
        } else if (this.network != null && network == null) {
            // Circuitmod.LOGGER.info("[QUARRY-NETWORK] Quarry at " + pos + " disconnecting from network: " + this.network.getNetworkId());
        }
        
        this.network = network;
        
        // Initialize mining area if not already done
        if (startPos == null && world != null) {
            initializeMiningArea(pos, world.getBlockState(pos));
        }
    }
    
    @Override
    public void setWorld(World world) {
        super.setWorld(world);
        
        // Initialize mining area when world is set (important for client-side loading)
        if (world != null && startPos == null && pos != null) {
            // Circuitmod.LOGGER.info("[SET-WORLD] Initializing mining area at {}", pos);
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
                // Circuitmod.LOGGER.info("[QUARRY-ENERGY] Energy offered: " + energyOffered + ", consumed: " + energyToConsume + ", accumulated: " + this.energyReceived);
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
            
            // Circuitmod.LOGGER.info("[CLIENT] Updated mining progress: " + miningProgress + "% at " + miningPos);
        }
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

    /**
     * Toggles the mining enabled state.
     * @param enabled The new enabled state.
     */
    public void setMiningEnabled(boolean enabled) {
        if (miningEnabled != enabled) {
            miningEnabled = enabled;
            // Circuitmod.LOGGER.info("[QUARRY-MINING] Mining enabled: " + enabled);
            markDirty();
        }
    }

    /**
     * Gets the current mining enabled state.
     * @return true if mining is enabled, false otherwise.
     */
    public boolean isMiningEnabled() {
        return miningEnabled;
    }
    
    /**
     * Toggles the mining enabled state and notifies clients.
     */
    public void toggleMining() {
        if (world == null || world.isClient()) {
            return;
        }
        
        miningEnabled = !miningEnabled;
        // Circuitmod.LOGGER.info("[QUARRY-TOGGLE] Mining toggled to: " + miningEnabled);
        markDirty();
        
        // Send status update to all players tracking this quarry
        if (world instanceof ServerWorld serverWorld) {
            for (ServerPlayerEntity player : PlayerLookup.tracking(serverWorld, pos)) {
                ModNetworking.sendMiningEnabledStatus(player, miningEnabled, pos);
            }
        }
    }
    
    /**
     * Sets the mining enabled state from a network packet.
     * This is called on the client side when a packet is received.
     * 
     * @param enabled Whether mining is enabled
     */
    public void setMiningEnabledFromNetwork(boolean enabled) {
        // Only update on client side
        if (world != null && world.isClient()) {
            this.miningEnabled = enabled;
            markDirty();
            // Circuitmod.LOGGER.info("[CLIENT] Updated mining enabled status: " + enabled);
        }
    }
    
    /**
     * Set the mining area dimensions
     * 
     * @param width The width of the mining area (X direction)
     * @param length The length of the mining area (Z direction)
     */
    public void setMiningDimensions(int width, int length) {
        // Validate dimensions
        width = Math.max(1, Math.min(100, width));
        length = Math.max(1, Math.min(100, length));
        
        if (this.miningWidth != width || this.miningLength != length) {
            this.miningWidth = width;
            this.miningLength = length;
            
            // Always reinitialize the mining area when dimensions change
            // This ensures the quarry updates immediately regardless of mining state
            this.initializeMiningArea(this.pos, this.getCachedState());
            
            // Reset current mining position to start from the beginning of the new area
            this.currentMiningPos = null;
            this.currentMiningProgress = 0;
            this.totalMiningTicks = 0;
            this.currentMiningTicks = 0;
            
            markDirty();
            // Circuitmod.LOGGER.info("[QUARRY-DIMENSIONS] Mining dimensions set to: {}x{}", width, length);
        }
    }
    
    /**
     * Get the width of the mining area
     * @return the mining width
     */
    public int getMiningWidth() {
        return miningWidth;
    }
    
    /**
     * Get the length of the mining area
     * @return the mining length
     */
    public int getMiningLength() {
        return miningLength;
    }
    
    /**
     * Set the mining dimensions from a network packet.
     * This is called on the client side when a sync packet is received.
     * 
     * @param width The width of the mining area
     * @param length The length of the mining area
     */
    public void setMiningDimensionsFromNetwork(int width, int length) {
        // Only update on client side
        if (world != null && world.isClient()) {
            this.miningWidth = width;
            this.miningLength = length;
            
            // Always reinitialize the mining area to ensure bounds are calculated correctly
            if (this.startPos != null && this.facingDirection != null) {
                // Recalculate bounds using the same logic as initializeMiningArea
                int minX, maxX, minZ, maxZ;
                
                if (facingDirection == Direction.NORTH) {
                    minX = startPos.getX();
                    maxX = startPos.getX() + miningWidth - 1;
                    minZ = startPos.getZ() - miningLength + 1;
                    maxZ = startPos.getZ();
                } else if (facingDirection == Direction.SOUTH) {
                    minX = startPos.getX() - miningWidth + 1;
                    maxX = startPos.getX();
                    minZ = startPos.getZ();
                    maxZ = startPos.getZ() + miningLength - 1;
                } else if (facingDirection == Direction.EAST) {
                    minX = startPos.getX();
                    maxX = startPos.getX() + miningLength - 1;
                    minZ = startPos.getZ();
                    maxZ = startPos.getZ() + miningWidth - 1;
                } else { // WEST
                    minX = startPos.getX() - miningLength + 1;
                    maxX = startPos.getX();
                    minZ = startPos.getZ() - miningWidth + 1;
                    maxZ = startPos.getZ();
                }
                
                this.miningAreaMinX = minX;
                this.miningAreaMaxX = maxX;
                this.miningAreaMinZ = minZ;
                this.miningAreaMaxZ = maxZ;
            } else {
                // If we don't have startPos or facingDirection, try to initialize the mining area
                if (this.pos != null && world != null) {
                    initializeMiningArea(this.pos, world.getBlockState(this.pos));
                }
            }
            
            markDirty();
            // Circuitmod.LOGGER.info("[CLIENT] Updated quarry dimensions from network: {}x{}", width, length);
        }
    }
    
    /**
     * Reset the quarry to start mining from the top again.
     * This resets the current mining position and Y level to the starting position.
     */
    public void resetHeight() {
        if (world == null || world.isClient()) {
            return;
        }
        
        // Reset current mining progress
        this.currentMiningPos = null;
        this.currentMiningProgress = 0;
        this.totalMiningTicks = 0;
        this.currentMiningTicks = 0;
        
        // Reset Y level to the starting height
        if (this.startPos != null) {
            this.currentY = this.startPos.getY();
            
            // Reset to the starting corner of the mining area
            this.currentPos = new BlockPos(this.miningAreaMinX, this.startPos.getY(), this.miningAreaMinZ);
            
            Circuitmod.LOGGER.info("[QUARRY-RESET] Reset quarry height at {} to start from Y level {} at position {}", pos, this.currentY, this.currentPos);
        }
        
        markDirty();
    }
    
    // NamedScreenHandlerFactory implementation
    @Override
    public Text getDisplayName() {
        return Text.translatable("block.circuitmod.quarry_block");
    }
    
    @Override
    public ModScreenHandlers.QuarryData getScreenOpeningData(ServerPlayerEntity player) {
        return new ModScreenHandlers.QuarryData(this.pos);
    }
    
    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new QuarryScreenHandler(syncId, playerInventory, this, this.propertyDelegate, this);
    }

    public boolean findAndJoinNetwork() {
        if (world == null || world.isClient()) return false;
        boolean foundNetwork = false;
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.offset(dir);
            BlockEntity be = world.getBlockEntity(neighborPos);
            if (be instanceof IPowerConnectable) {
                IPowerConnectable connectable = (IPowerConnectable) be;
                EnergyNetwork network = connectable.getNetwork();
                if (network != null && network != this.network) {
                    if (this.network != null) {
                        this.network.removeBlock(pos);
                    }
                    network.addBlock(pos, this);
                    foundNetwork = true;
                    break;
                }
            }
        }
        if (!foundNetwork && (this.network == null)) {
            EnergyNetwork newNetwork = new EnergyNetwork();
            newNetwork.addBlock(pos, this);
            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = pos.offset(dir);
                BlockEntity be = world.getBlockEntity(neighborPos);
                if (be instanceof IPowerConnectable && ((IPowerConnectable) be).getNetwork() == null) {
                    IPowerConnectable connectable = (IPowerConnectable) be; // find the problem here 
                    if (connectable.canConnectPower(dir.getOpposite()) && this.canConnectPower(dir)) {
                        newNetwork.addBlock(neighborPos, connectable);
                    }
                }
            }
            // If we created a new network, consider it a success
            foundNetwork = true;
        }
        return foundNetwork;
    }

    /**
     * Get the perimeter positions of the mining area for rendering
     * @return List of BlockPos representing the perimeter of the mining area
     */
    public List<BlockPos> getPerimeterPositions() {
        List<BlockPos> perimeter = new ArrayList<>();
        
        // If mining area isn't initialized, try to initialize it
        if (startPos == null || facingDirection == null) {
            if (world != null && this.pos != null) {
                initializeMiningArea(this.pos, world.getBlockState(this.pos));
            } else {
                // If we still can't initialize, return empty list
                return perimeter;
            }
        }
        
        // Double-check that initialization worked
        if (startPos == null || facingDirection == null) {
            return perimeter;
        }
        
        // Get the current mining area bounds
        int minX = this.miningAreaMinX;
        int maxX = this.miningAreaMaxX;
        int minZ = this.miningAreaMinZ;
        int maxZ = this.miningAreaMaxZ;
        
        // If bounds are not set, try to initialize them
        if (minX == 0 && maxX == 0 && minZ == 0 && maxZ == 0 && startPos != null && facingDirection != null) {
            // Recalculate bounds using the same logic as initializeMiningArea
            if (facingDirection == Direction.NORTH) {
                minX = startPos.getX();
                maxX = startPos.getX() + miningWidth - 1;
                minZ = startPos.getZ() - miningLength + 1;
                maxZ = startPos.getZ();
            } else if (facingDirection == Direction.SOUTH) {
                minX = startPos.getX() - miningWidth + 1;
                maxX = startPos.getX();
                minZ = startPos.getZ();
                maxZ = startPos.getZ() + miningLength - 1;
            } else if (facingDirection == Direction.EAST) {
                minX = startPos.getX();
                maxX = startPos.getX() + miningLength - 1;
                minZ = startPos.getZ();
                maxZ = startPos.getZ() + miningWidth - 1;
            } else { // WEST
                minX = startPos.getX() - miningLength + 1;
                maxX = startPos.getX();
                minZ = startPos.getZ() - miningWidth + 1;
                maxZ = startPos.getZ();
            }
            
            // Update the stored bounds
            this.miningAreaMinX = minX;
            this.miningAreaMaxX = maxX;
            this.miningAreaMinZ = minZ;
            this.miningAreaMaxZ = maxZ;
        }
        

        
        // Validate that the mining area is reasonable
        if (maxX < minX || maxZ < minZ) {
            return perimeter;
        }
        
        // Limit the size of the perimeter to avoid performance issues
        int width = maxX - minX + 1;
        int length = maxZ - minZ + 1;
        if (width > 100 || length > 100) {
            return perimeter;
        }
        
        // Final check - if we still don't have valid bounds, return empty
        if (minX == 0 && maxX == 0 && minZ == 0 && maxZ == 0) {
            return perimeter;
        }
        
        // Add all positions along the perimeter
        // Top edge (maxZ)
        for (int x = minX; x <= maxX; x++) {
            perimeter.add(new BlockPos(x, startPos.getY(), maxZ));
        }
        
        // Bottom edge (minZ)
        for (int x = minX; x <= maxX; x++) {
            perimeter.add(new BlockPos(x, startPos.getY(), minZ));
        }
        
        // Left edge (minX)
        for (int z = minZ + 1; z < maxZ; z++) {
            perimeter.add(new BlockPos(minX, startPos.getY(), z));
        }
        
        // Right edge (maxX)
        for (int z = minZ + 1; z < maxZ; z++) {
            perimeter.add(new BlockPos(maxX, startPos.getY(), z));
        }
        

        
        return perimeter;
    }
    
    /**
     * Get the mining area bounds for debugging/rendering purposes
     * @return Array containing [minX, maxX, minZ, maxZ]
     */
    public int[] getMiningAreaBounds() {
        return new int[]{miningAreaMinX, miningAreaMaxX, miningAreaMinZ, miningAreaMaxZ};
    }
} 