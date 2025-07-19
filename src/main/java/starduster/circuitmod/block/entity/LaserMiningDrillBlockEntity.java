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
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.entity.damage.DamageSource;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.machines.LaserMiningDrillBlock;
import starduster.circuitmod.network.ModNetworking;
import starduster.circuitmod.power.EnergyNetwork;
import starduster.circuitmod.power.IEnergyConsumer;
import starduster.circuitmod.power.IPowerConnectable;
import starduster.circuitmod.screen.ModScreenHandlers;
import starduster.circuitmod.screen.LaserMiningDrillScreenHandler;
import net.minecraft.block.Blocks;

import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.sound.ModSounds;

import java.util.ArrayList;
import java.util.List;

public class LaserMiningDrillBlockEntity extends BlockEntity implements SidedInventory, NamedScreenHandlerFactory, ExtendedScreenHandlerFactory<ModScreenHandlers.LaserMiningDrillData>, IEnergyConsumer {
    // Energy properties
    private static final int MAX_ENERGY_DEMAND = 1000; // Maximum energy demand per tick
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
    
    // Area properties - for straight line mining
    private BlockPos startPos; // Starting position (one block in front of the drill)
    private BlockPos currentPos; // Current mining position
    private int currentDepth; // Current depth level (distance from start)
    private Direction facingDirection; // Direction the drill mines (same as visual facing)
    private int miningDepth = 50; // Depth of the mining line (how far it will drill)
    
    // Networking properties
    private int packetCooldown = 0; // Cooldown to avoid sending too many packets
    private static final int PACKET_COOLDOWN_MAX = 10; // Only send packets every 10 ticks max (0.5 seconds)
    
    // Property delegate indices
    private static final int ENERGY_RECEIVED_INDEX = 0;
    private static final int MINING_ENABLED_INDEX = 1;
    private static final int CURRENT_DEPTH_INDEX = 2;
    private static final int PROPERTY_COUNT = 3;
    
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
                case CURRENT_DEPTH_INDEX:
                    return currentDepth;
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
                case CURRENT_DEPTH_INDEX:
                    currentDepth = value;
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
    private int soundClock = 160;

