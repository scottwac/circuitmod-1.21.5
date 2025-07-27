package starduster.circuitmod.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.block.machines.MassFabricatorBlock;
import starduster.circuitmod.power.EnergyNetwork;
import starduster.circuitmod.power.IEnergyConsumer;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.entity.player.PlayerInventory;
import starduster.circuitmod.screen.MassFabricatorScreenHandler;

public class MassFabricatorBlockEntity extends BlockEntity implements SidedInventory, IEnergyConsumer, NamedScreenHandlerFactory {
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int SLOT_COUNT = 2;
    public static final int ENERGY_REQUIRED = 4320000; // 1 hour with 1 reactor rod (60 energy/tick × 72,000 ticks)
    public static final int MAX_PROGRESS = 100;

    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(SLOT_COUNT, ItemStack.EMPTY);
    private int progress = 0;
    private int energyStored = 0;
    private int selectedResource = 0; // 0: Diamond, 1: Emerald, 2: Netherite, 3: Gold
    private boolean shouldShowFireworks = false;
    private EnergyNetwork network;

    public MassFabricatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MASS_FABRICATOR_BLOCK_ENTITY, pos, state);
    }

    // Inventory methods
    @Override
    public int size() { return SLOT_COUNT; }
    @Override
    public boolean isEmpty() { return inventory.stream().allMatch(ItemStack::isEmpty); }
    @Override
    public ItemStack getStack(int slot) { return inventory.get(slot); }
    @Override
    public ItemStack removeStack(int slot, int amount) { return Inventories.splitStack(inventory, slot, amount); }
    @Override
    public ItemStack removeStack(int slot) { return Inventories.removeStack(inventory, slot); }
    @Override
    public void setStack(int slot, ItemStack stack) { inventory.set(slot, stack); }
    @Override
    public boolean canPlayerUse(PlayerEntity player) { return true; }
    @Override
    public void clear() { inventory.clear(); }
    @Override
    public int[] getAvailableSlots(Direction side) { return new int[]{0, 1}; }
    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) { return slot == INPUT_SLOT; }
    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) { return slot == OUTPUT_SLOT; }

    // Energy system
    @Override
    public boolean canConnectPower(Direction side) { return true; }
    @Override
    public EnergyNetwork getNetwork() { return network; }
    @Override
    public void setNetwork(EnergyNetwork network) { this.network = network; }
    @Override
    public int consumeEnergy(int energyOffered) {
        int toConsume = Math.min(energyOffered, ENERGY_REQUIRED - energyStored);
        energyStored += toConsume;
        return toConsume;
    }
    @Override
    public int getEnergyDemand() { return Math.max(0, ENERGY_REQUIRED - energyStored); }
    @Override
    public Direction[] getInputSides() { return Direction.values(); }

    // Progress and resource selection
    public int getProgress() { return progress; }
    public int getMaxProgress() { return MAX_PROGRESS; }
    public int getSelectedResource() { return selectedResource; }
    public void setSelectedResource(int resource) { this.selectedResource = resource; }

    // NBT  
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        nbt.putInt("progress", progress);
        nbt.putInt("energyStored", energyStored);
        nbt.putInt("selectedResource", selectedResource);
        nbt.putBoolean("shouldShowFireworks", shouldShowFireworks);
        if (network != null) {
            NbtCompound networkNbt = new NbtCompound();
            network.writeToNbt(networkNbt);
            nbt.put("energy_network", networkNbt);
        }
        Inventories.writeNbt(nbt, inventory, registryLookup);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        if (nbt.contains("progress")) progress = nbt.getInt("progress").orElse(0);
        if (nbt.contains("energyStored")) energyStored = nbt.getInt("energyStored").orElse(0);
        if (nbt.contains("selectedResource")) selectedResource = nbt.getInt("selectedResource").orElse(0);
        if (nbt.contains("shouldShowFireworks")) shouldShowFireworks = nbt.getBoolean("shouldShowFireworks").orElse(false);
        if (nbt.contains("energy_network")) {
            this.network = new EnergyNetwork();
            NbtCompound networkNbt = nbt.getCompound("energy_network").orElse(new NbtCompound());
            network.readFromNbt(networkNbt);
        }
        Inventories.readNbt(nbt, inventory, registryLookup);
    }

    // Tick logic (stub)
    public static void tick(World world, BlockPos pos, BlockState state, MassFabricatorBlockEntity entity) {
        if (world.isClient()) return;

        // Check for Nether Star in input
        ItemStack input = entity.inventory.get(INPUT_SLOT);
        boolean hasNetherStar = !input.isEmpty() && input.getItem().getTranslationKey().equals("item.minecraft.nether_star");
        boolean canOutput = true;
        if (!entity.inventory.get(OUTPUT_SLOT).isEmpty()) {
            // Always replace output if full
            canOutput = true;
        }

        if (hasNetherStar && entity.energyStored >= ENERGY_REQUIRED && canOutput) {
            entity.progress++;
            if (entity.progress >= MAX_PROGRESS) {
                // Create the selected item
                ItemStack result = getResultForResource(entity.selectedResource);
                entity.inventory.set(OUTPUT_SLOT, result.copy());
                // Reset
                entity.progress = 0;
                entity.energyStored = 0;
                // Trigger firework effect
                entity.shouldShowFireworks = true;
            }
            entity.markDirty();
        } else {
            entity.progress = 0;
        }
    }

    private static ItemStack getResultForResource(int resource) {
        // 0: Diamond, 1: Emerald, 2: Netherite Ingot, 3: Gold Ingot
        switch (resource) {
            case 1: return new ItemStack(net.minecraft.item.Items.EMERALD);
            case 2: return new ItemStack(net.minecraft.item.Items.NETHERITE_INGOT);
            case 3: return new ItemStack(net.minecraft.item.Items.GOLD_INGOT);
            default: return new ItemStack(net.minecraft.item.Items.DIAMOND);
        }
    }

    // PropertyDelegate for GUI
    public PropertyDelegate getPropertyDelegate() {
        return new PropertyDelegate() {
            @Override public int get(int index) {
                switch (index) {
                    case 0: return progress;
                    case 1: return MAX_PROGRESS;
                    case 2: return energyStored;
                    case 3: return ENERGY_REQUIRED;
                    case 4: return selectedResource;
                    default: return 0;
                }
            }
            @Override public void set(int index, int value) {
                switch (index) {
                    case 0: progress = value; break;
                    case 2: energyStored = value; break;
                    case 4: selectedResource = value; break;
                }
            }
            @Override public int size() { return 5; }
        };
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.circuitmod.mass_fabricator");
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new MassFabricatorScreenHandler(syncId, playerInventory, this, this.getPropertyDelegate());
    }
    
    public boolean shouldShowFireworks() {
        return shouldShowFireworks;
    }
    
    public void setFireworksShown() {
        this.shouldShowFireworks = false;
        markDirty();
    }
} 