package starduster.circuitmod.block.entity;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.screen.ModScreenHandlers;
import starduster.circuitmod.screen.ExpeditionControlScreenHandler;
import starduster.circuitmod.util.ExpeditionCommandHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ExpeditionControlBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<ModScreenHandlers.ExpeditionControlData> {
    private static final int MAX_HISTORY = 100;
    
    // Command history (output lines)
    private final List<String> outputLines = new ArrayList<>();
    
    // Time when the block entity was created (for uptime tracking)
    private long creationTime = -1;
    
    // Fuel storage (abstract number)
    private int storedFuel = 0;
    private static final int MAX_FUEL = 10000;
    
    // Fuel values for different items
    private static final int COAL_FUEL_VALUE = 50;
    private static final int CHARCOAL_FUEL_VALUE = 50;
    private static final int BLAZE_ROD_FUEL_VALUE = 1000;
    
    // Monitor mode - persists so screen reopens to monitor view
    private boolean monitorMode = false;

    public ExpeditionControlBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EXPEDITION_CONTROL_BLOCK_ENTITY, pos, state);
        this.outputLines.add("Expedition Control Terminal v1.0");
        this.outputLines.add("Type 'help' for available commands");
        this.outputLines.add("");
    }

    /**
     * Execute a command and add the result to the output
     */
    public void executeCommand(String command, UUID playerId) {
        if (world == null || world.isClient) {
            return;
        }
        
        // Initialize creation time on first command if not set
        if (creationTime < 0) {
            creationTime = world.getTime();
        }
        
        // Add the command to output with prompt
        if (!command.trim().isEmpty()) {
            outputLines.add("> " + command);
        }
        
        // Execute the command
        ExpeditionCommandHandler.CommandResult result = ExpeditionCommandHandler.executeCommand(
            command,
            (ServerWorld) world,
            pos,
            creationTime,
            this,
            playerId
        );
        
        // Handle clear screen command
        if (result.clearScreen()) {
            outputLines.clear();
        } else if (result.output() != null && !result.output().isEmpty()) {
            // Add each line of output
            String[] lines = result.output().split("\n");
            for (String line : lines) {
                outputLines.add(line);
            }
        }
        
        // Add blank line after command output
        outputLines.add("");
        
        // Limit history size
        while (outputLines.size() > MAX_HISTORY) {
            outputLines.remove(0);
        }
        
        markDirty();
        syncToClient();
    }

    /**
     * Get the output lines for display
     */
    public List<String> getOutputLines() {
        return new ArrayList<>(outputLines);
    }
    
    /**
     * Append a line from asynchronous events
     */
    public void appendOutputLine(String line) {
        if (line == null || line.isEmpty()) {
            return;
        }
        outputLines.add(line);
        while (outputLines.size() > MAX_HISTORY) {
            outputLines.remove(0);
        }
        syncToClient();
    }

    /**
     * Clear the output
     */
    public void clearOutput() {
        outputLines.clear();
        outputLines.add("Expedition Control Terminal v1.0");
        outputLines.add("Type 'help' for available commands");
        outputLines.add("");
        markDirty();
        syncToClient();
    }

    /**
     * Get the stored fuel amount
     */
    public int getStoredFuel() {
        return storedFuel;
    }

    /**
     * Get maximum fuel capacity
     */
    public int getMaxFuel() {
        return MAX_FUEL;
    }

    /**
     * Consume fuel for an expedition
     * @return true if enough fuel was available and consumed
     */
    public boolean consumeFuel(int amount) {
        if (storedFuel >= amount) {
            storedFuel -= amount;
            markDirty();
            syncToClient();
            return true;
        }
        return false;
    }

    /**
     * Add fuel from an item stack
     * @return the number of items consumed
     */
    public int addFuelFromItem(ItemStack stack, int maxToConsume) {
        if (stack.isEmpty()) {
            return 0;
        }
        
        int fuelValue = getFuelValue(stack);
        if (fuelValue <= 0) {
            return 0;
        }
        
        int spaceAvailable = MAX_FUEL - storedFuel;
        int maxItemsNeeded = (spaceAvailable + fuelValue - 1) / fuelValue; // Ceiling division
        int itemsToConsume = Math.min(Math.min(stack.getCount(), maxToConsume), maxItemsNeeded);
        
        if (itemsToConsume > 0) {
            storedFuel = Math.min(MAX_FUEL, storedFuel + (itemsToConsume * fuelValue));
            markDirty();
            syncToClient();
        }
        
        return itemsToConsume;
    }

    /**
     * Get fuel value for an item
     */
    public static int getFuelValue(ItemStack stack) {
        if (stack.isOf(Items.COAL)) {
            return COAL_FUEL_VALUE;
        } else if (stack.isOf(Items.CHARCOAL)) {
            return CHARCOAL_FUEL_VALUE;
        } else if (stack.isOf(Items.BLAZE_ROD)) {
            return BLAZE_ROD_FUEL_VALUE;
        }
        return 0;
    }

    /**
     * Check if an item is valid fuel
     */
    public static boolean isValidFuel(ItemStack stack) {
        return getFuelValue(stack) > 0;
    }

    /**
     * Find an adjacent chest (checks all 6 directions)
     * @return the chest inventory, or null if none found
     */
    @Nullable
    public Inventory findAdjacentChest() {
        if (world == null) {
            return null;
        }
        
        for (Direction direction : Direction.values()) {
            BlockPos adjacentPos = pos.offset(direction);
            BlockEntity blockEntity = world.getBlockEntity(adjacentPos);
            if (blockEntity instanceof ChestBlockEntity chest) {
                return chest;
            }
        }
        return null;
    }

    /**
     * Refuel from an adjacent chest
     * @param maxFuelToAdd maximum amount of fuel to add (0 = unlimited up to MAX_FUEL)
     * @return the amount of fuel added
     */
    public int refuelFromAdjacentChest(int maxFuelToAdd) {
        Inventory chest = findAdjacentChest();
        if (chest == null) {
            return -1; // No chest found
        }
        
        int fuelAdded = 0;
        int targetFuel = maxFuelToAdd <= 0 ? MAX_FUEL : Math.min(storedFuel + maxFuelToAdd, MAX_FUEL);
        
        for (int i = 0; i < chest.size() && storedFuel < targetFuel; i++) {
            ItemStack stack = chest.getStack(i);
            if (isValidFuel(stack)) {
                int fuelValue = getFuelValue(stack);
                
                // Calculate how many items we need
                int spaceAvailable = targetFuel - storedFuel;
                int itemsNeeded = (spaceAvailable + fuelValue - 1) / fuelValue; // Ceiling division
                int itemsToConsume = Math.min(stack.getCount(), itemsNeeded);
                
                if (itemsToConsume > 0) {
                    int fuelFromItems = itemsToConsume * fuelValue;
                    storedFuel = Math.min(MAX_FUEL, storedFuel + fuelFromItems);
                    fuelAdded += fuelFromItems;
                    stack.decrement(itemsToConsume);
                    chest.markDirty();
                }
            }
        }
        
        if (fuelAdded > 0) {
            markDirty();
            syncToClient();
        }
        
        return fuelAdded;
    }

    /**
     * Check if monitor mode is active
     */
    public boolean isMonitorMode() {
        return monitorMode;
    }

    /**
     * Set monitor mode
     */
    public void setMonitorMode(boolean monitorMode) {
        this.monitorMode = monitorMode;
        markDirty();
        syncToClient();
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        
        // Save output lines
        NbtList linesList = new NbtList();
        for (String line : outputLines) {
            linesList.add(NbtString.of(line));
        }
        nbt.put("OutputLines", linesList);
        nbt.putLong("CreationTime", creationTime);
        nbt.putInt("StoredFuel", storedFuel);
        nbt.putBoolean("MonitorMode", monitorMode);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        
        // Load output lines
        outputLines.clear();
        NbtList linesList = nbt.getListOrEmpty("OutputLines");
        for (int i = 0; i < linesList.size(); i++) {
            linesList.getString(i).ifPresent(outputLines::add);
        }
        
        // If no lines were loaded, add default
        if (outputLines.isEmpty()) {
            outputLines.add("Expedition Control Terminal v1.0");
            outputLines.add("Type 'help' for available commands");
            outputLines.add("");
        }
        
        creationTime = nbt.getLong("CreationTime").orElse(-1L);
        storedFuel = nbt.getInt("StoredFuel").orElse(0);
        monitorMode = nbt.getBoolean("MonitorMode").orElse(false);
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        NbtCompound nbt = new NbtCompound();
        writeNbt(nbt, registries);
        return nbt;
    }

    private void syncToClient() {
        if (world == null) {
            return;
        }
        markDirty();
        if (world instanceof ServerWorld serverWorld) {
            world.updateListeners(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
            serverWorld.getChunkManager().markForUpdate(pos);
        }
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.circuitmod.expedition_control_block");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new ExpeditionControlScreenHandler(syncId, playerInventory, this.pos, this.storedFuel, this.monitorMode);
    }

    @Override
    public ModScreenHandlers.ExpeditionControlData getScreenOpeningData(ServerPlayerEntity player) {
        return new ModScreenHandlers.ExpeditionControlData(this.pos, new ArrayList<>(this.outputLines), this.storedFuel, this.monitorMode);
    }
}

