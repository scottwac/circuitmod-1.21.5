package starduster.circuitmod.block.entity;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.machines.ConstructorBlock;
import starduster.circuitmod.blueprint.Blueprint;
import starduster.circuitmod.item.BlueprintItem;
import starduster.circuitmod.screen.ConstructorScreenHandler;
import starduster.circuitmod.screen.ModScreenHandlers;
import starduster.circuitmod.power.EnergyNetwork;
import starduster.circuitmod.power.IEnergyConsumer;
import starduster.circuitmod.power.IPowerConnectable;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.block.Blocks;

import java.util.*;

/**
 * Block entity for the Constructor block.
 * Reads blueprints and builds structures by placing blocks from its inventory.
 */
public class ConstructorBlockEntity extends BlockEntity implements Inventory, NamedScreenHandlerFactory, ExtendedScreenHandlerFactory<ModScreenHandlers.ConstructorData>, IEnergyConsumer {
    
    // Inventory: 1 slot for blueprint + 12 slots for blocks (same as quarry layout)
    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(13, ItemStack.EMPTY);
    
    // Blueprint and construction state
    private Blueprint currentBlueprint = null;
    private boolean building = false;
    private int buildProgress = 0;
    private int totalBuildBlocks = 0;
    private String statusMessage = "No blueprint loaded";
    
    // Current construction position and progress tracking (like quarry)
    private BlockPos currentBuildPos = null;
    private Set<BlockPos> builtPositions = new HashSet<>(); // Track what we've built
    private int currentBuildProgress = 0; // Progress on current block (0-100)
    private int totalBuildTicks = 0; // Total ticks needed to build current block
    private int currentBuildTicks = 0; // Current ticks spent building
    
    // Required materials tracking (using block/item identifiers instead of display names)
    private Map<String, Integer> requiredMaterials = new HashMap<>();
    private Map<String, Integer> availableMaterials = new HashMap<>();
    
    // Blueprint placement settings (relative to constructor facing direction)
    private int forwardOffset = 1;    // Blocks forward from constructor (positive = away from constructor)
    private int rightOffset = 0;      // Blocks to the right (positive = right when facing constructor direction)
    private int upOffset = 0;         // Blocks up from constructor (positive = up)
    private int blueprintRotation = 0; // Blueprint rotation in 90-degree increments (0=same as constructor, 1=90° CW, etc.)
    
    // Client-side blueprint positions for rendering (synced from server)
    private List<BlockPos> clientBuildPositions = new ArrayList<>();
    
    // Client-side: mapping of build positions to items for ghost rendering
    private Map<BlockPos, net.minecraft.item.Item> clientGhostBlockItems = new HashMap<>();
    public void setGhostBlockItemsFromNetwork(Map<BlockPos, net.minecraft.item.Item> map) {
        if (world != null && world.isClient()) {
            this.clientGhostBlockItems = new HashMap<>(map);
        }
    }
    public Map<BlockPos, net.minecraft.item.Item> getClientGhostBlockItems() {
        return clientGhostBlockItems;
    }
    
    // Energy properties
    private static final int MAX_ENERGY_DEMAND = 1000; // Maximum energy demand per tick
    private static final int ENERGY_PER_BLOCK = 100; // Flat energy cost per block (like quarry)
    private int energyDemand = MAX_ENERGY_DEMAND; // Current energy demand per tick
    private int energyReceived = 0; // Energy received this tick
    private boolean isReceivingPower = false; // Track if we're receiving power
    private boolean hasBlueprintState = false; // Track blueprint state on client side
    private boolean hasPowerState = false; // Track power state on client side
    private String clientStatusMessage = "No blueprint loaded"; // Track status message on client side
    private EnergyNetwork network;
    public boolean needsNetworkRefresh = false;
    
