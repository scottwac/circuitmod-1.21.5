package starduster.circuitmod.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
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
import starduster.circuitmod.screen.ModScreenHandlers;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.item.ModItems;
import starduster.circuitmod.power.EnergyNetwork;
import starduster.circuitmod.power.IEnergyProducer;
import starduster.circuitmod.power.IPowerConnectable;
import starduster.circuitmod.screen.ReactorScreenHandler;
import starduster.circuitmod.item.FuelRodItem;

public class ReactorBlockBlockEntity extends BlockEntity implements SidedInventory, NamedScreenHandlerFactory, ExtendedScreenHandlerFactory<ModScreenHandlers.ReactorData>, IEnergyProducer {
    // Energy production properties
    private static final int BASE_ENERGY_PER_ROD = 60; // Base energy per fuel rod per tick
    private static final int MAX_RODS = 9; // Maximum number of fuel rods that can be inserted
    private static final int UPDATE_INTERVAL = 100; // 5 seconds (at 20 ticks per second)
    
    // Network and state
    private EnergyNetwork network;
    private int tickCounter = 0;
    private boolean needsNetworkRefresh = false;
    private boolean isActive = false; // Whether the reactor is currently producing energy
    
    // Inventory for fuel rods - 9 slots for 9 rods
    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(MAX_RODS, ItemStack.EMPTY);
    
    // Property delegate indices for GUI synchronization
    private static final int ENERGY_PRODUCTION_INDEX = 0;
    private static final int ROD_COUNT_INDEX = 1;
    private static final int IS_ACTIVE_INDEX = 2;
    private static final int PROPERTY_COUNT = 3;
    
    // Stored PropertyDelegate values for synchronization
    private int syncedEnergyProduction = 0;
    private int syncedRodCount = 0;
    private int syncedActiveStatus = 0;
    
