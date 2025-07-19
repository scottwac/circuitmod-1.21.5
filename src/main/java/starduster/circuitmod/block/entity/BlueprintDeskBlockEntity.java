package starduster.circuitmod.block.entity;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.PacketByteBuf;
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
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.machines.BlueprintDeskBlock;
import starduster.circuitmod.blueprint.Blueprint;
import starduster.circuitmod.blueprint.BlueprintScanner;
import starduster.circuitmod.item.BlueprintItem;
import starduster.circuitmod.item.ModItems;
import starduster.circuitmod.screen.BlueprintDeskScreenHandler;
import starduster.circuitmod.screen.ModScreenHandlers;
import starduster.circuitmod.network.ModNetworking;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Block entity for the Blueprint Desk.
 * Handles area scanning, partner detection, and blueprint creation.
 */
public class BlueprintDeskBlockEntity extends BlockEntity implements Inventory, NamedScreenHandlerFactory, ExtendedScreenHandlerFactory<ModScreenHandlers.BlueprintDeskData> {
    
    // Inventory: 1 slot for storing blank blueprint items
    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(1, ItemStack.EMPTY);
    
    // Partner desk connection
    private BlockPos partnerPos = null;
    private boolean hasValidPartner = false;
    private boolean needsPartnerSearch = false; // Flag to delay partner search
    
    // Connection mode tracking
    private static final Map<PlayerEntity, BlockPos> playersInConnectionMode = new HashMap<>();
    
    // Scanning state
    private boolean isScanning = false;
    private int scanProgress = 0;
    