    // Property delegate for GUI synchronization
    private final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            switch (index) {
                case 0: return building ? 1 : 0;
                case 1: return buildProgress;
                case 2: return totalBuildBlocks;
                case 3: 
                    boolean hasBP = world != null && world.isClient() ? hasBlueprintState : (currentBlueprint != null);
                    if (world != null && world.getTime() % 100 == 0) { // Log every 5 seconds
                        Circuitmod.LOGGER.info("[CONSTRUCTOR-PROPERTY] Index 3 (hasBlueprint): {}, hasBlueprintState: {}, currentBlueprint: {}", hasBP ? 1 : 0, hasBlueprintState, currentBlueprint != null ? currentBlueprint.getName() : "null");
                    }
                    return hasBP ? 1 : 0;
                case 4: 
                    boolean hasPower = world != null && world.isClient() ? hasPowerState : isReceivingPower;
                    return hasPower ? 1 : 0;
                default: return 0;
            }
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0: 
                    boolean newBuilding = value == 1;
                    if (building != newBuilding) {
                        building = newBuilding;
                        markDirty();
                    }
                    break;
                case 1: buildProgress = value; break;
                case 2: totalBuildBlocks = value; break;
                case 3: 
                    // Update the blueprint state - this is used by the GUI
                    boolean hasBlueprint = value == 1;
                    if (world != null && world.isClient()) {
                        hasBlueprintState = hasBlueprint;
                        Circuitmod.LOGGER.info("[CONSTRUCTOR-PROPERTY] Setting hasBlueprintState to {} via property delegate", hasBlueprint);
                    }
                    break;
                case 4: 
                    // Update the power state - this is used by the GUI
                    boolean hasPower = value == 1;
                    if (world != null && world.isClient()) {
                        hasPowerState = hasPower;
                        Circuitmod.LOGGER.info("[CONSTRUCTOR-PROPERTY] Setting hasPowerState to {} via property delegate", hasPower);
                    }
                    break;
            }
        }

        @Override
        public int size() {
            return 5;
        }
    };
    
    public ConstructorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONSTRUCTOR_BLOCK_ENTITY, pos, state);
    }
    
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        
        // Save blueprint
        if (currentBlueprint != null) {
            nbt.put("blueprint", currentBlueprint.writeToNbt(registries));
        }
        
        nbt.putBoolean("is_building", building);
        nbt.putInt("build_progress", buildProgress);
        nbt.putInt("total_build_blocks", totalBuildBlocks);
        nbt.putString("status_message", statusMessage);
        
        // Save building progress tracking (like quarry)
        nbt.putInt("current_build_progress", currentBuildProgress);
        nbt.putInt("total_build_ticks", totalBuildTicks);
        nbt.putInt("current_build_ticks", currentBuildTicks);
        
        // Save current build position
        if (currentBuildPos != null) {
            nbt.putInt("build_x", currentBuildPos.getX());
            nbt.putInt("build_y", currentBuildPos.getY());
            nbt.putInt("build_z", currentBuildPos.getZ());
        }
        
        // Save built positions
        NbtCompound builtNbt = new NbtCompound();
        int index = 0;
        for (BlockPos pos : builtPositions) {
            NbtCompound posNbt = new NbtCompound();
            posNbt.putInt("x", pos.getX());
            posNbt.putInt("y", pos.getY());
            posNbt.putInt("z", pos.getZ());
            builtNbt.put("pos_" + index, posNbt);
            index++;
        }
        nbt.put("built_positions", builtNbt);
        
        // Save new positioning system
        nbt.putInt("forward_offset", forwardOffset);
        nbt.putInt("right_offset", rightOffset);
        nbt.putInt("up_offset", upOffset);
        nbt.putInt("blueprint_rotation", blueprintRotation);
        
        // Save inventory
        Inventories.writeNbt(nbt, this.inventory, registries);
    }
    
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        
        // Load blueprint
        if (nbt.contains("blueprint")) {
            try {
                NbtCompound blueprintNbt = nbt.getCompound("blueprint").orElse(new NbtCompound());
                currentBlueprint = Blueprint.readFromNbt(blueprintNbt, registries);
            } catch (Exception e) {
                Circuitmod.LOGGER.error("[CONSTRUCTOR] Failed to load blueprint from NBT", e);
                currentBlueprint = null;
            }
        }
        
        building = nbt.getBoolean("is_building").orElse(false);
        buildProgress = nbt.getInt("build_progress").orElse(0);
        totalBuildBlocks = nbt.getInt("total_build_blocks").orElse(0);
        statusMessage = nbt.getString("status_message").orElse("No blueprint loaded");
        
        // Load building progress tracking (like quarry)
        currentBuildProgress = nbt.getInt("current_build_progress").orElse(0);
        totalBuildTicks = nbt.getInt("total_build_ticks").orElse(0);
        currentBuildTicks = nbt.getInt("current_build_ticks").orElse(0);
        
        // Load current build position
        if (nbt.contains("build_x") && nbt.contains("build_y") && nbt.contains("build_z")) {
            currentBuildPos = new BlockPos(
                nbt.getInt("build_x").orElse(0),
                nbt.getInt("build_y").orElse(0),
                nbt.getInt("build_z").orElse(0)
            );
        }
        
        // Load built positions
        builtPositions.clear();
        if (nbt.contains("built_positions")) {
            NbtCompound builtNbt = nbt.getCompound("built_positions").orElse(new NbtCompound());
            for (String key : builtNbt.getKeys()) {
                if (key.startsWith("pos_")) {
                    NbtCompound posNbt = builtNbt.getCompound(key).orElse(new NbtCompound());
                    BlockPos pos = new BlockPos(
                        posNbt.getInt("x").orElse(0),
                        posNbt.getInt("y").orElse(0),
                        posNbt.getInt("z").orElse(0)
                    );
                    builtPositions.add(pos);
                }
            }
        }
        
        // Load positioning system (with backwards compatibility)
        if (nbt.contains("forward_offset")) {
            // Load new positioning system
            forwardOffset = nbt.getInt("forward_offset").orElse(1);
            rightOffset = nbt.getInt("right_offset").orElse(0);
            upOffset = nbt.getInt("up_offset").orElse(0);
            blueprintRotation = nbt.getInt("blueprint_rotation").orElse(0);
        } else if (nbt.contains("build_offset_x") && nbt.contains("build_offset_y") && nbt.contains("build_offset_z")) {
            // Backwards compatibility: convert old buildOffset to new system
            int offsetX = nbt.getInt("build_offset_x").orElse(0);
            int offsetY = nbt.getInt("build_offset_y").orElse(0);
            int offsetZ = nbt.getInt("build_offset_z").orElse(1);
            
            // Convert absolute offset to relative (assumes old constructor faced north)
            this.forwardOffset = offsetZ;
            this.rightOffset = offsetX;
            this.upOffset = offsetY;
            this.blueprintRotation = 0;
            
            Circuitmod.LOGGER.info("[CONSTRUCTOR] Converted old buildOffset ({},{},{}) to new system (forward:{}, right:{}, up:{}, rotation:{})",
                    offsetX, offsetY, offsetZ, forwardOffset, rightOffset, upOffset, blueprintRotation);
        } else {
            // Set defaults
            forwardOffset = 1;
            rightOffset = 0;
            upOffset = 0;
            blueprintRotation = 0;
        }
        
        // Load inventory
        Inventories.readNbt(nbt, this.inventory, registries);
        
        // Update materials tracking after loading
        updateMaterialsTracking();
    }
    
    /**
     * Server tick method
     */
    public static void tick(World world, BlockPos pos, BlockState state, ConstructorBlockEntity entity) {
        if (world.isClient()) {
            return;
        }
        
        // Handle network refresh
        if (entity.needsNetworkRefresh) {
            entity.findAndJoinNetwork();
            entity.needsNetworkRefresh = false;
        }
        
        // Update block state based on building status
        boolean buildingState = entity.building;
        if (state.get(ConstructorBlock.RUNNING) != buildingState) {
            world.setBlockState(pos, state.with(ConstructorBlock.RUNNING, buildingState), Block.NOTIFY_ALL);
        }
        
        // Set energy demand like quarry - always demand maximum when building
        if (entity.building && entity.currentBlueprint != null) {
            entity.energyDemand = MAX_ENERGY_DEMAND;
        } else {
            entity.energyDemand = 1; // Small demand when idle to show power connection
        }
        
        // Process building if active (power affects speed, not requirement)
        if (entity.building && entity.currentBlueprint != null) {
            entity.processBuildingTick();
        }
        
        // Update materials tracking periodically
        if (world.getTime() % 20 == 0) { // Every second
            entity.updateMaterialsTracking();
        }
        
        // Update materials tracking periodically
        if (world.getTime() % 20 == 0) { // Every second
            entity.updateMaterialsTracking();
        }
        
        // Update power status and reset energy received at the end of each tick
        boolean wasReceivingPower = entity.isReceivingPower;
        entity.isReceivingPower = entity.energyReceived > 0;
        
        // Send power status updates to clients if it changed
        if (wasReceivingPower != entity.isReceivingPower) {
            for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
                if (player.getWorld() == world && player.getBlockPos().getSquaredDistance(pos) <= 64 * 64) {
                    starduster.circuitmod.network.ModNetworking.sendConstructorPowerStatusUpdate(player, pos, entity.isReceivingPower);
                }
            }
        }
        
        // Send initial sync to nearby players periodically (every 100 ticks to avoid spam)
        if (world.getTime() % 100 == 0) {
            for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
                if (player.getWorld() == world && player.getBlockPos().getSquaredDistance(pos) <= 64 * 64) {
                    // Send current building status
                    starduster.circuitmod.network.ModNetworking.sendConstructorBuildingStatusUpdate(player, pos, entity.building, entity.currentBlueprint != null);
                    // Send current power status
                    starduster.circuitmod.network.ModNetworking.sendConstructorPowerStatusUpdate(player, pos, entity.isReceivingPower);
                }
            }
        }
        
        // Debug logging for power status
        if (world.getTime() % 20 == 0) { // Log every second
            Circuitmod.LOGGER.info("[CONSTRUCTOR-POWER] Energy demand: {}, energy received: {}, isReceivingPower: {}", 
                entity.energyDemand, entity.energyReceived, entity.isReceivingPower);
        }
        
        entity.energyReceived = 0;
    }
    
    /**
     * Loads a blueprint from the blueprint slot
     */
    public void loadBlueprint() {
        ItemStack blueprintStack = inventory.get(0); // First slot is for blueprint
        
        if (world == null || world.isClient()) {
            return;
        }
        
        if (blueprintStack.isEmpty() || !(blueprintStack.getItem() instanceof BlueprintItem)) {
            clearBlueprint();
            return;
        }
        
        try {
            Blueprint blueprint = BlueprintItem.getBlueprint(blueprintStack, world.getRegistryManager());
            if (blueprint != null && !blueprint.isEmpty()) {
                this.currentBlueprint = blueprint;
                this.totalBuildBlocks = blueprint.getTotalBlocks();
                this.buildProgress = 0;
                this.builtPositions.clear();
                this.currentBuildPos = null;
                this.statusMessage = "Blueprint loaded: " + blueprint.getName();
                
                updateMaterialsTracking();
                markDirty();
                
                Circuitmod.LOGGER.info("[CONSTRUCTOR] Loaded blueprint: {}", blueprint.getName());
                
                // Send status update to nearby players
                if (world != null && !world.isClient()) {
                    List<BlockPos> buildPositions = getBlueprintBuildPositions();
                    for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
                        if (player.getWorld() == world && player.getBlockPos().getSquaredDistance(pos) <= 64 * 64) {
                            starduster.circuitmod.network.ModNetworking.sendConstructorBuildingStatusUpdate(player, pos, building, true);
                            starduster.circuitmod.network.ModNetworking.sendConstructorStatusMessageUpdate(player, pos, this.statusMessage);
                            starduster.circuitmod.network.ModNetworking.sendConstructorBuildPositionsSync(player, pos, buildPositions);
                            // Send ghost block items
                            java.util.Map<BlockPos, net.minecraft.item.Item> ghostBlockItems = new java.util.HashMap<>();
                            for (BlockPos blueprintPos : blueprint.getAllBlockPositions()) {
                                net.minecraft.block.BlockState state = blueprint.getBlockState(blueprintPos);
                                if (state != null) {
                                    ghostBlockItems.put(blueprintPos, state.getBlock().asItem());
                                }
                            }
                            starduster.circuitmod.network.ModNetworking.sendConstructorGhostBlocksSync(player, pos, ghostBlockItems);
                        }
                    }
                }
            } else {
                clearBlueprint();
                this.statusMessage = "Invalid blueprint";
                // Send status update to nearby players
                if (world != null && !world.isClient()) {
                    for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
                        if (player.getWorld() == world && player.getBlockPos().getSquaredDistance(pos) <= 64 * 64) {
                            starduster.circuitmod.network.ModNetworking.sendConstructorStatusMessageUpdate(player, pos, this.statusMessage);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Circuitmod.LOGGER.error("[CONSTRUCTOR] Failed to load blueprint", e);
            clearBlueprint();
            this.statusMessage = "Blueprint loading failed";
            // Send status update to nearby players
            if (world != null && !world.isClient()) {
                for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
                    if (player.getWorld() == world && player.getBlockPos().getSquaredDistance(pos) <= 64 * 64) {
                        starduster.circuitmod.network.ModNetworking.sendConstructorStatusMessageUpdate(player, pos, this.statusMessage);
                    }
                }
            }
        }
    }
    
    /**
     * Clears the current blueprint
     */
    private void clearBlueprint() {
        this.currentBlueprint = null;
        this.building = false;
        this.buildProgress = 0;
        this.totalBuildBlocks = 0;
        this.builtPositions.clear();
        this.currentBuildPos = null;
        this.statusMessage = "No blueprint loaded";
        this.requiredMaterials.clear();
        this.availableMaterials.clear();
        markDirty();
        
        // Send status update to nearby players
        if (world != null && !world.isClient()) {
            for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
                if (player.getWorld() == world && player.getBlockPos().getSquaredDistance(pos) <= 64 * 64) {
                    starduster.circuitmod.network.ModNetworking.sendConstructorBuildingStatusUpdate(player, pos, building, false);
                    starduster.circuitmod.network.ModNetworking.sendConstructorStatusMessageUpdate(player, pos, this.statusMessage);
                    starduster.circuitmod.network.ModNetworking.sendConstructorBuildPositionsSync(player, pos, new ArrayList<>());
                }
            }
        }
    }
    
    /**
     * Starts building the current blueprint
     */
    public void startBuilding() {
        Circuitmod.LOGGER.info("[CONSTRUCTOR] startBuilding() called");
        
        if (currentBlueprint == null || world == null || world.isClient()) {
            Circuitmod.LOGGER.info("[CONSTRUCTOR] startBuilding() - early return: blueprint={}, world={}, isClient={}", 
                currentBlueprint != null, world != null, world != null && world.isClient());
            return;
        }
        
        // Update materials tracking before checking
        updateMaterialsTracking();
        
        Circuitmod.LOGGER.info("[CONSTRUCTOR] startBuilding() - Required materials: {}", requiredMaterials);
        Circuitmod.LOGGER.info("[CONSTRUCTOR] startBuilding() - Available materials: {}", availableMaterials);
        
        // Check if we have ANY materials available (not all required)
        boolean hasAnyMaterials = hasAnyMaterialsAvailable();
        Circuitmod.LOGGER.info("[CONSTRUCTOR] startBuilding() - hasAnyMaterialsAvailable() returned: {}", hasAnyMaterials);
        
        if (!hasAnyMaterials) {
            this.statusMessage = "No materials available";
            // Send status update to nearby players
            if (world != null && !world.isClient()) {
                for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
                    if (player.getWorld() == world && player.getBlockPos().getSquaredDistance(pos) <= 64 * 64) {
                        starduster.circuitmod.network.ModNetworking.sendConstructorStatusMessageUpdate(player, pos, this.statusMessage);
                    }
                }
            }
            Circuitmod.LOGGER.info("[CONSTRUCTOR] startBuilding() - No materials available, cannot start building");
            return;
        }
        
        this.building = true;
        this.statusMessage = "Building: " + currentBlueprint.getName() + " (partial materials)";
        markDirty();
        
        Circuitmod.LOGGER.info("[CONSTRUCTOR] Started building with partial materials: {}", currentBlueprint.getName());
        
        // Send status update to nearby players
        if (world != null && !world.isClient()) {
            for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
                if (player.getWorld() == world && player.getBlockPos().getSquaredDistance(pos) <= 64 * 64) {
                    starduster.circuitmod.network.ModNetworking.sendConstructorBuildingStatusUpdate(player, pos, building, currentBlueprint != null);
                    starduster.circuitmod.network.ModNetworking.sendConstructorStatusMessageUpdate(player, pos, this.statusMessage);
                }
            }
        }
    }
    
    /**
     * Stops building
     */
    public void stopBuilding() {
        this.building = false;
        this.statusMessage = "Building stopped";
        markDirty();
        
        // Send status update to nearby players
        if (world != null && !world.isClient()) {
            for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
                if (player.getWorld() == world && player.getBlockPos().getSquaredDistance(pos) <= 64 * 64) {
                    starduster.circuitmod.network.ModNetworking.sendConstructorStatusMessageUpdate(player, pos, this.statusMessage);
                }
            }
        }
    }
    
    /**
     * Toggles the building state
     */
    public void toggleBuilding() {
        if (world == null || world.isClient()) {
            Circuitmod.LOGGER.info("[CONSTRUCTOR] toggleBuilding() - early return: world={}, isClient={}", 
                world != null, world != null && world.isClient());
            return;
        }
        
        Circuitmod.LOGGER.info("[CONSTRUCTOR] toggleBuilding() - current building state: {}, hasBlueprint: {}", 
            building, currentBlueprint != null);
        
        if (building) {
            Circuitmod.LOGGER.info("[CONSTRUCTOR] toggleBuilding() - stopping building");
            stopBuilding();
        } else {
            Circuitmod.LOGGER.info("[CONSTRUCTOR] toggleBuilding() - starting building");
            startBuilding();
        }
    }
    
    /**
     * Processes building operations for one tick (like quarry mining)
     */
    private void processBuildingTick() {
        if (currentBlueprint == null || world == null) {
            return;
        }
        
        // Debug logging for energy consumption
        if (world.getTime() % 20 == 0) { // Log every second
            Circuitmod.LOGGER.info("[CONSTRUCTOR-ENERGY] Energy received: {}, energy per block: {}, energy demand: {}", 
                energyReceived, ENERGY_PER_BLOCK, energyDemand);
        }
        
        // If we have energy and are building, try to build the current block
        if (energyReceived > 0 && building) {
            // Try to build the current block (this will handle gradual building)
            boolean built = buildNextBlock(world);
            
            if (built) {
                // Block was completed, mark dirty for sync
                markDirty();
            }
        }
    }
    
    /**
     * Building logic to build a block at the current position (like quarry mining)
     */
    private boolean buildNextBlock(World world) {
        // If building is disabled, do nothing
        if (!building) {
            advanceToNextBlock();
            return false;
        }

        // Debug logging to track the building process
        if (world.getTime() % 20 == 0) { // Log every second
            Circuitmod.LOGGER.info("[CONSTRUCTOR-BUILDING] buildNextBlock() called - currentBuildPos: {}, energyReceived: {}, building: {}", 
                currentBuildPos, energyReceived, building);
        }

        // --- INSTANTLY FIND NEXT VALID BLOCK TO BUILD ---
        int maxAttempts = 4096; // Prevent infinite loops
        int attempts = 0;
        while (attempts < maxAttempts) {
            if (currentBuildPos == null) {
                currentBuildPos = getNextBuildPosition();
                if (currentBuildPos == null) {
                    // Check if we've built everything we can with available materials
                    if (buildProgress >= totalBuildBlocks) {
                        // All blocks built - construction complete
                        this.building = false;
                        this.statusMessage = "Construction complete!";
                        this.buildProgress = totalBuildBlocks;
                        
                        Circuitmod.LOGGER.info("[CONSTRUCTOR-BUILDING] Construction complete! All {} blocks built", totalBuildBlocks);
                        
                        // Send status update to nearby players
                        if (world != null && !world.isClient()) {
                            for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
                                if (player.getWorld() == world && player.getBlockPos().getSquaredDistance(pos) <= 64 * 64) {
                                    starduster.circuitmod.network.ModNetworking.sendConstructorBuildingStatusUpdate(player, pos, building, currentBlueprint != null);
                                    starduster.circuitmod.network.ModNetworking.sendConstructorStatusMessageUpdate(player, pos, this.statusMessage);
                                }
                            }
                        }
                        return false;
                    } else {
                        // No more positions to build with current materials, but construction not complete
                        // This means we're waiting for more materials
                        this.statusMessage = "Waiting for more materials... (" + buildProgress + "/" + totalBuildBlocks + " blocks built)";
                        
                        if (world.getTime() % 20 == 0) { // Log every second
                            Circuitmod.LOGGER.info("[CONSTRUCTOR-BUILDING] Waiting for more materials. Built: {}/{}, continuing to search", buildProgress, totalBuildBlocks);
                        }
                        
                        // Continue searching for buildable positions
                        attempts++;
                        continue;
                    }
                }
                currentBuildProgress = 0;
                currentBuildTicks = 0;
                totalBuildTicks = 0;
                
                if (world.getTime() % 20 == 0) { // Log every second
                    Circuitmod.LOGGER.info("[CONSTRUCTOR-BUILDING] Found next build position: {}", currentBuildPos);
                }
            }
            
            // Check if we have the required material for this position
            BlockState requiredState = currentBlueprint.getBlockState(currentBuildPos);
            if (requiredState == null) {
                // Invalid position - skip it
                if (world.getTime() % 20 == 0) { // Log every second
                    Circuitmod.LOGGER.info("[CONSTRUCTOR-BUILDING] Invalid position - no block state at {}", currentBuildPos);
                }
                advanceToNextBlock();
                currentBuildPos = null;
                attempts++;
                continue;
            }
            
            ItemStack requiredItem = new ItemStack(requiredState.getBlock().asItem());
            boolean foundItem = false;
            for (int slot = 1; slot < inventory.size(); slot++) { // Skip blueprint slot
                ItemStack stack = inventory.get(slot);
                if (!stack.isEmpty() && stack.getItem() == requiredItem.getItem()) {
                    foundItem = true;
                    break;
                }
            }
            
            if (!foundItem) {
                // Skip this position - no material available, but continue searching
                if (world.getTime() % 20 == 0) { // Log every second
                    Circuitmod.LOGGER.info("[CONSTRUCTOR-BUILDING] No material available for {} at position {}, continuing search", requiredItem.getName().getString(), currentBuildPos);
                }
                advanceToNextBlock();
                currentBuildPos = null;
                attempts++;
                continue;
            }
            
            // Check if the target position is buildable
            // Build the blueprint starting from the position that was the minimum corner of the original scan
            // The Constructor should build the blueprint exactly where it was originally scanned
            BlockPos worldPos = pos.add(getBuildOffset()).add(currentBuildPos);
            BlockState currentState = world.getBlockState(worldPos);
            
            // Allow building if the position is air, replaceable, or a common replaceable block
            boolean canBuild = currentState.isAir() || 
                              currentState.isReplaceable() || 
                              currentState.isOf(Blocks.DIRT) ||
                              currentState.isOf(Blocks.SAND) ||
                              currentState.isOf(Blocks.GRAVEL) ||
                              currentState.isOf(Blocks.STONE) ||
                              currentState.isOf(Blocks.COBBLESTONE) ||
                              currentState.isOf(Blocks.GRASS_BLOCK) ||
                              currentState.isOf(Blocks.DEEPSLATE) ||
                              currentState.isOf(Blocks.TUFF) ||
                              currentState.isOf(Blocks.CLAY) ||
                              currentState.isOf(Blocks.SOUL_SAND) ||
                              currentState.isOf(Blocks.SOUL_SOIL);
            
            if (!canBuild) {
                // Position not available - skip it
                if (world.getTime() % 20 == 0) { // Log every second
                    Circuitmod.LOGGER.info("[CONSTRUCTOR-BUILDING] Position not available at world pos {} (current state: {})", worldPos, currentState.getBlock().getName());
                }
                advanceToNextBlock();
                currentBuildPos = null;
                attempts++;
                continue;
            }
            
            // Found a valid block to build
                            if (world.getTime() % 20 == 0) { // Log every second
                    Circuitmod.LOGGER.info("[CONSTRUCTOR-BUILDING] Found valid block to build: {} at relative position {} (world pos: {})", requiredState.getBlock().getName(), currentBuildPos, worldPos);
                }
            break;
        }
        
        if (attempts >= maxAttempts) {
            // Could not find a valid block to build
            Circuitmod.LOGGER.warn("[CONSTRUCTOR-BUILDING] Could not find valid block to build after {} attempts", maxAttempts);
            return false;
        }

        // --- ONLY NOW START BUILDING PROGRESS ---
        // Flat energy cost like quarry
        int energyCost = ENERGY_PER_BLOCK;
        this.totalBuildTicks = Math.max(10, energyCost);

        if (this.energyReceived < 1) {
            return false; // Don't advance position, just wait for more energy
        }

        int energyToConsume = Math.min(this.energyReceived, MAX_ENERGY_DEMAND);
        float energySpeedMultiplier = (float) Math.sqrt(energyToConsume);
        this.energyReceived -= energyToConsume;
        float progressThisTick = energySpeedMultiplier;
        currentBuildTicks += (int) progressThisTick;
        currentBuildProgress = (currentBuildTicks * 100) / totalBuildTicks;
        currentBuildProgress = Math.min(100, currentBuildProgress);

        if (world.getTime() % 20 == 0) { // Log every second
            Circuitmod.LOGGER.info("[CONSTRUCTOR-BUILDING] Building progress: {}/{} ticks ({}%)", currentBuildTicks, totalBuildTicks, currentBuildProgress);
        }

        if (currentBuildTicks >= totalBuildTicks) {
            // Block is complete - place it
            BlockState requiredState = currentBlueprint.getBlockState(currentBuildPos);
            ItemStack requiredItem = new ItemStack(requiredState.getBlock().asItem());
            // Build the blueprint starting from the position that was the minimum corner of the original scan
            BlockPos worldPos = pos.add(getBuildOffset()).add(currentBuildPos);
            
            // Find and consume the item
            boolean consumed = false;
            for (int slot = 1; slot < inventory.size(); slot++) { // Skip blueprint slot
                ItemStack stack = inventory.get(slot);
                if (!stack.isEmpty() && stack.getItem() == requiredItem.getItem()) {
                    stack.decrement(1);
                    if (stack.isEmpty()) {
                        inventory.set(slot, ItemStack.EMPTY);
                    }
                    consumed = true;
                    break;
                }
            }
            
            if (consumed) {
                // Place the block
                world.setBlockState(worldPos, requiredState, Block.NOTIFY_ALL);
                
                // Mark as built and advance
                builtPositions.add(currentBuildPos);
                buildProgress++;
                advanceToNextBlock();
                currentBuildPos = null;
                totalBuildTicks = 0;
                
                // Update status message to show progress and material availability
                int availableCount = 0;
                int requiredCount = 0;
                for (Map.Entry<String, Integer> required : requiredMaterials.entrySet()) {
                    int available = availableMaterials.getOrDefault(required.getKey(), 0);
                    availableCount += available;
                    requiredCount += required.getValue();
                }
                
                this.statusMessage = String.format("Building... (%d/%d blocks) - Materials: %d/%d", 
                    buildProgress, totalBuildBlocks, availableCount, requiredCount);
                
                // Send status update to nearby players periodically (every 10 blocks to avoid spam)
                if (buildProgress % 10 == 0 && world != null && !world.isClient()) {
                    for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
                        if (player.getWorld() == world && player.getBlockPos().getSquaredDistance(pos) <= 64 * 64) {
                            starduster.circuitmod.network.ModNetworking.sendConstructorStatusMessageUpdate(player, pos, this.statusMessage);
                        }
                    }
                }
                
                if (world.getTime() % 20 == 0) { // Log every second
                    Circuitmod.LOGGER.info("[CONSTRUCTOR-BUILDING] Successfully placed block at {}: {}", worldPos, requiredState.getBlock().getName());
                }
                return true;
            } else {
                // Could not consume item, stay at current position
                Circuitmod.LOGGER.warn("[CONSTRUCTOR-BUILDING] Could not consume item for block placement");
                return false;
            }
        }
        return false; // Still building, not finished yet
    }
    
    /**
     * Helper method to advance to the next block
     */
    private void advanceToNextBlock() {
        currentBuildPos = null;
        currentBuildProgress = 0;
        totalBuildTicks = 0;
        currentBuildTicks = 0;
    }
    
    /**
     * Gets the next position to build at
     */
    private BlockPos getNextBuildPosition() {
        if (currentBlueprint == null) {
            return null;
        }
        
        Set<BlockPos> allPositions = currentBlueprint.getAllBlockPositions();
        for (BlockPos pos : allPositions) {
            if (!builtPositions.contains(pos)) {
                return pos;
            }
        }
        
        return null; // All positions built
    }
    


    /**
     * Updates material tracking for the GUI
     */
    private void updateMaterialsTracking() {
        requiredMaterials.clear();
        availableMaterials.clear();
        
        if (currentBlueprint != null) {
            // Count required materials using block/item identifiers
            for (BlockPos pos : currentBlueprint.getAllBlockPositions()) {
                BlockState state = currentBlueprint.getBlockState(pos);
                if (state != null) {
                    // Use the block's item form identifier (e.g., "minecraft:stone")
                    Block block = state.getBlock();
                    Item blockItem = block.asItem();
                    if (blockItem != Items.AIR) {
                        String itemId = Registries.ITEM.getId(blockItem).toString();
                        requiredMaterials.put(itemId, requiredMaterials.getOrDefault(itemId, 0) + 1);
                    } else {
                        // Fallback to block ID if no item form exists
                        String blockId = Registries.BLOCK.getId(block).toString();
                        requiredMaterials.put(blockId, requiredMaterials.getOrDefault(blockId, 0) + 1);
                    }
                }
            }
            
            // Count available materials in inventory using item identifiers
            Circuitmod.LOGGER.info("[CONSTRUCTOR-MATERIALS] Scanning inventory ({} slots):", inventory.size());
            for (int slot = 1; slot < inventory.size(); slot++) {
                ItemStack stack = inventory.get(slot);
                if (!stack.isEmpty()) {
                    // Use the item's registry identifier (e.g., "minecraft:stone")
                    String itemId = Registries.ITEM.getId(stack.getItem()).toString();
                    availableMaterials.put(itemId, availableMaterials.getOrDefault(itemId, 0) + stack.getCount());
                    Circuitmod.LOGGER.info("[CONSTRUCTOR-MATERIALS] Slot {}: {} x{}", slot, itemId, stack.getCount());
                } else {
                    Circuitmod.LOGGER.info("[CONSTRUCTOR-MATERIALS] Slot {}: empty", slot);
                }
            }
            
            Circuitmod.LOGGER.info("[CONSTRUCTOR-MATERIALS] Final required: {}", requiredMaterials);
            Circuitmod.LOGGER.info("[CONSTRUCTOR-MATERIALS] Final available: {}", availableMaterials);
        }
        
        // Send materials sync to all nearby players (server side only)
        if (world != null && !world.isClient()) {
            for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
                if (player.getWorld() == world && player.getBlockPos().getSquaredDistance(pos) <= 64 * 64) {
                    starduster.circuitmod.network.ModNetworking.sendConstructorMaterialsSync(player, pos, requiredMaterials, availableMaterials);
                }
            }
        }
    }
    
    /**
     * Checks if we have all required materials
     */
    private boolean hasRequiredMaterials() {
        updateMaterialsTracking();
        
        Circuitmod.LOGGER.info("[CONSTRUCTOR-MATERIALS] Checking required materials:");
        for (Map.Entry<String, Integer> required : requiredMaterials.entrySet()) {
            int available = availableMaterials.getOrDefault(required.getKey(), 0);
            Circuitmod.LOGGER.info("[CONSTRUCTOR-MATERIALS] {}: required={}, available={}, sufficient={}", 
                required.getKey(), required.getValue(), available, available >= required.getValue());
            if (available < required.getValue()) {
                Circuitmod.LOGGER.info("[CONSTRUCTOR-MATERIALS] Missing required materials - returning false");
                return false;
            }
        }
        
        Circuitmod.LOGGER.info("[CONSTRUCTOR-MATERIALS] All materials available - returning true");
        return true;
    }
    
    /**
     * Checks if we have ANY materials available (not all required)
     * This allows building to start with partial materials
     */
    private boolean hasAnyMaterialsAvailable() {
        updateMaterialsTracking();
        
        Circuitmod.LOGGER.info("[CONSTRUCTOR-MATERIALS] Checking if any materials are available:");
        for (Map.Entry<String, Integer> required : requiredMaterials.entrySet()) {
            int available = availableMaterials.getOrDefault(required.getKey(), 0);
            Circuitmod.LOGGER.info("[CONSTRUCTOR-MATERIALS] {}: required={}, available={}", 
                required.getKey(), required.getValue(), available);
            if (available > 0) {
                Circuitmod.LOGGER.info("[CONSTRUCTOR-MATERIALS] Found available materials - returning true");
                return true;
            }
        }
        
        Circuitmod.LOGGER.info("[CONSTRUCTOR-MATERIALS] No materials available - returning false");
        return false;
    }
    
    // Inventory implementation
    @Override
    public int size() {
        return inventory.size();
    }

    @Override
    public boolean isEmpty() {
        return inventory.stream().allMatch(ItemStack::isEmpty);
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
        
        // Auto-load blueprint when inserted in slot 0
        if (slot == 0 && world != null && !world.isClient()) {
            loadBlueprint();
        }
        
        // Update materials tracking when inventory changes
        if (slot > 0 && world != null && !world.isClient()) {
            updateMaterialsTracking();
            Circuitmod.LOGGER.info("[CONSTRUCTOR-INVENTORY] Slot {} updated with: {}", slot, stack.isEmpty() ? "empty" : stack.getName().getString() + " x" + stack.getCount());
            
            // If we're building and new materials were added, update status message
            if (building && currentBlueprint != null) {
                int availableCount = 0;
                int requiredCount = 0;
                for (Map.Entry<String, Integer> required : requiredMaterials.entrySet()) {
                    int available = availableMaterials.getOrDefault(required.getKey(), 0);
                    availableCount += available;
                    requiredCount += required.getValue();
                }
                
                this.statusMessage = String.format("Building... (%d/%d blocks) - Materials: %d/%d", 
                    buildProgress, totalBuildBlocks, availableCount, requiredCount);
                
                Circuitmod.LOGGER.info("[CONSTRUCTOR-INVENTORY] Updated status after inventory change: {}", this.statusMessage);
            }
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
    
    // Screen handler factory implementation
    @Override
    public Text getDisplayName() {
        return Text.translatable("block.circuitmod.constructor_block");
    }
    
    @Override
    public ModScreenHandlers.ConstructorData getScreenOpeningData(ServerPlayerEntity player) {
        return new ModScreenHandlers.ConstructorData(this.pos);
    }
    
    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        // Debug logging for inventory contents when screen is opened
        if (world != null && !world.isClient()) {
            Circuitmod.LOGGER.info("[CONSTRUCTOR-SCREEN] Screen opened - Inventory contents:");
            for (int i = 0; i < inventory.size(); i++) {
                ItemStack stack = inventory.get(i);
                if (!stack.isEmpty()) {
                    Circuitmod.LOGGER.info("[CONSTRUCTOR-SCREEN] Slot {}: {} x{}", i, stack.getName().getString(), stack.getCount());
                } else {
                    Circuitmod.LOGGER.info("[CONSTRUCTOR-SCREEN] Slot {}: empty", i);
                }
            }
            
            // Update materials tracking when screen is opened
            updateMaterialsTracking();
        }
        
        return new ConstructorScreenHandler(syncId, playerInventory, this, this.propertyDelegate, this);
    }
    
    // Getters for GUI
    public boolean isBuilding() {
        return building;
    }
    
    public int getBuildProgress() {
        return buildProgress;
    }
    
    public int getTotalBuildBlocks() {
        return totalBuildBlocks;
    }
    
    public boolean hasBlueprint() {
        if (world != null && world.isClient()) {
            return hasBlueprintState;
        }
        return currentBlueprint != null;
    }
    
    public String getStatusMessage() {
        if (world != null && world.isClient()) {
            return clientStatusMessage;
        }
        return statusMessage;
    }
    
    public PropertyDelegate getPropertyDelegate() {
        return propertyDelegate;
    }
    
    public Map<String, Integer> getRequiredMaterials() {
        return new HashMap<>(requiredMaterials);
    }
    
    public Map<String, Integer> getAvailableMaterials() {
        return new HashMap<>(availableMaterials);
    }
    
    public String getBlueprintName() {
        return currentBlueprint != null ? currentBlueprint.getName() : "No Blueprint";
    }
    
    public boolean isReceivingPower() {
        return isReceivingPower;
    }
    
    public Blueprint getCurrentBlueprint() {
        return currentBlueprint;
    }
    public Set<BlockPos> getBuiltPositions() {
        return builtPositions;
    }
    
    /**
     * Gets the constructor's facing direction from the block state
     */
    private Direction getConstructorFacing() {
        if (world != null && world.getBlockState(pos).getBlock() instanceof starduster.circuitmod.block.machines.ConstructorBlock) {
            return world.getBlockState(pos).get(HorizontalFacingBlock.FACING);
        }
        return Direction.NORTH; // Default fallback
    }
    
    /**
     * Converts relative offset to world position based on constructor facing
     * @param forward blocks forward from constructor (positive = away from constructor)
     * @param right blocks to the right (positive = right when facing constructor direction)  
     * @param up blocks up from constructor (positive = up)
     * @return world position relative to constructor
     */
    private BlockPos getWorldPositionFromRelative(int forward, int right, int up) {
        Direction facing = getConstructorFacing();
        
        // Calculate forward direction (where constructor is facing)
        Vec3i forwardVec = facing.getVector();
        
        // Calculate right direction (90 degrees clockwise from facing)
        Vec3i rightVec = facing.rotateYClockwise().getVector();
        
        // Combine all offsets
        int totalX = forwardVec.getX() * forward + rightVec.getX() * right;
        int totalY = up;
        int totalZ = forwardVec.getZ() * forward + rightVec.getZ() * right;
        
        return new BlockPos(totalX, totalY, totalZ);
    }
    
    /**
     * Gets the base build position (constructor position + relative offsets)
     */
    private BlockPos getBaseBuildPosition() {
        return pos.add(getWorldPositionFromRelative(forwardOffset, rightOffset, upOffset));
    }
    
    /**
     * Rotates a blueprint position based on the blueprint rotation setting
     * @param blueprintPos the relative position within the blueprint
     * @return rotated position
     */
    private BlockPos rotateBlueprintPosition(BlockPos blueprintPos) {
        int rotations = blueprintRotation % 4; // Normalize to 0-3
        if (rotations == 0) {
            return blueprintPos;
        }
        
        int x = blueprintPos.getX();
        int y = blueprintPos.getY();
        int z = blueprintPos.getZ();
        
        // Apply rotations (90 degrees clockwise each time)
        for (int i = 0; i < rotations; i++) {
            int newX = -z;
            int newZ = x;
            x = newX;
            z = newZ;
        }
        
        return new BlockPos(x, y, z);
    }
    
    /**
     * Gets the current build offset (for backwards compatibility)
     * @return the build offset relative to the Constructor
     */
    public BlockPos getBuildOffset() {
        return getWorldPositionFromRelative(forwardOffset, rightOffset, upOffset);
    }
    
    /**
     * Sets the build offset (for backwards compatibility)
     * @param offset the new build offset relative to the Constructor
     */
    public void setBuildOffset(BlockPos offset) {
        // Convert absolute offset back to relative (assumes constructor facing north)
        this.forwardOffset = offset.getZ();
        this.rightOffset = offset.getX();
        this.upOffset = offset.getY();
        markDirty();
        Circuitmod.LOGGER.info("[CONSTRUCTOR] Set build offset to: {} (forward:{}, right:{}, up:{})", 
                offset, forwardOffset, rightOffset, upOffset);
    }
    
    // New getter/setter methods for individual offset components
    public int getForwardOffset() { return forwardOffset; }
    public int getRightOffset() { return rightOffset; }
    public int getUpOffset() { return upOffset; }
    public int getBlueprintRotation() { return blueprintRotation; }
    
    public void setForwardOffset(int forward) {
        this.forwardOffset = forward;
        markDirty();
    }
    
    public void setRightOffset(int right) {
        this.rightOffset = right;
        markDirty();
    }
    
    public void setUpOffset(int up) {
        this.upOffset = up;
        markDirty();
    }
    
    public void setBlueprintRotation(int rotation) {
        this.blueprintRotation = rotation % 4;
        markDirty();
    }
    
    /**
     * Update building status from network (client-side only)
     */
    public void setBuildingStatusFromNetwork(boolean building, boolean hasBlueprint) {
        if (world != null && world.isClient()) {
            this.building = building;
            this.hasBlueprintState = hasBlueprint;
            Circuitmod.LOGGER.info("[CONSTRUCTOR] Updated from network: building={}, hasBlueprint={}", building, hasBlueprint);
        }
    }
    
    /**
     * Update power status from network (client-side only)
     */
    public void setPowerStatusFromNetwork(boolean hasPower) {
        if (world != null && world.isClient()) {
            this.hasPowerState = hasPower;
            Circuitmod.LOGGER.info("[CONSTRUCTOR] Updated power status from network: hasPower={}", hasPower);
        }
    }
    
    /**
     * Update status message from network (client-side only)
     */
    public void setStatusMessageFromNetwork(String message) {
        if (world != null && world.isClient()) {
            this.clientStatusMessage = message;
            Circuitmod.LOGGER.info("[CONSTRUCTOR] Updated status message from network: {}", message);
        }
    }
    
    /**
     * Get all world positions where blueprint blocks will be built (for renderer)
     */
    public List<BlockPos> getBlueprintBuildPositions() {
        // On client side, use synced positions
        if (world != null && world.isClient()) {
            return new ArrayList<>(clientBuildPositions);
        }
        
        // On server side, calculate from blueprint
        List<BlockPos> positions = new ArrayList<>();
        
        if (currentBlueprint == null) {
            return positions;
        }
        
        // Get the base build position (constructor + relative offsets)
        BlockPos baseBuildPos = getBaseBuildPosition();
        
        // Convert blueprint relative positions to world positions
        for (BlockPos blueprintPos : currentBlueprint.getAllBlockPositions()) {
            // Apply blueprint rotation first
            BlockPos rotatedBlueprintPos = rotateBlueprintPosition(blueprintPos);
            
            // Then add to base build position
            BlockPos worldPos = baseBuildPos.add(rotatedBlueprintPos);
            positions.add(worldPos);
        }
        
        return positions;
    }
    
    /**
     * Set build positions from network (client-side only)
     */
    public void setBuildPositionsFromNetwork(List<BlockPos> positions) {
        if (world != null && world.isClient()) {
            this.clientBuildPositions = new ArrayList<>(positions);
            Circuitmod.LOGGER.info("[CONSTRUCTOR] Updated build positions from network: {} positions", positions.size());
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
            String oldNetworkId = this.network.getNetworkId();
            String newNetworkId = network.getNetworkId();
            Circuitmod.LOGGER.info("[CONSTRUCTOR-NETWORK] Constructor at " + pos + " changing networks: " + oldNetworkId + " -> " + newNetworkId);
        } else if (network != null && this.network == null) {
            Circuitmod.LOGGER.info("[CONSTRUCTOR-NETWORK] Constructor at " + pos + " connecting to network: " + network.getNetworkId());
        } else if (this.network != null && network == null) {
            String oldNetworkId = this.network.getNetworkId();
            Circuitmod.LOGGER.info("[CONSTRUCTOR-NETWORK] Constructor at " + pos + " disconnecting from network: " + oldNetworkId);
        }
        
        this.network = network;
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
            
            // Debug logs to diagnose the issue (only log occasionally to avoid spam)
            if (world.getTime() % 20 == 0) { // Only log every second
                Circuitmod.LOGGER.info("[CONSTRUCTOR-ENERGY] Energy offered: " + energyOffered + ", consumed: " + energyToConsume + ", accumulated: " + this.energyReceived);
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
    
    /**
     * Finds and joins a power network
     */
    private void findAndJoinNetwork() {
        if (world == null || world.isClient()) {
            return;
        }
        
        // Search for nearby power connectables
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.offset(direction);
            BlockEntity neighborEntity = world.getBlockEntity(neighborPos);
            
            if (neighborEntity instanceof IPowerConnectable neighbor) {
                EnergyNetwork neighborNetwork = neighbor.getNetwork();
                if (neighborNetwork != null) {
                    neighborNetwork.addBlock(pos, this);
                    Circuitmod.LOGGER.info("[CONSTRUCTOR-NETWORK] Constructor at {} joined network {} via {}", pos, neighborNetwork.getNetworkId(), direction);
                    return;
                }
            }
        }
        
        // If no network found, create a new one
        EnergyNetwork newNetwork = new EnergyNetwork();
        newNetwork.addBlock(pos, this);
        Circuitmod.LOGGER.info("[CONSTRUCTOR-NETWORK] Constructor at {} created new network {}", pos, newNetwork.getNetworkId());
    }
} 