    // Property delegate for GUI synchronization
    private final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            switch (index) {
                case ENERGY_PRODUCTION_INDEX:
                    return syncedEnergyProduction;
                case ROD_COUNT_INDEX:
                    return syncedRodCount;
                case IS_ACTIVE_INDEX:
                    return syncedActiveStatus;
                default:
                    return 0;
            }
        }

        @Override
        public void set(int index, int value) {
            // This method is called by Minecraft's built-in synchronization
            // Update the stored values when we receive synchronization data
            switch (index) {
                case ENERGY_PRODUCTION_INDEX:
                    syncedEnergyProduction = value;
                    break;
                case ROD_COUNT_INDEX:
                    syncedRodCount = value;
                    break;
                case IS_ACTIVE_INDEX:
                    syncedActiveStatus = value;
                    break;
            }
        }

        @Override
        public int size() {
            return PROPERTY_COUNT;
        }
    };
    
    public ReactorBlockBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REACTOR_BLOCK_BLOCK_ENTITY, pos, state);
    }
    
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        
        // Save reactor state
        nbt.putInt("tick_counter", tickCounter);
        nbt.putBoolean("is_active", isActive);
        
        // Save PropertyDelegate values
        nbt.putInt("synced_energy_production", syncedEnergyProduction);
        nbt.putInt("synced_rod_count", syncedRodCount);
        nbt.putInt("synced_active_status", syncedActiveStatus);
        
        // Save network data if we have a network
        if (network != null) {
            NbtCompound networkNbt = new NbtCompound();
            network.writeToNbt(networkNbt);
            nbt.put("energy_network", networkNbt);
        }
        
        // Save inventory
        Inventories.writeNbt(nbt, this.inventory, registries);
    }
    
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        
        // Load reactor state
        this.tickCounter = nbt.getInt("tick_counter").orElse(0);
        this.isActive = nbt.getBoolean("is_active").orElse(false);
        
        // Load PropertyDelegate values
        this.syncedEnergyProduction = nbt.getInt("synced_energy_production").orElse(0);
        this.syncedRodCount = nbt.getInt("synced_rod_count").orElse(0);
        this.syncedActiveStatus = nbt.getInt("synced_active_status").orElse(0);
        
        // Load network data
        if (nbt.contains("energy_network")) {
            this.network = new EnergyNetwork();
            NbtCompound networkNbt = nbt.getCompound("energy_network").orElse(new NbtCompound());
            network.readFromNbt(networkNbt);
        }
        
        // Load inventory
        Inventories.readNbt(nbt, this.inventory, registries);
        needsNetworkRefresh = true;
    }

    // Tick method called by the ticker in ReactorBlock
    public static void tick(World world, BlockPos pos, BlockState state, ReactorBlockBlockEntity blockEntity) {
        if (blockEntity.needsNetworkRefresh) {
            blockEntity.findAndJoinNetwork();
            blockEntity.needsNetworkRefresh = false;
        }
        
        if (world.isClient()) {
            return;
        }

        // Update tick counter
        blockEntity.tickCounter++;
        
        // Update packet cooldown
        // Removed packet cooldown as it's no longer used
        
        // Check if reactor should be active (has fuel)
        boolean shouldBeActive = blockEntity.getRodCount() > 0;
        
        // If reactor is active, damage fuel rods
        if (shouldBeActive) {
            blockEntity.damageFuelRods();
        }
        
        // Update active state and block state if needed
        if (blockEntity.isActive != shouldBeActive) {
            blockEntity.isActive = shouldBeActive;
            blockEntity.markDirty();
            
            // Update PropertyDelegate values
            blockEntity.updatePropertyDelegateValues();
            
            // Update the block state to reflect the number of rods
            int rodCount = blockEntity.getRodCount();
            BlockState newState = state.with(starduster.circuitmod.block.machines.ReactorBlock.RODS, rodCount);
            if (!state.equals(newState)) {
                world.setBlockState(pos, newState, 3);
            }
        }
        
        // Debug logging (only log occasionally to avoid spam)
        if (world.getTime() % 20 == 0) { // Only log every second
            String networkInfo = blockEntity.network != null ? blockEntity.network.getNetworkId() : "NO NETWORK";
            Circuitmod.LOGGER.info("[REACTOR-TICK] Rods: " + blockEntity.getRodCount() + 
                ", Active: " + blockEntity.isActive + 
                ", Energy: " + blockEntity.getCurrentEnergyProduction() + 
                ", Network: " + networkInfo);
        }
    }
    
    // IEnergyProducer implementation
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
            Circuitmod.LOGGER.info("[REACTOR-NETWORK] Reactor at " + pos + " changing networks: " + oldNetworkId + " -> " + newNetworkId);
        } else if (network != null && this.network == null) {
            Circuitmod.LOGGER.info("[REACTOR-NETWORK] Reactor at " + pos + " connecting to network: " + network.getNetworkId());
        } else if (this.network != null && network == null) {
            String oldNetworkId = this.network.getNetworkId();
            Circuitmod.LOGGER.info("[REACTOR-NETWORK] Reactor at " + pos + " disconnecting from network: " + oldNetworkId);
        }
        
        this.network = network;
    }
    
    @Override
    public int produceEnergy(int maxRequested) {
        if (world == null || world.isClient() || !isActive) {
            return 0;
        }
        
        int currentProduction = getCurrentEnergyProduction();
        int energyToProduce = Math.min(currentProduction, maxRequested);
        
        // Debug logs to diagnose the issue (only log occasionally to avoid spam)
        if (world.getTime() % 20 == 0) { // Only log every second
            Circuitmod.LOGGER.info("[REACTOR-ENERGY] Max requested: " + maxRequested + 
                ", Current production: " + currentProduction + 
                ", Producing: " + energyToProduce + 
                ", Rods: " + getRodCount() + 
                ", Active: " + isActive);
        }
        
        return energyToProduce;
    }
    
    @Override
    public int getMaxOutput() {
        return getCurrentEnergyProduction();
    }
    
    @Override
    public Direction[] getOutputSides() {
        return Direction.values(); // Can output to all sides
    }
    
    @Override
    public void setWorld(World world) {
        super.setWorld(world);
        
        // Don't call updateBlockState here as it can cause deadlocks during world loading
        // The tick method will handle updating the block state when needed
    }
    
    // Helper methods
    public int getRodCount() {
        int count = 0;
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty() && stack.getItem() == ModItems.FUEL_ROD) {
                count += stack.getCount();
            }
        }
        return Math.min(count, MAX_RODS);
    }
    
    public boolean isInventoryFull() {
        for (ItemStack stack : inventory) {
            if (stack.isEmpty()) {
                return false; // Found an empty slot
            }
        }
        return true; // All slots are occupied
    }
    
    public int getCurrentEnergyProduction() {
        int rodCount = getRodCount();
        if (rodCount == 0) {
            return 0;
        }
        
        // Calculate energy with scaling bonuses
        // Base: rodCount * BASE_ENERGY_PER_ROD
        // Bonus: (rodCount - 1) * 0.3 * BASE_ENERGY_PER_ROD (30% bonus per additional rod)
        int baseEnergy = rodCount * BASE_ENERGY_PER_ROD;
        int bonusEnergy = (rodCount - 1) * (BASE_ENERGY_PER_ROD * 3 / 10); // 30% bonus per additional rod
        
        return baseEnergy + bonusEnergy;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public PropertyDelegate getPropertyDelegate() {
        return propertyDelegate;
    }
    
    /**
     * Damages fuel rods in the reactor inventory when the reactor is active.
     * Fuel rods take 1 second of durability damage every 20 ticks when the reactor is running.
     * When a fuel rod reaches 0 durability, it is consumed.
     */
    private void damageFuelRods() {
        boolean inventoryChanged = false;
        
        // Only damage fuel rods every 20 ticks (once per second)
        if (tickCounter % 20 != 0) {
            return;
        }
        
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.get(i);
            if (!stack.isEmpty() && stack.getItem() == ModItems.FUEL_ROD) {
                // Damage the fuel rod by 1 second every 20 ticks
                boolean wasConsumed = FuelRodItem.reduceDurability(stack, 1);
                
                if (wasConsumed) {
                    // Fuel rod was consumed, clear the slot
                    inventory.set(i, ItemStack.EMPTY);
                    inventoryChanged = true;
                    
                    if (world != null && !world.isClient()) {
                        Circuitmod.LOGGER.info("[REACTOR-FUEL] Fuel rod consumed at slot " + i + " in reactor at " + pos);
                    }
                } else {
                    // Fuel rod was damaged but not consumed, update the slot
                    inventory.set(i, stack);
                    inventoryChanged = true;
                }
            }
        }
        
        // If inventory changed, mark dirty and update state
        if (inventoryChanged) {
            markDirty();
            updateBlockState();
            updatePropertyDelegateValues();
        }
    }
    
    // Update stored PropertyDelegate values
    private void updatePropertyDelegateValues() {
        syncedRodCount = getRodCount();
        syncedEnergyProduction = getCurrentEnergyProduction();
        syncedActiveStatus = isActive ? 1 : 0;
        
        // Debug logging
        if (world != null && !world.isClient() && world.getTime() % 20 == 0) {
            Circuitmod.LOGGER.info("[PROPERTY-DELEGATE] Updated values - Energy: {}, Rods: {}, Active: {}", 
                syncedEnergyProduction, syncedRodCount, syncedActiveStatus);
        }
    }
    
    // Network connection logic
    public void findAndJoinNetwork() {
        if (world == null || world.isClient()) return;
        
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
                    IPowerConnectable connectable = (IPowerConnectable) be;
                    if (connectable.canConnectPower(dir.getOpposite()) && this.canConnectPower(dir)) {
                        newNetwork.addBlock(neighborPos, connectable);
                    }
                }
            }
        }
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
        ItemStack result = Inventories.splitStack(inventory, slot, amount);
        if (!result.isEmpty()) {
            markDirty();
            updateBlockState();
            
            // Update PropertyDelegate values
            updatePropertyDelegateValues();
            
            // Force a block update to trigger PropertyDelegate synchronization
            if (world != null && !world.isClient()) {
                world.updateListeners(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
                markDirty();
            }
        }
        return result;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack result = Inventories.removeStack(inventory, slot);
        if (!result.isEmpty()) {
            markDirty();
            updateBlockState();
            
            // Update PropertyDelegate values
            updatePropertyDelegateValues();
            
            // Force a block update to trigger PropertyDelegate synchronization
            if (world != null && !world.isClient()) {
                world.updateListeners(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
                markDirty();
            }
        }
        return result;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        // Only allow fuel rods to be set
        if (stack.isEmpty() || stack.getItem() == ModItems.FUEL_ROD) {
            inventory.set(slot, stack);
            if (stack.getCount() > getMaxCountPerStack()) {
                stack.setCount(getMaxCountPerStack());
            }
            markDirty();
            updateBlockState();
            
            // Update PropertyDelegate values
            updatePropertyDelegateValues();
            
            // Force a block update to trigger PropertyDelegate synchronization
            if (world != null && !world.isClient()) {
                world.updateListeners(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
                // Also mark the block entity as dirty to ensure NBT sync
                markDirty();
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
        markDirty();
        updateBlockState();
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
        // Only allow fuel rods to be inserted
        return stack.getItem() == ModItems.FUEL_ROD;
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {

        return true; // Allow extraction from any side
    }
    
    // Helper method to update block state based on rod count
    private void updateBlockState() {
        if (world != null && !world.isClient() && pos != null) {
            try {
                int rodCount = getRodCount();
                BlockState currentState = world.getBlockState(pos);
                BlockState newState = currentState.with(starduster.circuitmod.block.machines.ReactorBlock.RODS, rodCount);
                if (!currentState.equals(newState)) {
                    world.setBlockState(pos, newState, 3);
                    Circuitmod.LOGGER.info("[REACTOR-STATE] Updated rod count to " + rodCount + " at " + pos);
                }
            } catch (Exception e) {
                Circuitmod.LOGGER.warn("[REACTOR-STATE] Failed to update block state at " + pos + ": " + e.getMessage());
            }
        }
    }
    
    // NamedScreenHandlerFactory implementation
    @Override
    public Text getDisplayName() {
        return Text.translatable("block.circuitmod.reactor_block");
    }
    
    @Override
    public ModScreenHandlers.ReactorData getScreenOpeningData(ServerPlayerEntity player) {
        return new ModScreenHandlers.ReactorData(this.pos);
    }

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, net.minecraft.entity.player.PlayerInventory playerInventory, PlayerEntity player) {
        return new ReactorScreenHandler(syncId, playerInventory, this, this.propertyDelegate, this);
    }
} 