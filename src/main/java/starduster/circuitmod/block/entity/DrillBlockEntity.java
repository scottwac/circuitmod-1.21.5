package starduster.circuitmod.block.entity;

import net.minecraft.block.Block;
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
import starduster.circuitmod.block.machines.DrillBlock;
import starduster.circuitmod.network.ModNetworking;
import starduster.circuitmod.power.EnergyNetwork;
import starduster.circuitmod.power.IEnergyConsumer;
import starduster.circuitmod.power.IPowerConnectable;
import starduster.circuitmod.screen.ModScreenHandlers;
import starduster.circuitmod.screen.DrillScreenHandler;
import net.minecraft.block.Blocks;

import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.sound.ModSounds;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.util.Identifier;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;

public class DrillBlockEntity extends BlockEntity implements SidedInventory, NamedScreenHandlerFactory, ExtendedScreenHandlerFactory<ModScreenHandlers.DrillData>, IEnergyConsumer {
    // Energy properties
    private static final int MAX_ENERGY_DEMAND = 1000; // Maximum energy demand per tick
    private int energyDemand = MAX_ENERGY_DEMAND; // Current energy demand per tick
    private int energyReceived = 0; // Energy received this tick
    private int accumulatedEnergy = 0; // Energy accumulated for current block
    private EnergyNetwork network;
    
    // Mining properties
    private int currentBlockEnergyCost = 1; // Energy cost for the current block being mined
    private boolean miningEnabled = false; // Whether mining is enabled or disabled
    private boolean inventoryFullPause = false; // Whether we're paused due to full inventory
    
    // Mining progress tracking
    private BlockPos currentMiningPos = null; // Current block being mined
    private int currentMiningProgress = 0; // Progress on current block (0-100)
    
    // Area properties - for horizontal mining with fixed depth
    private BlockPos startPos; // Starting corner of the mining area
    private BlockPos currentPos; // Current mining position
    private int currentDepth; // Current depth level (distance from start)
    private int currentY; // Current mining Y level (vertical position)
    private int currentWidth; // Current width position (X or Z depending on facing)
    private Direction facingDirection; // Direction the drill mines (same as visual facing)
    private int miningHeight = 16; // Height of the mining area (Y direction)
    private int miningWidth = 16; // Width of the mining area (X or Z direction depending on facing)
    private static final int MINING_DEPTH = 50; // Fixed depth of 50 blocks
    
    // Mining area bounds (calculated once and reused)
    private int miningAreaMinY;
    private int miningAreaMaxY;
    private int miningAreaMinWidth;
    private int miningAreaMaxWidth;
    
    // Networking properties
    private int packetCooldown = 0; // Cooldown to avoid sending too many packets
    private static final int PACKET_COOLDOWN_MAX = 10; // Only send packets every 10 ticks max (0.5 seconds)
    // Cached enchantment level from the placed item (Fortune only for now)
    private int cachedFortuneLevel = 0;
    
    // Property delegate indices
    private static final int ENERGY_RECEIVED_INDEX = 0;
    private static final int MINING_ENABLED_INDEX = 1;
    private static final int ACCUMULATED_ENERGY_INDEX = 2;
    private static final int CURRENT_BLOCK_COST_INDEX = 3;
    private static final int PROPERTY_COUNT = 4;
    
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
                case ACCUMULATED_ENERGY_INDEX:
                    return accumulatedEnergy;
                case CURRENT_BLOCK_COST_INDEX:
                    return currentBlockEnergyCost;
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
                case ACCUMULATED_ENERGY_INDEX:
                    accumulatedEnergy = value;
                    break;
                case CURRENT_BLOCK_COST_INDEX:
                    currentBlockEnergyCost = value;
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

