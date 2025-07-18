package starduster.circuitmod.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import starduster.circuitmod.block.entity.BatteryBlockEntity;
import starduster.circuitmod.screen.ModScreenHandlers.BatteryData;
import starduster.circuitmod.Circuitmod;

public class BatteryScreenHandler extends ScreenHandler {
    private final PropertyDelegate propertyDelegate;
    private final BatteryBlockEntity blockEntity; // Reference to block entity for getting position
    
    // Property delegate indices
    private static final int STORED_ENERGY_INDEX = 0;
    private static final int MAX_CAPACITY_INDEX = 1;
    private static final int MAX_CHARGE_RATE_INDEX = 2;
    private static final int MAX_DISCHARGE_RATE_INDEX = 3;
    private static final int CAN_CHARGE_INDEX = 4;
    private static final int CAN_DISCHARGE_INDEX = 5;
    private static final int NETWORK_SIZE_INDEX = 6;
    private static final int NETWORK_STORED_ENERGY_INDEX = 7;
    private static final int NETWORK_MAX_STORAGE_INDEX = 8;
    private static final int NETWORK_LAST_PRODUCED_INDEX = 9;
    private static final int NETWORK_LAST_CONSUMED_INDEX = 10;
    private static final int NETWORK_LAST_STORED_INDEX = 11;
    private static final int NETWORK_LAST_DRAWN_INDEX = 12;
    
    // Client constructor
    public BatteryScreenHandler(int syncId, PlayerInventory playerInventory, BatteryData data) {
        super(ModScreenHandlers.BATTERY_SCREEN_HANDLER, syncId);
        
        // Get the block position from the data
        BlockPos pos = data.pos();
        // Look up the block entity in the client world
        BatteryBlockEntity blockEntity = 
            (BatteryBlockEntity) playerInventory.player.getWorld().getBlockEntity(pos);
        
        // Initialize fields
        this.propertyDelegate = blockEntity != null ? blockEntity.getPropertyDelegate() : new ArrayPropertyDelegate(13);
        this.blockEntity = blockEntity;
        
        // Add property delegate for synchronization
        this.addProperties(propertyDelegate);
        
        Circuitmod.LOGGER.info("[BATTERY-HANDLER] Client constructor called - blockEntity at pos: {}", pos);
    }
    
    // Server constructor
    public BatteryScreenHandler(int syncId, PlayerInventory playerInventory, PropertyDelegate propertyDelegate, BatteryBlockEntity blockEntity) {
        super(ModScreenHandlers.BATTERY_SCREEN_HANDLER, syncId);
        this.propertyDelegate = propertyDelegate;
        this.blockEntity = blockEntity;
        
        if (blockEntity != null) {
            Circuitmod.LOGGER.info("[BATTERY-HANDLER] Server constructor called - blockEntity at pos: {}", blockEntity.getPos());
        } else {
            Circuitmod.LOGGER.warn("[BATTERY-HANDLER] Server constructor called - blockEntity is null!");
        }
        
        // Add property delegate for synchronization
        this.addProperties(propertyDelegate);
    }
    
    @Override
    public boolean canUse(PlayerEntity player) {
        return true; // Batteries don't have inventory, so always allow use
    }
    
    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        // Batteries don't have inventory slots, so return empty stack
        return ItemStack.EMPTY;
    }
    
    // Get stored energy for display
    public int getStoredEnergy() {
        return this.propertyDelegate.get(STORED_ENERGY_INDEX);
    }
    
    // Get max capacity for display
    public int getMaxCapacity() {
        return this.propertyDelegate.get(MAX_CAPACITY_INDEX);
    }
    
    // Get max charge rate for display
    public int getMaxChargeRate() {
        return this.propertyDelegate.get(MAX_CHARGE_RATE_INDEX);
    }
    
    // Get max discharge rate for display
    public int getMaxDischargeRate() {
        return this.propertyDelegate.get(MAX_DISCHARGE_RATE_INDEX);
    }
    
    // Get can charge state for display
    public boolean canCharge() {
        return this.propertyDelegate.get(CAN_CHARGE_INDEX) == 1;
    }
    
    // Get can discharge state for display
    public boolean canDischarge() {
        return this.propertyDelegate.get(CAN_DISCHARGE_INDEX) == 1;
    }
    
    // Get network size for display
    public int getNetworkSize() {
        return this.propertyDelegate.get(NETWORK_SIZE_INDEX);
    }
    
    // Get network stored energy for display
    public int getNetworkStoredEnergy() {
        return this.propertyDelegate.get(NETWORK_STORED_ENERGY_INDEX);
    }
    
    // Get network max storage for display
    public int getNetworkMaxStorage() {
        return this.propertyDelegate.get(NETWORK_MAX_STORAGE_INDEX);
    }
    
    // Get network last produced energy for display
    public int getNetworkLastProduced() {
        return this.propertyDelegate.get(NETWORK_LAST_PRODUCED_INDEX);
    }
    
    // Get network last consumed energy for display
    public int getNetworkLastConsumed() {
        return this.propertyDelegate.get(NETWORK_LAST_CONSUMED_INDEX);
    }
    
    // Get network last stored energy for display
    public int getNetworkLastStored() {
        return this.propertyDelegate.get(NETWORK_LAST_STORED_INDEX);
    }
    
    // Get network last drawn energy for display
    public int getNetworkLastDrawn() {
        return this.propertyDelegate.get(NETWORK_LAST_DRAWN_INDEX);
    }
    
    // Get block position (for client-side networking)
    public BlockPos getBlockPos() {
        return blockEntity != null ? blockEntity.getPos() : BlockPos.ORIGIN;
    }
    
    // Get the block entity (for direct access when needed)
    public BatteryBlockEntity getBlockEntity() {
        return blockEntity;
    }
} 