    public LaserMiningDrillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LASER_MINING_DRILL_BLOCK_ENTITY, pos, state);
        
        // Initialize mining area immediately if we have the necessary data
        if (pos != null && state != null) {
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
        nbt.putInt("mining_depth", this.miningDepth);
        
        // Save mining progress data
        if (currentMiningPos != null) {
            nbt.putInt("current_mining_x", currentMiningPos.getX());
            nbt.putInt("current_mining_y", currentMiningPos.getY());
            nbt.putInt("current_mining_z", currentMiningPos.getZ());
        }
        nbt.putInt("current_mining_progress", this.currentMiningProgress);
        nbt.putInt("total_mining_ticks", this.totalMiningTicks);
        nbt.putInt("current_mining_ticks", this.currentMiningTicks);
        nbt.putInt("current_depth", this.currentDepth);
        
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
        if (facingDirection != null) {
            nbt.putInt("facing_direction", facingDirection.ordinal());
        }
        
        // Save inventory
        Inventories.writeNbt(nbt, this.inventory, registries);
    }
    
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        
        // Load mining and energy data
        this.energyDemand = nbt.getInt("energy_demand").orElse(MAX_ENERGY_DEMAND);
        this.energyReceived = nbt.getInt("energy_received").orElse(0);
        this.currentBlockEnergyCost = nbt.getInt("current_block_energy_cost").orElse(1);
        this.miningEnabled = nbt.getBoolean("mining_enabled").orElse(false);
        this.miningDepth = nbt.getInt("mining_depth").orElse(50);
        
        // Load mining progress data
        if (nbt.contains("current_mining_x")) {
            int x = nbt.getInt("current_mining_x").orElse(0);
            int y = nbt.getInt("current_mining_y").orElse(0);
            int z = nbt.getInt("current_mining_z").orElse(0);
            this.currentMiningPos = new BlockPos(x, y, z);
        }
        this.currentMiningProgress = nbt.getInt("current_mining_progress").orElse(0);
        this.totalMiningTicks = nbt.getInt("total_mining_ticks").orElse(0);
        this.currentMiningTicks = nbt.getInt("current_mining_ticks").orElse(0);
        this.currentDepth = nbt.getInt("current_depth").orElse(0);
        
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
        if (nbt.contains("facing_direction")) {
            int ordinal = nbt.getInt("facing_direction").orElse(0);
            this.facingDirection = Direction.values()[ordinal];
        }
        
        // Load inventory
        Inventories.readNbt(nbt, this.inventory, registries);
        
        // Mark that we need to refresh the network connection
        this.needsNetworkRefresh = true;
    }

    public void tick(World world, BlockPos pos, BlockState state, LaserMiningDrillBlockEntity blockEntity) {
        boolean runningBefore = blockEntity.isMiningEnabled();

        // Sound and visual state management (like quarry)
        if(blockEntity.isMiningEnabled()) {
            if(blockEntity.soundClock <= 0){
                blockEntity.soundClock = 160;
            }
            if(blockEntity.soundClock == 160) {
                world.playSound(null, pos, ModSounds.MINER_MACHINE_RUN, SoundCategory.BLOCKS, 1F, 1F);
                // Play laser beam sound alongside the machine sound
                world.playSound(null, pos, ModSounds.LASER_BEAM, SoundCategory.BLOCKS, 0.7F, 1.2F);
            }
            blockEntity.soundClock = blockEntity.soundClock - 1;
            world.setBlockState(pos, world.getBlockState(pos).with(LaserMiningDrillBlock.RUNNING, true), Block.NOTIFY_ALL);
            
            // Add laser beam particle effects and entity damage
            if (world.isClient()) {
                // Spawn particles along the laser beam path
                Direction beamDirection = blockEntity.getMiningDirection().getOpposite();
                float beamLength = blockEntity.getMiningDepth(); // Use actual mining depth
                
                // Spawn particles at random positions along the beam
                for (int i = 0; i < 3; i++) {
                    float distance = (float)(Math.random() * beamLength);
                    float x = pos.getX() + 0.5f;
                    float y = pos.getY() + 0.5f;
                    float z = pos.getZ() + 0.5f;
                    
                    // Offset in beam direction
                    switch (beamDirection) {
                        case NORTH:
                            z -= distance;
                            break;
                        case SOUTH:
                            z += distance;
                            break;
                        case EAST:
                            x += distance;
                            break;
                        case WEST:
                            x -= distance;
                            break;
                        case UP:
                            y += distance;
                            break;
                        case DOWN:
                            y -= distance;
                            break;
                    }
                    
                    // Add some random offset for spread
                    x += (float)(Math.random() - 0.5) * 0.5f;
                    y += (float)(Math.random() - 0.5) * 0.5f;
                    z += (float)(Math.random() - 0.5) * 0.5f;
                    
                    // Spawn electric spark particles
                    world.addParticleClient(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0.0, 0.0, 0.0);
                    
                    // Occasionally spawn flame particles for heat effect
                    if (Math.random() < 0.3) {
                        world.addParticleClient(ParticleTypes.FLAME, x, y, z, 0.0, 0.0, 0.0);
                    }
                }
            } else {
                // Server-side: Damage entities in the laser beam path
                Direction beamDirection = blockEntity.getMiningDirection().getOpposite();
                int beamLength = blockEntity.getMiningDepth();
                
                // Check for entities along the laser beam path
                for (int distance = 0; distance < beamLength; distance++) {
                    BlockPos beamPos = pos.offset(beamDirection, distance);
                    
                    // Get entities in a small area around the beam position
                    List<net.minecraft.entity.Entity> entities = world.getOtherEntities(null, 
                        new net.minecraft.util.math.Box(beamPos).expand(0.5), 
                        entity -> entity.isAlive() && !entity.isSpectator());
                    
                    for (net.minecraft.entity.Entity entity : entities) {
                        // Check if entity is actually in the laser beam path (more precise)
                        double entityX = entity.getX();
                        double entityY = entity.getY() + entity.getHeight() / 2; // Check center of entity
                        double entityZ = entity.getZ();
                        
                        // Calculate distance from beam center
                        double distanceX = Math.abs(entityX - (beamPos.getX() + 0.5));
                        double distanceY = Math.abs(entityY - (beamPos.getY() + 0.5));
                        double distanceZ = Math.abs(entityZ - (beamPos.getZ() + 0.5));
                        
                        // Only damage if entity is within 0.3 blocks of beam center (narrower beam)
                        if (distanceX <= 0.3 && distanceY <= 0.3 && distanceZ <= 0.3) {
                            // Use generic damage source (death message will be handled by the translation key)
                            entity.damage((ServerWorld) world, world.getDamageSources().generic(), 20.0f);
                            
                            Circuitmod.LOGGER.info("[LASER-MINING-DRILL] Damaged entity {} at position {}", 
                                entity.getName().getString(), beamPos);
                        }
                    }
                }
            }
        }
        if(!blockEntity.isMiningEnabled()) {
            world.setBlockState(pos, world.getBlockState(pos).with(LaserMiningDrillBlock.RUNNING, false), Block.NOTIFY_ALL);
            blockEntity.soundClock = 160;
        }

        // Handle network refresh with retry logic for world reload scenarios
        if (blockEntity.needsNetworkRefresh) {
            boolean networkFound = blockEntity.findAndJoinNetwork();
            if (networkFound) {
                blockEntity.needsNetworkRefresh = false;
                Circuitmod.LOGGER.info("[LASER-MINING-DRILL-NETWORK] Successfully joined network at " + pos);
            } else {
                // If we couldn't find a network, try again in a few ticks
                // This helps with world reload scenarios where power cables might not be loaded yet
                if (world.getTime() % 20 == 0) { // Log every second
                    Circuitmod.LOGGER.info("[LASER-MINING-DRILL-NETWORK] No network found at " + pos + ", will retry. Mining enabled: " + blockEntity.miningEnabled);
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
        
        // Safety check - validate that laser mining drill's mining area exists
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
            // Circuitmod.LOGGER.info("[LASER-MINING-DRILL-TICK] Energy received: " + blockEntity.energyReceived + ", network: " + networkInfo);
        }
        
        // Process mining operations based on energy available and if mining is enabled
        if (blockEntity.energyReceived > 0 && blockEntity.miningEnabled) {
            // Try to mine the current block (this will handle gradual mining)
            boolean mined = blockEntity.mineNextBlock(world);
            
            if (mined) {
                needsSync = true;
            }
            
            // Add impact particles at the current mining position
            if (world.isClient() && blockEntity.currentMiningPos != null) {
                BlockPos miningPos = blockEntity.currentMiningPos;
                float x = miningPos.getX() + 0.5f;
                float y = miningPos.getY() + 0.5f;
                float z = miningPos.getZ() + 0.5f;
                
                // Spawn impact particles
                for (int i = 0; i < 2; i++) {
                    world.addParticleClient(ParticleTypes.CRIT, x, y, z, 0.0, 0.0, 0.0);
                    world.addParticleClient(ParticleTypes.SMOKE, x, y, z, 0.0, 0.0, 0.0);
                }
                
                // Spawn additional spark particles occasionally
                if (Math.random() < 0.2) {
                    world.addParticleClient(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0.0, 0.0, 0.0);
                }
            }
        } else {
            // No energy received or mining is disabled
            needsSync = true;
            
            // If mining is enabled but we're not receiving power, try to refresh network connection
            // This helps with world reload scenarios where network connections might be lost
            if (blockEntity.miningEnabled && blockEntity.energyReceived == 0 && blockEntity.network == null) {
                if (world.getTime() % 40 == 0) { // Try every 2 seconds
                    Circuitmod.LOGGER.info("[LASER-MINING-DRILL-NETWORK-RETRY] Mining enabled but no power at " + pos + ", attempting network refresh");
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
        if (blockEntity.packetCooldown == 0 && blockEntity.currentMiningPos != null) {
            blockEntity.packetCooldown = PACKET_COOLDOWN_MAX;
            
            ModNetworking.sendDrillMiningProgressUpdate(
                PlayerLookup.tracking(blockEntity),
                blockEntity.currentMiningProgress,
                blockEntity.currentMiningPos
            );
        }
    }
    
    private void initializeMiningArea(BlockPos pos, BlockState state) {
        // Get the facing direction from the block state
        Direction facing = state.get(net.minecraft.block.HorizontalFacingBlock.FACING);
        // INVERT: Use the opposite direction for mining (like quarry)
        this.facingDirection = facing.getOpposite();
        
        // Calculate the starting position (one block in front of the laser mining drill IN THE MINING DIRECTION)
        // The renderer draws the green rectangle here, so mining should start here too
        BlockPos frontPos = pos.offset(this.facingDirection);
        this.startPos = frontPos;
        this.currentPos = frontPos;
        
        // Mark dirty to trigger client sync
        markDirty();
        
        Circuitmod.LOGGER.info("[LASER-MINING-DRILL-DEBUG] Initialized mining area at {} facing {}, depth: {}", 
            pos, facing, miningDepth);
    }
    
    private void advanceToNextPosition() {
        // For straight line mining, just increment depth
        currentDepth++;
        
        // If we've reached the maximum depth, stop mining (don't reset)
        if (currentDepth >= miningDepth) {
            // Stop mining by setting currentMiningPos to null
            currentMiningPos = null;
            Circuitmod.LOGGER.info("[LASER-MINING-DRILL-DEBUG] Reached maximum depth {}, stopping mining", miningDepth);
            return;
        }
        
        // Update current position
        currentPos = startPos.offset(facingDirection, currentDepth);
        
        Circuitmod.LOGGER.info("[LASER-MINING-DRILL-DEBUG] Advanced to depth {} at position {}", currentDepth, currentPos);
    }
    
    private boolean mineNextBlock(World world) {
        // If mining is disabled, do nothing
        if (!miningEnabled) {
            advanceToNextBlock();
            return false;
        }
        
        // --- INSTANTLY FIND NEXT VALID BLOCK TO MINE ---
        int maxAttempts = miningDepth; // Only try as many times as the mining depth
        int attempts = 0;
        while (attempts < maxAttempts) {
            if (currentMiningPos == null) {
                currentMiningPos = getNextMiningPos();
                if (currentMiningPos == null) {
                    // Reached the end of the mining line
                    Circuitmod.LOGGER.info("[LASER-MINING-DRILL-DEBUG] Reached end of mining line at depth {}", currentDepth);
                    return false;
                }
                currentMiningProgress = 0;
                currentMiningTicks = 0;
                totalMiningTicks = 0;
            }
            // Skip if it's the laser mining drill itself, a safe zone block
            if (currentMiningPos.equals(pos) || isInSafeZone(currentMiningPos)) {
                advanceToNextBlock();
                currentMiningPos = null;
                attempts++;
                continue;
            }
            BlockState blockState = world.getBlockState(currentMiningPos);
            // Skip air, water, lava
            if (blockState.isAir() || blockState.getBlock() == Blocks.WATER || blockState.getBlock() == Blocks.LAVA) {
                advanceToNextBlock();
                currentMiningPos = null;
                attempts++;
                continue;
            }
            // Skip bedrock or unbreakable
            if (blockState.getHardness(world, currentMiningPos) < 0) {
                advanceToNextBlock();
                currentMiningPos = null;
                attempts++;
                continue;
            }
            // Skip if the block is a block entity that's part of our network
            BlockEntity targetEntity = world.getBlockEntity(currentMiningPos);
            if (targetEntity instanceof IPowerConnectable) {
                advanceToNextBlock();
                currentMiningPos = null;
                attempts++;
                continue;
            }
            // Found a valid block to mine
            break;
        }
        if (attempts >= maxAttempts) {
            // Could not find a valid block to mine in the entire line
            Circuitmod.LOGGER.info("[LASER-MINING-DRILL-DEBUG] No valid blocks found in entire mining line");
            return false;
        }

        // --- ONLY NOW START MINING PROGRESS ---
        float hardness = world.getBlockState(currentMiningPos).getHardness(world, currentMiningPos);
        int energyCost = Math.max(1, (int)(hardness * 2.0f) + 1);
        this.currentBlockEnergyCost = energyCost;

        if (totalMiningTicks == 0) {
            totalMiningTicks = Math.max(10, (int)(hardness * 20.0f));
        }

        if (this.energyReceived < 1) {
            return false; // Don't advance position, just wait for more energy
        }

        int energyToConsume = Math.min(this.energyReceived, MAX_ENERGY_DEMAND);
        float energySpeedMultiplier = (float) Math.sqrt(energyToConsume);
        this.energyReceived -= energyToConsume;
        float progressThisTick = energySpeedMultiplier;
        currentMiningTicks += (int) progressThisTick;
        currentMiningProgress = (currentMiningTicks * 100) / totalMiningTicks;
        currentMiningProgress = Math.min(100, currentMiningProgress);

        if (currentMiningTicks >= totalMiningTicks) {
            ItemStack minedItem = new ItemStack(world.getBlockState(currentMiningPos).getBlock().asItem());
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
            if (addedToInventory) {
                world.removeBlock(currentMiningPos, false);
                advanceToNextBlock();
                currentMiningPos = null;
                totalMiningTicks = 0;
                return true;
            } else {
                // Inventory full, stay at current position
                return false;
            }
        }
        return false; // Still mining, not finished yet
    }

    /**
     * Advances to the next block in the mining sequence
     */
    private void advanceToNextBlock() {
        currentMiningPos = null;
        currentMiningProgress = 0;
        currentMiningTicks = 0;
        advanceToNextPosition();
    }

    /**
     * Gets the next mining position in the sequence
     */
    private BlockPos getNextMiningPos() {
        if (startPos == null || facingDirection == null) {
            return null;
        }
        
        // Calculate the next position based on current depth
        if (currentDepth >= miningDepth) {
            return null; // Reached maximum depth
        }
        
        // Get the position at the current depth
        BlockPos nextPos = startPos.offset(facingDirection, currentDepth);
        
        return nextPos;
    }
    
    private boolean isInSafeZone(BlockPos targetPos) {
        // Don't mine the laser mining drill itself
        if (targetPos.equals(pos)) {
            return true; // Safe zone includes the laser mining drill
        }
        
        // Don't mine the block directly in front of the laser mining drill (where the laser mining drill is placed)
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

    public void setEnergyReceived(int energy) {
        this.energyReceived = energy;
    }

    @Override
    public int getEnergyDemand() {
        return energyDemand;
    }

    public int getEnergyReceived() {
        return energyReceived;
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
    public Direction[] getInputSides() {
        return Direction.values(); // Can receive from all sides
    }
    
    public void removeFromNetwork() {
        if (network != null) {
            network.removeBlock(pos);
            network = null;
            Circuitmod.LOGGER.info("[LASER-MINING-DRILL] Removed from energy network at " + pos);
        }
    }

    // Inventory implementation
    @Override
    public int size() {
        return inventory.size();
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < inventory.size(); i++) {
            if (!inventory.get(i).isEmpty()) {
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
        markDirty();
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
                Circuitmod.LOGGER.info("[LASER-MINING-DRILL] Mining toggled to: {}", enabled);
            }
            return true;
        }
        return false;
    }

    // Network synchronization methods
    public void setMiningProgressFromNetwork(int miningProgress, BlockPos miningPos) {
        this.currentMiningProgress = miningProgress;
        this.currentMiningPos = miningPos;
        Circuitmod.LOGGER.info("[LASER-MINING-DRILL] Received mining progress update: {} at {}", miningProgress, miningPos);
    }
    
    public BlockPos getCurrentMiningPos() {
        return currentMiningPos;
    }
    
    public int getCurrentMiningProgress() {
        return currentMiningProgress;
    }
    
    public int getTotalMiningTicks() {
        return totalMiningTicks;
    }
    
    public int getCurrentMiningTicks() {
        return currentMiningTicks;
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
            
            Circuitmod.LOGGER.info("[LASER-MINING-DRILL] Mining enabled set to: {}", enabled);
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
            Circuitmod.LOGGER.info("[LASER-MINING-DRILL] Mining enabled updated from network: {}", enabled);
        }
    }
    
    public void setMiningDepth(int depth) {
        // Validate depth - increased limit to 2000 to match GUI
        depth = Math.max(1, Math.min(2000, depth));
        
        if (this.miningDepth != depth) {
            this.miningDepth = depth;
            
            // Always reinitialize the mining area when depth changes
            // This ensures the laser mining drill updates immediately regardless of mining state
            this.initializeMiningArea(this.pos, this.getCachedState());
            
            // Reset current mining position to start from the beginning of the new area
            this.currentMiningPos = null;
            this.currentMiningProgress = 0;
            this.totalMiningTicks = 0;
            this.currentMiningTicks = 0;
            
            markDirty();
            
            Circuitmod.LOGGER.info("[LASER-MINING-DRILL] Mining depth set to: {}", depth);
        }
    }
    
    public void setMiningDepthFromNetwork(int depth) {
        setMiningDepth(depth);
    }
    
    public int getMiningDepth() {
        return miningDepth;
    }
    
    public int getCurrentDepth() {
        return currentDepth;
    }
    
    /**
     * Gets the facing direction for mining
     */
    public Direction getMiningDirection() {
        return facingDirection;
    }
    
    /**
     * Gets the starting position of the mining area
     */
    public BlockPos getStartPos() {
        return startPos;
    }
    
    /**
     * Gets the current position in the mining sequence
     */
    public BlockPos getCurrentPos() {
        return currentPos;
    }

    public PropertyDelegate getPropertyDelegate() {
        return propertyDelegate;
    }

    // Screen handler implementation
    @Override
    public Text getDisplayName() {
        return Text.translatable("block.circuitmod.laser_mining_drill_block");
    }
    
    @Override
    public ModScreenHandlers.LaserMiningDrillData getScreenOpeningData(ServerPlayerEntity player) {
        return new ModScreenHandlers.LaserMiningDrillData(pos);
    }
    
    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new LaserMiningDrillScreenHandler(syncId, playerInventory, this, this.propertyDelegate, this);
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
                    Circuitmod.LOGGER.info("[LASER-MINING-DRILL] Joined network {} at {}", 
                        neighborNetwork.getNetworkId(), pos);
                    return true;
                }
            }
        }
        
        // If no network found, create a new one
        if (network == null) {
            network = new EnergyNetwork();
            network.addBlock(pos, this);
            Circuitmod.LOGGER.info("[LASER-MINING-DRILL] Created new network {} at {}", 
                network.getNetworkId(), pos);
            return true;
        }
        
        return false;
    }
    
    /**
     * Gets the positions for rendering the mining line
     */
    public List<BlockPos> getMiningLinePositions() {
        List<BlockPos> positions = new ArrayList<>();
        
        if (startPos == null || facingDirection == null) {
            return positions;
        }
        
        // Calculate positions along the mining line
        for (int depth = 0; depth < miningDepth; depth++) {
            BlockPos linePos = startPos.offset(facingDirection, depth);
            positions.add(linePos);
        }
        
        return positions;
    }
}