    public DrillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRILL_BLOCK_ENTITY, pos, state);
        
        // Initialize mining area immediately if we have the necessary data
        if (pos != null && state != null) {
            initializeMiningArea(pos, state);
        }
    }

    // Save data to NBT when the block is saved
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putInt("fortune_level", this.cachedFortuneLevel);
        
        // Save mining and energy data
        nbt.putInt("energy_demand", this.energyDemand);
        nbt.putInt("energy_received", this.energyReceived);
        nbt.putInt("current_block_energy_cost", this.currentBlockEnergyCost);
        nbt.putBoolean("mining_enabled", this.miningEnabled);
        nbt.putInt("mining_height", this.miningHeight);
        nbt.putInt("mining_width", this.miningWidth);
        
        // Save mining area bounds
        nbt.putInt("mining_area_min_y", this.miningAreaMinY);
        nbt.putInt("mining_area_max_y", this.miningAreaMaxY);
        nbt.putInt("mining_area_min_width", this.miningAreaMinWidth);
        nbt.putInt("mining_area_max_width", this.miningAreaMaxWidth);
        
        // Save mining progress data
        if (currentMiningPos != null) {
            nbt.putInt("current_mining_x", currentMiningPos.getX());
            nbt.putInt("current_mining_y", currentMiningPos.getY());
            nbt.putInt("current_mining_z", currentMiningPos.getZ());
        }
        nbt.putInt("current_mining_progress", this.currentMiningProgress);
        nbt.putInt("accumulated_energy", this.accumulatedEnergy);
        nbt.putBoolean("inventory_full_pause", this.inventoryFullPause);
        
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
        
        nbt.putInt("current_depth", this.currentDepth);
        nbt.putInt("current_y_level", this.currentY);
        nbt.putInt("current_width", this.currentWidth);
        
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
        this.cachedFortuneLevel = nbt.getInt("fortune_level").orElse(0);
        
        // Load mining and energy data
        this.energyDemand = nbt.getInt("energy_demand").orElse(MAX_ENERGY_DEMAND);
        this.energyReceived = nbt.getInt("energy_received").orElse(0);
        this.currentBlockEnergyCost = nbt.getInt("current_block_energy_cost").orElse(1);
        this.miningEnabled = nbt.getBoolean("mining_enabled").orElse(false);
        this.miningHeight = nbt.getInt("mining_height").orElse(16);
        this.miningWidth = nbt.getInt("mining_width").orElse(16);
        
        // Load mining area bounds
        this.miningAreaMinY = nbt.getInt("mining_area_min_y").orElse(0);
        this.miningAreaMaxY = nbt.getInt("mining_area_max_y").orElse(0);
        this.miningAreaMinWidth = nbt.getInt("mining_area_min_width").orElse(0);
        this.miningAreaMaxWidth = nbt.getInt("mining_area_max_width").orElse(0);
        
        // Load mining progress data
        if (nbt.contains("current_mining_x")) {
            int x = nbt.getInt("current_mining_x").orElse(0);
            int y = nbt.getInt("current_mining_y").orElse(0);
            int z = nbt.getInt("current_mining_z").orElse(0);
            this.currentMiningPos = new BlockPos(x, y, z);
        }
        this.currentMiningProgress = nbt.getInt("current_mining_progress").orElse(0);
        this.accumulatedEnergy = nbt.getInt("accumulated_energy").orElse(0);
        this.inventoryFullPause = nbt.getBoolean("inventory_full_pause").orElse(false);
        
        // Load mining area data
        if (nbt.contains("start_x")) {
            int x = nbt.getInt("start_x").orElse(0);
            int y = nbt.getInt("start_y").orElse(0);
            int z = nbt.getInt("start_z").orElse(0);
            this.startPos = new BlockPos(x, y, z);
        }
        
        if (nbt.contains("current_x")) {
            int x = nbt.getInt("current_x").orElse(0);
            int y = nbt.getInt("current_y").orElse(0);
            int z = nbt.getInt("current_z").orElse(0);
            this.currentPos = new BlockPos(x, y, z);
        }
        
        this.currentDepth = nbt.getInt("current_depth").orElse(0);
        this.currentY = nbt.getInt("current_y_level").orElse(0);
        this.currentWidth = nbt.getInt("current_width").orElse(0);
        
        if (nbt.contains("facing_direction")) {
            int ordinal = nbt.getInt("facing_direction").orElse(0);
            this.facingDirection = Direction.values()[ordinal];
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
            initializeMiningArea(pos, world.getBlockState(pos));
        }
        
        // Ensure mining area is properly initialized on both client and server
        if (startPos == null && pos != null && world != null) {
            initializeMiningArea(pos, world.getBlockState(pos));
        }
    }

    // Sound properties
    private int soundClock = 0;

    public void tick(World world, BlockPos pos, BlockState state, DrillBlockEntity blockEntity) {
        boolean runningBefore = blockEntity.isMiningEnabled();

        // Sound and visual state management (like quarry)
        if(blockEntity.isMiningEnabled()) {
            if(blockEntity.soundClock <= 0){
                blockEntity.soundClock = 160;
            }
            if(blockEntity.soundClock == 160) {
                world.playSound(null, pos, ModSounds.MINER_MACHINE_RUN, SoundCategory.BLOCKS, 1F, 1F);
            }
            blockEntity.soundClock = blockEntity.soundClock - 1;
            world.setBlockState(pos, world.getBlockState(pos).with(DrillBlock.RUNNING, true), Block.NOTIFY_ALL);
        }
        if(!blockEntity.isMiningEnabled()) {
            world.setBlockState(pos, world.getBlockState(pos).with(DrillBlock.RUNNING, false), Block.NOTIFY_ALL);
            blockEntity.soundClock = 160;
        }

        // Condense inventory every 20 ticks (once per second) to prevent scattered items
        if (world.getTime() % 20 == 0) {
            blockEntity.condenseInventory();
        }

        // Handle network refresh with retry logic for world reload scenarios
        if (blockEntity.needsNetworkRefresh) {
            boolean networkFound = blockEntity.findAndJoinNetwork();
            if (networkFound) {
                blockEntity.needsNetworkRefresh = false;
                Circuitmod.LOGGER.info("[DRILL-NETWORK] Successfully joined network at " + pos);
            } else {
                // If we couldn't find a network, try again in a few ticks
                // This helps with world reload scenarios where power cables might not be loaded yet
                if (world.getTime() % 20 == 0) { // Log every second
                    Circuitmod.LOGGER.info("[DRILL-NETWORK] No network found at " + pos + ", will retry. Mining enabled: " + blockEntity.miningEnabled);
                }
            }
        }
        if (world.isClient()) {
            return;
        }

        // Initialize mining area if not set (this should happen first)
        if (blockEntity.startPos == null) {
            blockEntity.initializeMiningArea(pos, state);
        }
        
        // Safety check - validate that drill's mining area exists
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
            // Circuitmod.LOGGER.info("[DRILL-TICK] Energy received: " + blockEntity.energyReceived + ", network: " + networkInfo);
        }
        
        // Process mining operations if mining is enabled
        if (blockEntity.miningEnabled) {
            // Try to mine the current block
            boolean mined = blockEntity.mineNextBlock(world);
            
            if (mined) {
                needsSync = true;
            }
        } else {
            // Mining is disabled
            needsSync = true;
            
            // If mining is enabled but we're not receiving power, try to refresh network connection
            if (blockEntity.miningEnabled && blockEntity.energyReceived == 0 && blockEntity.network == null) {
                if (world.getTime() % 40 == 0) { // Try every 2 seconds
                    Circuitmod.LOGGER.info("[DRILL-NETWORK-RETRY] Mining enabled but no power at " + pos + ", attempting network refresh");
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
                ModNetworking.sendDrillMiningProgressUpdate(PlayerLookup.tracking(serverWorld, pos), blockEntity.currentMiningProgress, blockEntity.currentMiningPos);
            }
        }
        
        // Send mining area bounds to clients for rendering (every 20 ticks to avoid spam)
        if (world instanceof ServerWorld serverWorld && world.getTime() % 20 == 0) {
            for (ServerPlayerEntity player : PlayerLookup.tracking(serverWorld, pos)) {
                ModNetworking.sendDrillDimensionsUpdate(PlayerLookup.tracking(serverWorld, pos), blockEntity.miningHeight, blockEntity.miningWidth);
            }
        }
        
        // Send initial sync to new players (every 100 ticks to avoid spam)
        if (world instanceof ServerWorld serverWorld && world.getTime() % 100 == 0) {
            for (ServerPlayerEntity player : PlayerLookup.tracking(serverWorld, pos)) {
                // Send current mining enabled status
                ModNetworking.sendDrillMiningEnabledUpdate(PlayerLookup.tracking(serverWorld, pos), blockEntity.miningEnabled);
                // Send current dimensions
                ModNetworking.sendDrillDimensionsUpdate(PlayerLookup.tracking(serverWorld, pos), blockEntity.miningHeight, blockEntity.miningWidth);
            }
        }
    }

    /**
     * Initializes the mining area based on the drill's position and facing direction
     */
    private void initializeMiningArea(BlockPos pos, BlockState state) {
        // Get the facing direction from the block state
        Direction facing = state.get(net.minecraft.block.HorizontalFacingBlock.FACING);
        // INVERT: Use the opposite direction for mining (like quarry)
        this.facingDirection = facing.getOpposite();
        
        // Calculate the starting position (one block in front of the drill IN THE MINING DIRECTION)
        // The renderer draws the green rectangle here, so mining should start here too
        BlockPos frontPos = pos.offset(this.facingDirection);
        this.startPos = frontPos;
        this.currentPos = frontPos;
        
        // System.out.println("[DRILL-DEBUG] Visual facing: " + facing + ", Mining direction: " + this.facingDirection);
        // System.out.println("[DRILL-DEBUG] Drill position: " + pos + ", Start position: " + frontPos);
        
        // Calculate mining area bounds for horizontal mining with fixed depth
        // The drill mines horizontally with configurable height and width, fixed depth of 50 blocks
        int centerY = frontPos.getY();
        
        // Calculate bounds for Y (height) direction
        this.miningAreaMinY = centerY - (miningHeight / 2);
        this.miningAreaMaxY = centerY + (miningHeight / 2) - 1;
        
        // Calculate bounds for width direction (X or Z depending on facing)
        if (this.facingDirection == Direction.NORTH || this.facingDirection == Direction.SOUTH) {
            // Drill mines North/South, width is X direction
            int centerX = frontPos.getX();
            this.miningAreaMinWidth = centerX - (miningWidth / 2);
            this.miningAreaMaxWidth = centerX + (miningWidth / 2) - 1;
            // System.out.println("[DRILL-DEBUG] North/South facing - Width is X direction");
            // System.out.println("[DRILL-DEBUG] centerX: " + centerX + ", miningWidth: " + miningWidth);
            // System.out.println("[DRILL-DEBUG] miningAreaMinWidth: " + miningAreaMinWidth + ", miningAreaMaxWidth: " + miningAreaMaxWidth);
        } else {
            // Drill mines East/West, width is Z direction
            int centerZ = frontPos.getZ();
            this.miningAreaMinWidth = centerZ - (miningWidth / 2);
            this.miningAreaMaxWidth = centerZ + (miningWidth / 2) - 1;
            // System.out.println("[DRILL-DEBUG] East/West facing - Width is Z direction");
            // System.out.println("[DRILL-DEBUG] centerZ: " + centerZ + ", miningWidth: " + miningWidth);
            // System.out.println("[DRILL-DEBUG] miningAreaMinWidth: " + miningAreaMinWidth + ", miningAreaMaxWidth: " + miningAreaMaxWidth);
        }
        
        // Start at the minimum Y and width, depth 0
        this.currentY = this.miningAreaMinY;
        this.currentWidth = this.miningAreaMinWidth;
        this.currentDepth = 0; // Start at depth 0 (front position)
        
        // Mark dirty to trigger client sync
        markDirty();
        
        Circuitmod.LOGGER.info("[DRILL-DEBUG] Initialized mining area at {} facing {}, bounds: Y({}-{}), Width({}-{}), Depth: 0-{}", 
            pos, facing, miningAreaMinY, miningAreaMaxY, miningAreaMinWidth, miningAreaMaxWidth, MINING_DEPTH);
        Circuitmod.LOGGER.info("[DRILL-DEBUG] Visual facing: {}, Mining direction: {}, StartPos: {}", 
            facing, this.facingDirection, frontPos);
    }

    /**
     * Checks if a position is within the mining area bounds
     */
    private boolean isPositionInArea(BlockPos pos, int minY, int maxY, int minWidth, int maxWidth, int depth) {
        // Check Y bounds
        if (pos.getY() < minY || pos.getY() > maxY) {
            return false;
        }
        
        // Check width bounds (X or Z depending on facing direction)
        if (facingDirection == Direction.NORTH || facingDirection == Direction.SOUTH) {
            // Width is X direction
            if (pos.getX() < minWidth || pos.getX() > maxWidth) {
                return false;
            }
        } else {
            // Width is Z direction
            if (pos.getZ() < minWidth || pos.getZ() > maxWidth) {
                return false;
            }
        }
        
        // Check depth bounds (distance from start position)
        int distanceFromStart = Math.abs(pos.getX() - startPos.getX()) + Math.abs(pos.getZ() - startPos.getZ());
        return distanceFromStart <= depth;
    }

    /**
     * Attempts to mine the next block in the sequence (quarry-style)
     */
    private boolean mineNextBlock(World world) {
        // If mining is disabled, do nothing
        if (!miningEnabled) {
            advanceToNextBlock();
            return false;
        }
        
        // If we're paused due to full inventory, check if we have space now
        if (inventoryFullPause) {
            if (!isInventoryFull()) {
                inventoryFullPause = false;
                Circuitmod.LOGGER.info("[DRILL-MINING] Inventory space available, resuming mining");
            } else {
                // Still full, can't mine
                return false;
            }
        }
        
        // Get current mining position
        if (currentMiningPos == null) {
            currentMiningPos = getNextMiningPos();
            if (currentMiningPos == null) {
                // No more blocks to mine
                return false;
            }
            
            // Calculate energy cost for this new block
            BlockState blockState = world.getBlockState(currentMiningPos);
            float hardness = blockState.getBlock().getHardness();
            currentBlockEnergyCost = Math.max(1, (int)(hardness * 100)); // 100 energy per hardness point
            accumulatedEnergy = 0; // Reset accumulated energy for new block
            currentMiningProgress = 0;
            
            Circuitmod.LOGGER.info("[DRILL-MINING] Starting new block at {} with cost {} energy", currentMiningPos, currentBlockEnergyCost);
        }
        
        // Accumulate energy from this tick
        if (energyReceived > 0) {
            accumulatedEnergy += energyReceived;
            
            // Calculate mining progress as percentage
            currentMiningProgress = Math.min(100, (accumulatedEnergy * 100) / currentBlockEnergyCost);
        }
        
        // Check if we have enough energy to mine this block
        if (accumulatedEnergy >= currentBlockEnergyCost) {
            // Try to mine the block
            boolean success = mineBlock(world, currentMiningPos);
            
            if (success) {
                Circuitmod.LOGGER.info("[DRILL-MINING] Successfully mined block at {}", currentMiningPos);
                
                // Reset for next block
                currentMiningPos = null;
                accumulatedEnergy = 0;
                currentMiningProgress = 0;
                currentBlockEnergyCost = 1;
                // Advance to the next position in the scan so we don't retry the same block
                advanceToNextPosition(miningAreaMinY, miningAreaMaxY, miningAreaMinWidth, miningAreaMaxWidth);
                
                return true;
            } else {
                // Mining failed (inventory full) - pause and wait
                inventoryFullPause = true;
                Circuitmod.LOGGER.info("[DRILL-MINING] Mining failed at {} (inventory full) - pausing", currentMiningPos);
                return false;
            }
        }
        
        // Not enough energy yet, continue accumulating
        return false;
    }

    /**
     * Advances to the next block in the mining sequence
     */
    private void advanceToNextBlock() {
        currentMiningPos = null;
        currentMiningProgress = 0;
        advanceToNextPosition(miningAreaMinY, miningAreaMaxY, miningAreaMinWidth, miningAreaMaxWidth);
    }

    /**
     * Gets the next mining position in the sequence
     */
    private BlockPos getNextMiningPos() {
        if (currentPos == null || startPos == null || facingDirection == null) {
            return null;
        }
        
        // Check if we've completed the current Y level
        if (currentY > miningAreaMaxY) {
            // Move to next width position
            currentWidth++;
            currentY = miningAreaMinY;
            
            // Check if we've completed the current width level
            if (currentWidth > miningAreaMaxWidth) {
                // We've completed the entire vertical plane, move to next depth level
                currentDepth++;
                currentWidth = miningAreaMinWidth;
                currentY = miningAreaMinY;
                
                // Check if we've gone too deep (safety check)
                if (currentDepth >= MINING_DEPTH) {
                    Circuitmod.LOGGER.warn("[DRILL] Reached maximum depth of {}, stopping mining", MINING_DEPTH);
                    return null;
                }
            }
        }
        
        // Calculate the actual block position based on facing direction
        int x, y, z;
        y = currentY;
        
        if (facingDirection == Direction.NORTH || facingDirection == Direction.SOUTH) {
            // Width is X direction, depth is Z direction
            x = currentWidth;
            z = startPos.getZ() + (facingDirection == Direction.NORTH ? -currentDepth : currentDepth);
        } else {
            // Width is Z direction, depth is X direction
            x = startPos.getX() + (facingDirection == Direction.WEST ? -currentDepth : currentDepth);
            z = currentWidth;
        }
        
        // Create the next mining position
        BlockPos nextPos = new BlockPos(x, y, z);
        
        // Debug: Log the calculated position
        // Circuitmod.LOGGER.info("[DRILL-POSITION-DEBUG] Calculated next mining position: " + nextPos);
        // Circuitmod.LOGGER.info("[DRILL-POSITION-DEBUG]   Current depth: " + currentDepth);
        // Circuitmod.LOGGER.info("[DRILL-POSITION-DEBUG]   Current width: " + currentWidth);
        // Circuitmod.LOGGER.info("[DRILL-POSITION-DEBUG]   Current Y: " + currentY);
        // Circuitmod.LOGGER.info("[DRILL-POSITION-DEBUG]   Facing: " + facingDirection);
        
        // Check if this position is safe to mine
        if (isInSafeZone(nextPos)) {
            // Skip this position and try the next one
            advanceToNextPosition(miningAreaMinY, miningAreaMaxY, miningAreaMinWidth, miningAreaMaxWidth);
            return getNextMiningPos();
        }
        
        return nextPos;
    }

    /**
     * Advances to the next position in the mining sequence
     */
    private void advanceToNextPosition(int minY, int maxY, int minWidth, int maxWidth) {
        if (currentPos == null) {
            return;
        }
        
        // Move to next Y position
        currentY++;
        
        // If we've reached the end of the Y range, move to next width position
        if (currentY > maxY) {
            currentY = minY;
            currentWidth++;
            
            // If we've reached the end of the width range, move to next depth level
            if (currentWidth > maxWidth) {
                currentWidth = minWidth;
                currentDepth++;
                
                // Safety check for maximum depth
                if (currentDepth >= MINING_DEPTH) {
                    Circuitmod.LOGGER.warn("[DRILL] Reached maximum depth of {}, stopping mining", MINING_DEPTH);
                    currentPos = null;
                    return;
                }
            }
        }
        
        // Calculate the actual block position based on facing direction
        int x, y, z;
        y = currentY;
        
        if (facingDirection == Direction.NORTH || facingDirection == Direction.SOUTH) {
            // Width is X direction, depth is Z direction
            x = currentWidth;
            z = startPos.getZ() + (facingDirection == Direction.NORTH ? -currentDepth : currentDepth);
        } else {
            // Width is Z direction, depth is X direction
            x = startPos.getX() + (facingDirection == Direction.WEST ? -currentDepth : currentDepth);
            z = currentWidth;
        }
        
        // Update current position
        currentPos = new BlockPos(x, y, z);
    }

    /**
     * Checks if a position is safe to mine (not the drill itself or bedrock)
     */
    private boolean isInSafeZone(BlockPos targetPos) {
        // Don't mine the drill itself
        if (targetPos.equals(pos)) {
            return true; // Safe zone includes the drill
        }
        
        // Don't mine the block directly in front of the drill (where the drill is placed)
        if (startPos != null && targetPos.equals(startPos)) {
            return true; // Safe zone includes the starting position
        }
        
        // Don't mine bedrock
        if (world != null) {
            BlockState blockState = world.getBlockState(targetPos);
            if (blockState.isOf(Blocks.BEDROCK)) {
                return true; // Safe zone includes bedrock
            }
        }
        
        return false; // Not in safe zone, can mine
    }
    
    /**
     * Mine a block at the specified position using quarry-like logic
     */
    private boolean mineBlock(World world, BlockPos miningPos) {
        BlockState blockState = world.getBlockState(miningPos);
        BlockEntity blockEntity = world.getBlockEntity(miningPos);
        
        // Build a mock tool like the quarry to apply tool-required and Fortune
        ItemStack mockTool = ItemStack.EMPTY;
        if (blockState.isToolRequired() || cachedFortuneLevel > 0) {
            mockTool = new ItemStack(net.minecraft.item.Items.NETHERITE_PICKAXE);
            if (cachedFortuneLevel > 0) {
                int fortune = Math.max(1, Math.min(5, cachedFortuneLevel));
                Registry<Enchantment> reg = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
                Enchantment ench = reg.get(Identifier.ofVanilla("fortune"));
                if (ench != null) {
                    int raw = reg.getRawId(ench);
                    java.util.Optional<RegistryEntry.Reference<Enchantment>> ref = reg.getEntry(raw);
                    ref.ifPresent(entry -> mockTool.addEnchantment(entry, fortune));
                }
            }
        }

        // Get the drops using the mock tool (if empty, behaves like hand)
        List<ItemStack> drops = Block.getDroppedStacks(
            blockState,
            (ServerWorld) world,
            miningPos,
            blockEntity,
            null,
            mockTool
        );
        
        // Check if we have space in inventory for all drops
        boolean canAddAll = true;
        for (ItemStack drop : drops) {
            if (!drop.isEmpty() && !canAddToInventory(drop)) {
                canAddAll = false;
                break;
            }
        }
        
        if (!canAddAll) {
            // Inventory doesn't have space for all drops
            return false;
        }
        
        // Add all drops to inventory
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                addToInventory(drop.copy());
            }
        }
        
        // Remove the block
        world.removeBlock(miningPos, false);
        
        return true;
    }

    public void setFortuneLevel(int fortuneLevel) {
        this.cachedFortuneLevel = Math.max(0, fortuneLevel);
        markDirty();
    }
    
    /**
     * Helper method to check if an item can be added to inventory
     */
    private boolean canAddToInventory(ItemStack itemToAdd) {
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.get(i);
            if (stack.isEmpty()) {
                return true; // Found empty slot
            } else if (ItemStack.areItemsEqual(stack, itemToAdd)) {
                int maxStack = Math.min(stack.getMaxCount(), getMaxCountPerStack());
                if (stack.getCount() + itemToAdd.getCount() <= maxStack) {
                    return true; // Can fit in existing stack
                }
            }
        }
        return false; // No space available
    }
    
    /**
     * Helper method to add an item to inventory
     */
    private void addToInventory(ItemStack itemToAdd) {
        // First try to add to existing stacks
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.get(i);
            if (!stack.isEmpty() && ItemStack.areItemsEqual(stack, itemToAdd)) {
                int maxStack = Math.min(stack.getMaxCount(), getMaxCountPerStack());
                int spaceAvailable = maxStack - stack.getCount();
                if (spaceAvailable > 0) {
                    int toAdd = Math.min(spaceAvailable, itemToAdd.getCount());
                    stack.increment(toAdd);
                    itemToAdd.decrement(toAdd);
                    
                    if (itemToAdd.isEmpty()) {
                        return;
                    }
                }
            }
        }
        
        // Then add to empty slots
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.get(i);
            if (stack.isEmpty()) {
                inventory.set(i, itemToAdd.copy());
                return;
            }
        }
    }



    /**
     * Checks if the inventory is full
     */
    private boolean isInventoryFull() {
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.get(i).isEmpty()) {
                return false;
            }
        }
        return true;
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
            this.energyReceived += energyToConsume;
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
        return player.squaredDistanceTo((double)pos.getX() + 0.5D, (double)pos.getY() + 0.5D, (double)pos.getZ() + 0.5D) <= 64.0D;
    }
    
    @Override
    public void clear() {
        inventory.clear();
    }
    
    @Override
    public int[] getAvailableSlots(Direction side) {
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

    // Block entity events
    @Override
    public boolean onSyncedBlockEvent(int type, int data) {
        if (type == 0) {
            // Handle mining enabled toggle
            boolean enabled = data == 1;
            if (miningEnabled != enabled) {
                miningEnabled = enabled;
                markDirty();
                Circuitmod.LOGGER.info("[DRILL] Mining toggled to: {}", enabled);
            }
            return true;
        }
        return false;
    }

    // Network synchronization methods
    public void setMiningProgressFromNetwork(int miningProgress, BlockPos miningPos) {
        this.currentMiningProgress = miningProgress;
        this.currentMiningPos = miningPos;
        Circuitmod.LOGGER.info("[DRILL] Received mining progress update: {} at {}", miningProgress, miningPos);
    }
    
    public BlockPos getCurrentMiningPos() {
        return currentMiningPos;
    }
    
    public int getCurrentMiningProgress() {
        return currentMiningProgress;
    }
    
    public int getAccumulatedEnergy() {
        return accumulatedEnergy;
    }
    
    public int getCurrentBlockEnergyCost() {
        return currentBlockEnergyCost;
    }
    
    public void setMiningEnabled(boolean enabled) {
        if (miningEnabled != enabled) {
            miningEnabled = enabled;
            markDirty();
            
            // Send network update
            if (world != null && !world.isClient()) {
                ModNetworking.sendDrillMiningEnabledUpdate(
                    PlayerLookup.tracking(this),
                    enabled
                );
            }
            
            Circuitmod.LOGGER.info("[DRILL] Mining enabled set to: {}", enabled);
        }
    }
    
    public boolean isMiningEnabled() {
        return miningEnabled;
    }
    
    public void toggleMining() {
        setMiningEnabled(!miningEnabled);
    }
    
    public void setMiningEnabledFromNetwork(boolean enabled) {
        if (miningEnabled != enabled) {
            miningEnabled = enabled;
            markDirty();
            Circuitmod.LOGGER.info("[DRILL] Mining enabled updated from network: {}", enabled);
        }
    }
    
    public void setMiningDimensions(int height, int width) {
        // Validate dimensions
        height = Math.max(1, Math.min(100, height));
        width = Math.max(1, Math.min(100, width));
        
        if (this.miningHeight != height || this.miningWidth != width) {
            this.miningHeight = height;
            this.miningWidth = width;
            
            // Always reinitialize the mining area when dimensions change
            // This ensures the drill updates immediately regardless of mining state
            this.initializeMiningArea(this.pos, this.getCachedState());
            
            // Reset current mining position to start from the beginning of the new area
            this.currentMiningPos = null;
            this.currentMiningProgress = 0;
            this.accumulatedEnergy = 0;
            this.currentBlockEnergyCost = 1;
            
            markDirty();
            
            Circuitmod.LOGGER.info("[DRILL] Mining dimensions set to: {}x{}", height, width);
        }
    }
    
    public int getMiningHeight() {
        return miningHeight;
    }
    
    public int getMiningWidth() {
        return miningWidth;
    }
    
    public int getMiningLength() {
        return miningWidth; // For drill, length is the same as width (horizontal mining)
    }
    
    public void setMiningDimensionsFromNetwork(int height, int width) {
        // Only update on client side
        if (world != null && world.isClient()) {
            this.miningHeight = height;
            this.miningWidth = width;
            
            // Always reinitialize the mining area to ensure bounds are calculated correctly
            if (this.startPos != null && this.facingDirection != null) {
                // Recalculate bounds using the same logic as initializeMiningArea
                int centerY = startPos.getY();
                
                // Calculate bounds for Y (height) direction
                this.miningAreaMinY = centerY - (miningHeight / 2);
                this.miningAreaMaxY = centerY + (miningHeight / 2) - 1;
                
                // Calculate bounds for width direction (X or Z depending on facing)
                if (this.facingDirection == Direction.NORTH || this.facingDirection == Direction.SOUTH) {
                    // Width is X direction
                    int centerX = startPos.getX();
                    this.miningAreaMinWidth = centerX - (miningWidth / 2);
                    this.miningAreaMaxWidth = centerX + (miningWidth / 2) - 1;
                } else {
                    // Width is Z direction
                    int centerZ = startPos.getZ();
                    this.miningAreaMinWidth = centerZ - (miningWidth / 2);
                    this.miningAreaMaxWidth = centerZ + (miningWidth / 2) - 1;
                }
            } else {
                // If we don't have startPos or facingDirection, try to initialize the mining area
                if (pos != null && world != null) {
                    initializeMiningArea(pos, world.getBlockState(pos));
                }
            }
            
            markDirty();
            Circuitmod.LOGGER.info("[DRILL] Mining dimensions updated from network: {}x{}", height, width);
        }
    }

    // Screen handler implementation
    @Override
    public Text getDisplayName() {
        return Text.translatable("block.circuitmod.drill_block");
    }
    
    @Override
    public ModScreenHandlers.DrillData getScreenOpeningData(ServerPlayerEntity player) {
        return new ModScreenHandlers.DrillData(pos);
    }
    
    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new DrillScreenHandler(syncId, playerInventory, this, this.propertyDelegate, this);
    }
    
    public boolean findAndJoinNetwork() {
        if (world == null || world.isClient()) {
            return false;
        }
        
        // Look for nearby power cables or other power connectables
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.offset(direction);
            BlockEntity neighborEntity = world.getBlockEntity(neighborPos);
            
            if (neighborEntity instanceof IPowerConnectable connectable) {
                EnergyNetwork neighborNetwork = connectable.getNetwork();
                if (neighborNetwork != null) {
                    // Join the neighbor's network
                    neighborNetwork.addBlock(pos, this);
                    Circuitmod.LOGGER.info("[DRILL] Joined network {} at {}", 
                        neighborNetwork.getNetworkId(), pos);
                    return true;
                }
            }
        }
        
        // If no network found, create a new one
        if (network == null) {
            network = new EnergyNetwork();
            network.addBlock(pos, this);
            Circuitmod.LOGGER.info("[DRILL] Created new network {} at {}", 
                network.getNetworkId(), pos);
            return true;
        }
        
        return false;
    }
    
    /**
     * Gets the perimeter positions for rendering the mining area box
     */
    public List<BlockPos> getPerimeterPositions() {
        List<BlockPos> perimeter = new ArrayList<>();
        
        if (startPos == null || facingDirection == null) {
            // System.out.println("[DRILL-PERIMETER-DEBUG] startPos or facingDirection is null");
            return perimeter;
        }
        
        // For horizontal mining with fixed depth, we create a rectangle that shows the full mining area
        // The rectangle should extend from the start position in the mining direction for 50 blocks
        
        // System.out.println("[DRILL-PERIMETER-DEBUG] ========================================");
        // System.out.println("[DRILL-PERIMETER-DEBUG] startPos: " + startPos);
        // System.out.println("[DRILL-PERIMETER-DEBUG] facingDirection: " + facingDirection);
        // System.out.println("[DRILL-PERIMETER-DEBUG] miningAreaMinY: " + miningAreaMinY + ", miningAreaMaxY: " + miningAreaMaxY);
        // System.out.println("[DRILL-PERIMETER-DEBUG] miningAreaMinWidth: " + miningAreaMinWidth + ", miningAreaMaxWidth: " + miningAreaMaxWidth);
        
        // Calculate the end position (50 blocks in the mining direction)
        int endX, endZ;
        if (facingDirection == Direction.NORTH) {
            endX = startPos.getX();
            endZ = startPos.getZ() - MINING_DEPTH;
        } else if (facingDirection == Direction.SOUTH) {
            endX = startPos.getX();
            endZ = startPos.getZ() + MINING_DEPTH;
        } else if (facingDirection == Direction.EAST) {
            endX = startPos.getX() + MINING_DEPTH;
            endZ = startPos.getZ();
        } else { // WEST
            endX = startPos.getX() - MINING_DEPTH;
            endZ = startPos.getZ();
        }
        
        // System.out.println("[DRILL-PERIMETER-DEBUG] End position: (" + endX + ", " + endZ + ")");
        
        // Create a simple rectangle showing the mining area at the start position
        // This will show the cross-section of the mining area that extends 50 blocks in the mining direction
        
        // Top edge
        for (int width = miningAreaMinWidth; width <= miningAreaMaxWidth; width++) {
            if (facingDirection == Direction.NORTH || facingDirection == Direction.SOUTH) {
                perimeter.add(new BlockPos(width, miningAreaMaxY, startPos.getZ()));
            } else {
                perimeter.add(new BlockPos(startPos.getX(), miningAreaMaxY, width));
            }
        }
        
        // Right edge
        for (int y = miningAreaMaxY; y >= miningAreaMinY; y--) {
            if (facingDirection == Direction.NORTH || facingDirection == Direction.SOUTH) {
                perimeter.add(new BlockPos(miningAreaMaxWidth, y, startPos.getZ()));
            } else {
                perimeter.add(new BlockPos(startPos.getX(), y, miningAreaMaxWidth));
            }
        }
        
        // Bottom edge
        for (int width = miningAreaMaxWidth; width >= miningAreaMinWidth; width--) {
            if (facingDirection == Direction.NORTH || facingDirection == Direction.SOUTH) {
                perimeter.add(new BlockPos(width, miningAreaMinY, startPos.getZ()));
            } else {
                perimeter.add(new BlockPos(startPos.getX(), miningAreaMinY, width));
            }
        }
        
        // Left edge
        for (int y = miningAreaMinY; y <= miningAreaMaxY; y++) {
            if (facingDirection == Direction.NORTH || facingDirection == Direction.SOUTH) {
                perimeter.add(new BlockPos(miningAreaMinWidth, y, startPos.getZ()));
            } else {
                perimeter.add(new BlockPos(startPos.getX(), y, miningAreaMinWidth));
            }
        }
        
        // System.out.println("[DRILL-PERIMETER-DEBUG] Generated " + perimeter.size() + " perimeter positions");
        // System.out.println("[DRILL-PERIMETER-DEBUG] First 5 positions: " + perimeter.subList(0, Math.min(5, perimeter.size())));
        // System.out.println("[DRILL-PERIMETER-DEBUG] ========================================");
        
        return perimeter;
    }
    
    /**
     * Gets the mining area bounds as an array [minY, maxY, minWidth, maxWidth]
     */
    public int[] getMiningAreaBounds() {
        return new int[]{miningAreaMinY, miningAreaMaxY, miningAreaMinWidth, miningAreaMaxWidth};
    }
    
    /**
     * Gets the facing direction used for mining (opposite of visual facing)
     */
    public Direction getFacingDirection() {
        return facingDirection;
    }
    

} 