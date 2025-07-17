package starduster.circuitmod.block.entity;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
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
    
    // Current construction position
    private BlockPos currentBuildPos = null;
    private Set<BlockPos> builtPositions = new HashSet<>(); // Track what we've built
    
    // Required materials tracking
    private Map<String, Integer> requiredMaterials = new HashMap<>();
    private Map<String, Integer> availableMaterials = new HashMap<>();
    
    // Energy properties
    private static final int MAX_ENERGY_DEMAND = 500; // Energy demand per tick when building
    private int energyDemand = 0; // Current energy demand per tick
    private int energyReceived = 0; // Energy received this tick
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
                case 3: return currentBlueprint != null ? 1 : 0;
                default: return 0;
            }
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0: building = value == 1; break;
                case 1: buildProgress = value; break;
                case 2: totalBuildBlocks = value; break;
                case 3: break; // Read-only
            }
        }

        @Override
        public int size() {
            return 4;
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
        
        // Set energy demand based on building status
        entity.energyDemand = entity.building ? MAX_ENERGY_DEMAND : 0;
        
        // Process building if active and has energy
        if (entity.building && entity.currentBlueprint != null && entity.energyReceived > 0) {
            entity.processBuildingTick();
        }
        
        // Update materials tracking periodically
        if (world.getTime() % 20 == 0) { // Every second
            entity.updateMaterialsTracking();
        }
        
        // Reset energy received at the end of each tick
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
            } else {
                clearBlueprint();
                this.statusMessage = "Invalid blueprint";
            }
        } catch (Exception e) {
            Circuitmod.LOGGER.error("[CONSTRUCTOR] Failed to load blueprint", e);
            clearBlueprint();
            this.statusMessage = "Blueprint loading failed";
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
    }
    
    /**
     * Starts building the current blueprint
     */
    public void startBuilding() {
        if (currentBlueprint == null || world == null || world.isClient()) {
            return;
        }
        
        if (!hasRequiredMaterials()) {
            this.statusMessage = "Missing required materials";
            return;
        }
        
        this.building = true;
        this.statusMessage = "Building: " + currentBlueprint.getName();
        markDirty();
        
        Circuitmod.LOGGER.info("[CONSTRUCTOR] Started building: {}", currentBlueprint.getName());
    }
    
    /**
     * Stops building
     */
    public void stopBuilding() {
        this.building = false;
        this.statusMessage = "Building stopped";
        markDirty();
    }
    
    /**
     * Processes one tick of building
     */
    private void processBuildingTick() {
        if (currentBlueprint == null || world == null) {
            return;
        }
        
        // Try to place one block this tick
        if (currentBuildPos == null) {
            currentBuildPos = getNextBuildPosition();
        }
        
        if (currentBuildPos == null) {
            // No more blocks to place - construction complete
            this.building = false;
            this.statusMessage = "Construction complete!";
            this.buildProgress = totalBuildBlocks;
            markDirty();
            return;
        }
        
        // Try to place the block at current position
        BlockState requiredState = currentBlueprint.getBlockState(currentBuildPos);
        
        if (requiredState != null) {
            ItemStack requiredItem = new ItemStack(requiredState.getBlock().asItem());
            
            // Find the required block in inventory
            for (int slot = 1; slot < inventory.size(); slot++) { // Skip blueprint slot
                ItemStack stack = inventory.get(slot);
                if (!stack.isEmpty() && ItemStack.areItemsEqual(stack, requiredItem)) {
                    // Found the required block - place it
                    // Calculate world position: constructor is at pos, build relative to it
                    BlockPos worldPos = pos.add(currentBuildPos);
                    
                    if (world.getBlockState(worldPos).isAir() || world.getBlockState(worldPos).isReplaceable()) {
                        world.setBlockState(worldPos, requiredState, Block.NOTIFY_ALL);
                        
                        // Consume the item
                        stack.decrement(1);
                        if (stack.isEmpty()) {
                            inventory.set(slot, ItemStack.EMPTY);
                        }
                        
                        // Mark as built and advance
                        builtPositions.add(currentBuildPos);
                        buildProgress++;
                        currentBuildPos = null; // Will get next position on next tick
                        
                        this.statusMessage = "Building... (" + buildProgress + "/" + totalBuildBlocks + ")";
                        markDirty();
                        return;
                    }
                }
            }
            
            // Couldn't find the required block - stop building
            this.building = false;
            this.statusMessage = "Missing: " + requiredState.getBlock().getName().getString();
            markDirty();
        } else {
            // Invalid position - skip it
            builtPositions.add(currentBuildPos);
            currentBuildPos = null;
        }
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
            // Count required materials
            for (BlockPos pos : currentBlueprint.getAllBlockPositions()) {
                BlockState state = currentBlueprint.getBlockState(pos);
                if (state != null) {
                    String blockName = state.getBlock().getName().getString();
                    requiredMaterials.put(blockName, requiredMaterials.getOrDefault(blockName, 0) + 1);
                }
            }
            
            // Count available materials in inventory
            for (int slot = 1; slot < inventory.size(); slot++) { // Skip blueprint slot
                ItemStack stack = inventory.get(slot);
                if (!stack.isEmpty()) {
                    String itemName = stack.getName().getString();
                    availableMaterials.put(itemName, availableMaterials.getOrDefault(itemName, 0) + stack.getCount());
                }
            }
        }
    }
    
    /**
     * Checks if we have all required materials
     */
    private boolean hasRequiredMaterials() {
        updateMaterialsTracking();
        
        for (Map.Entry<String, Integer> required : requiredMaterials.entrySet()) {
            int available = availableMaterials.getOrDefault(required.getKey(), 0);
            if (available < required.getValue()) {
                return false;
            }
        }
        
        return true;
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
        return currentBlueprint != null;
    }
    
    public String getStatusMessage() {
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
            Circuitmod.LOGGER.info("[CONSTRUCTOR-NETWORK] Constructor at " + pos + " changing networks: " + this.network.getNetworkId() + " -> " + network.getNetworkId());
        } else if (network != null && this.network == null) {
            Circuitmod.LOGGER.info("[CONSTRUCTOR-NETWORK] Constructor at " + pos + " connecting to network: " + network.getNetworkId());
        } else if (this.network != null && network == null) {
            Circuitmod.LOGGER.info("[CONSTRUCTOR-NETWORK] Constructor at " + pos + " disconnecting from network: " + this.network.getNetworkId());
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