    // Property delegate for GUI synchronization
    private final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            switch (index) {
                case 0: return hasValidPartner ? 1 : 0;
                case 1: return isScanning ? 1 : 0;
                case 2: return scanProgress;
                default: return 0;
            }
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0: hasValidPartner = value == 1; break;
                case 1: isScanning = value == 1; break;
                case 2: scanProgress = value; break;
            }
        }

        @Override
        public int size() {
            return 3;
        }
    };
    
    public BlueprintDeskBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BLUEPRINT_DESK_BLOCK_ENTITY, pos, state);
    }
    
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        
        // Save partner position
        if (partnerPos != null) {
            nbt.putInt("partner_x", partnerPos.getX());
            nbt.putInt("partner_y", partnerPos.getY());
            nbt.putInt("partner_z", partnerPos.getZ());
        }
        
        nbt.putBoolean("has_valid_partner", hasValidPartner);
        nbt.putBoolean("is_scanning", isScanning);
        nbt.putInt("scan_progress", scanProgress);
        
        // Save inventory
        Inventories.writeNbt(nbt, this.inventory, registries);
    }
    
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        
        // Load partner position
        if (nbt.contains("partner_x") && nbt.contains("partner_y") && nbt.contains("partner_z")) {
            int x = nbt.getInt("partner_x", 0);
            int y = nbt.getInt("partner_y", 0);
            int z = nbt.getInt("partner_z", 0);
            this.partnerPos = new BlockPos(x, y, z);
        }
        
        this.hasValidPartner = nbt.getBoolean("has_valid_partner", false);
        this.isScanning = nbt.getBoolean("is_scanning", false);
        this.scanProgress = nbt.getInt("scan_progress", 0);
        
        // Load inventory
        Inventories.readNbt(nbt, this.inventory, registries);
    }
    
    /**
     * Server tick method
     */
    public static void tick(World world, BlockPos pos, BlockState state, BlueprintDeskBlockEntity entity) {
        if (world.isClient()) {
            return;
        }
        
        // Update block state based on connection and scanning status
        boolean connected = entity.hasValidPartner;
        boolean scanning = entity.isScanning;
        
        if (state.get(BlueprintDeskBlock.CONNECTED) != connected || 
            state.get(BlueprintDeskBlock.SCANNING) != scanning) {
            world.setBlockState(pos, state
                .with(BlueprintDeskBlock.CONNECTED, connected)
                .with(BlueprintDeskBlock.SCANNING, scanning), 
                Block.NOTIFY_ALL);
        }
        


        
        // Validate partner connection periodically
        if (world.getTime() % 20 == 0) { // Every second
            entity.validatePartnerConnection();
        }
        
        // Handle scanning progress
        if (entity.isScanning) {
            entity.processScanningTick();
        }
    }
    
    /**
     * Finds and connects to a partner blueprint desk
     * Uses an optimized search pattern to avoid lag
     */
    public void findPartnerDesk() {
        if (world == null || world.isClient()) {
            return;
        }
        
        // Use a much smaller search radius to avoid lag
        int searchRadius = 50; // Reduced from 300 to 50 blocks
        
        // Search in expanding rings to find the closest partner first
        for (int radius = 1; radius <= searchRadius; radius++) {
            // Search the surface of a cube at this radius
            for (int axis = 0; axis < 3; axis++) {
                for (int sign = -1; sign <= 1; sign += 2) {
                    for (int other1 = -radius; other1 <= radius; other1++) {
                        for (int other2 = -radius; other2 <= radius; other2++) {
                            int x, y, z;
                            
                            if (axis == 0) {
                                x = sign * radius;
                                y = other1;
                                z = other2;
                            } else if (axis == 1) {
                                x = other1;
                                y = sign * radius;
                                z = other2;
                            } else {
                                x = other1;
                                y = other2;
                                z = sign * radius;
                            }
                            
                            // Skip if outside our search radius
                            if (Math.abs(x) > searchRadius || Math.abs(y) > searchRadius || Math.abs(z) > searchRadius) {
                                continue;
                            }
                            
                            // Skip self
                            if (x == 0 && y == 0 && z == 0) continue;
                            
                            BlockPos searchPos = pos.add(x, y, z);
                            BlockEntity blockEntity = world.getBlockEntity(searchPos);
                            
                            if (blockEntity instanceof BlueprintDeskBlockEntity otherDesk) {
                                // Check if the other desk doesn't have a partner
                                if (!otherDesk.hasValidPartner) {
                                    // Connect to each other
                                    this.setPartner(searchPos);
                                    otherDesk.setPartner(this.pos);
                                    
                                    Circuitmod.LOGGER.info("[BLUEPRINT-DESK] Connected desks at {} and {}", pos, searchPos);
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Sets the partner desk position
     */
    private void setPartner(BlockPos partnerPos) {
        this.partnerPos = partnerPos;
        this.hasValidPartner = true;
        markDirty();
    }
    
    /**
     * Disconnects from partner
     */
    public void disconnectFromPartner() {
        if (partnerPos != null && world != null && !world.isClient()) {
            BlockEntity partnerEntity = world.getBlockEntity(partnerPos);
            if (partnerEntity instanceof BlueprintDeskBlockEntity partner) {
                partner.partnerPos = null;
                partner.hasValidPartner = false;
                partner.markDirty();
            }
        }
        
        this.partnerPos = null;
        this.hasValidPartner = false;
        markDirty();
    }
    
    /**
     * Validates that the partner connection is still valid
     */
    private void validatePartnerConnection() {
        if (partnerPos == null) {
            hasValidPartner = false;
            return;
        }
        
        if (world == null) {
            return;
        }
        
        BlockEntity partnerEntity = world.getBlockEntity(partnerPos);
        if (!(partnerEntity instanceof BlueprintDeskBlockEntity)) {
            // Partner is no longer valid
            disconnectFromPartner();
        }
    }
    
    /**
     * Starts scanning the area between this desk and its partner
     */
    public void startScanning() {
        if (!hasValidPartner || partnerPos == null || world == null || world.isClient()) {
            return;
        }
        
        // Check if we have a blank blueprint item in our inventory
        ItemStack stack = inventory.get(0);
        if (stack.isEmpty() || !stack.isOf(ModItems.BLANK_BLUEPRINT)) {
            // Need a blank blueprint item
            return;
        }
        
        // Generate a unique name for the blueprint
        String blueprintName = generateUniqueBlueprintName();
        
        this.isScanning = true;
        this.scanProgress = 0;
        markDirty();
        
        // Start the scanning process
        BlockPos startPos = pos;
        BlockPos endPos = partnerPos;
        
        Circuitmod.LOGGER.info("[BLUEPRINT-DESK] Starting scan from {} to {} with name: {}", startPos, endPos, blueprintName);
        
        // Create the blueprint and start async scanning
        BlueprintScanner.scanAreaAsync(startPos, endPos, (ServerWorld) world, blueprintName, 
            this::onScanComplete, this::onScanProgress);
    }
    
    /**
     * Processes scanning progress each tick
     */
    private void processScanningTick() {
        // Scanning progress is handled by the async scanner
        // This just updates visual progress
        scanProgress = Math.min(scanProgress + 1, 100);
    }
    
    /**
     * Called when scanning progress updates
     */
    private void onScanProgress(int progress) {
        this.scanProgress = progress;
        markDirty();
    }
    
    /**
     * Called when scanning is complete
     */
    private void onScanComplete(Blueprint blueprint) {
        if (world == null || world.isClient()) {
            return;
        }
        
        this.isScanning = false;
        this.scanProgress = 100;
        
        // Create blueprint item
        ItemStack blueprintStack = BlueprintItem.createWithBlueprint(blueprint, world.getRegistryManager());
        
        // Replace the blank blueprint item with the completed blueprint
        inventory.set(0, blueprintStack);
        
        markDirty();
        
        Circuitmod.LOGGER.info("[BLUEPRINT-DESK] Scan complete! Created blueprint: {}", blueprint.getName());
    }
    

    
    /**
     * Schedules a partner search for the next tick
     */
    public void schedulePartnerSearch() {
        this.needsPartnerSearch = true;
    }
    
    /**
     * Starts connection mode for a player
     */
    public void startConnectionMode(PlayerEntity player) {
        playersInConnectionMode.put(player, this.pos);
    }
    
    /**
     * Attempts to connect to another desk when in connection mode
     */
    public boolean tryConnectToPartner(PlayerEntity player) {
        BlockPos firstDeskPos = playersInConnectionMode.get(player);
        if (firstDeskPos != null && !firstDeskPos.equals(this.pos)) {
            // Get the first desk
            if (world != null) {
                BlockEntity firstDeskEntity = world.getBlockEntity(firstDeskPos);
                if (firstDeskEntity instanceof BlueprintDeskBlockEntity firstDesk) {
                    // Connect the desks
                    this.setPartner(firstDeskPos);
                    firstDesk.setPartner(this.pos);
                    
                    // Clear connection mode
                    playersInConnectionMode.remove(player);
                    
                    // Notify player
                    player.sendMessage(Text.literal("Blueprint desks connected successfully!"), false);
                    
                    Circuitmod.LOGGER.info("[BLUEPRINT-DESK] Player {} connected desks at {} and {}", 
                        player.getName().getString(), firstDeskPos, this.pos);
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Checks if a player is currently in connection mode
     */
    public static boolean isPlayerInConnectionMode(PlayerEntity player) {
        return playersInConnectionMode.containsKey(player);
    }
    
    /**
     * Cancels connection mode for a player
     */
    public static void cancelConnectionMode(PlayerEntity player) {
        playersInConnectionMode.remove(player);
        player.sendMessage(Text.literal("Connection mode cancelled"), false);
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
        
        // Auto-start scanning when a blank blueprint is inserted
        if (slot == 0 && !stack.isEmpty() && stack.isOf(ModItems.BLANK_BLUEPRINT)) {
            if (hasValidPartner && !isScanning && world != null && !world.isClient()) {
                // Start scanning immediately
                startScanning();
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
        return Text.translatable("block.circuitmod.blueprint_desk");
    }
    
    @Override
    public ModScreenHandlers.BlueprintDeskData getScreenOpeningData(ServerPlayerEntity player) {
        return new ModScreenHandlers.BlueprintDeskData(this.pos);
    }
    
    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new BlueprintDeskScreenHandler(syncId, playerInventory, this, this.propertyDelegate);
    }
    
    // Getters for GUI
    public boolean hasValidPartner() {
        return hasValidPartner;
    }
    
    public boolean isScanning() {
        return isScanning;
    }
    
    public int getScanProgress() {
        return scanProgress;
    }
    

    
    /**
     * Generates a unique blueprint name with random numbers
     */
    private String generateUniqueBlueprintName() {
        // Generate a random 6-digit number
        int randomNumber = (int)(Math.random() * 900000) + 100000; // 100000 to 999999
        return String.valueOf(randomNumber);
    }
    
    public PropertyDelegate getPropertyDelegate() {
        return propertyDelegate;
    }
} 