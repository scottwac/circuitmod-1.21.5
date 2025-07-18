package starduster.circuitmod.block.entity;

import net.minecraft.block.AirBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.recipe.*;
import starduster.circuitmod.screen.BloomeryScreenHandler;
import starduster.circuitmod.screen.CrusherScreenHandler;
import starduster.circuitmod.util.ImplementedInventory;
import starduster.circuitmod.power.EnergyNetwork;
import starduster.circuitmod.power.IEnergyConsumer;
import starduster.circuitmod.power.IPowerConnectable;
import starduster.circuitmod.block.machines.CrusherBlock;

import java.util.Optional;

public class CrusherBlockEntity extends BlockEntity implements NamedScreenHandlerFactory, ImplementedInventory, IEnergyConsumer {
    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(6, ItemStack.EMPTY);

    private static final int INPUT_SLOT = 0;
    private static final int OUTPUT_SLOT_1 = 1;
    private static final int OUTPUT_SLOT_2 = 2;
    private static final int OUTPUT_SLOT_3 = 3;
    private static final int OUTPUT_SLOT_4 = 4;
    private static final int OUTPUT_SLOT_5 = 5;

    protected final PropertyDelegate propertyDelegate;
    private int progress = 0;
    private int maxProgress = 10;

    // Energy properties
    private static final int ENERGY_DEMAND_PER_TICK = 1; // Consumes 1 energy per tick when active
    private EnergyNetwork network;
    public boolean needsNetworkRefresh = false;
    private int energyReceived = 0; // Energy received this tick

    private void resetProgress() {
        this.progress = 0;
        this.maxProgress = 10;
    }

    public CrusherBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRUSHER_BLOCK_ENTITY, pos, state);
        this.propertyDelegate = new PropertyDelegate() {
            @Override
            public int get(int index) {
                switch (index) {
                    case 0 -> {return CrusherBlockEntity.this.progress;}
                    case 1 -> {return CrusherBlockEntity.this.maxProgress;}
                    default -> {return 0;}
                }
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                    case 0 -> CrusherBlockEntity.this.progress = value;
                    case 1 -> CrusherBlockEntity.this.maxProgress = value;
                }
            }

            @Override
            public int size() {
                return 2;
            }
        };
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.circuitmod.crusher");
    }

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new CrusherScreenHandler(syncId, playerInventory, this, this.propertyDelegate);
    }

    @Override
    public DefaultedList<ItemStack> getItems() {
        return inventory;
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        Inventories.writeNbt(nbt, inventory, registryLookup);
        nbt.putInt("crusher.progress", progress);
        nbt.putInt("crusher.max_progress", maxProgress);
        nbt.putInt("energy_received", energyReceived);
        
        // Save network data if we have a network
        if (network != null) {
            NbtCompound networkNbt = new NbtCompound();
            network.writeToNbt(networkNbt);
            nbt.put("energy_network", networkNbt);
        }
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        Inventories.readNbt(nbt, inventory, registryLookup);
        progress = nbt.getInt("crusher.progress").get();
        maxProgress = nbt.getInt("crusher.max_progress").get();
        energyReceived = nbt.getInt("energy_received").get();
        
        // Load network data
        if (nbt.contains("energy_network")) {
            this.network = new EnergyNetwork();
            NbtCompound networkNbt = nbt.getCompound("energy_network").orElse(new NbtCompound());
            network.readFromNbt(networkNbt);
        }
        this.needsNetworkRefresh = true;
        
        super.readNbt(nbt, registryLookup);
    }

    // Add network handling logic to the tick method
    public void tick(World world, BlockPos pos, BlockState state, CrusherBlockEntity entity) {
        // Handle network refresh
        if (needsNetworkRefresh && world != null && !world.isClient()) {
            needsNetworkRefresh = false;
            if (network != null) {
                network.addBlock(pos, this);
            }
        }
        
        // Do NOT reset energyReceived at the start!
        // Only reset at the end of the tick, after processing.
        // Remove isReceivingPower logic entirely.
        
        boolean wasRunning = state.get(CrusherBlock.RUNNING);
        boolean isRunning = hasRecipe() && energyReceived > 0;

        // DEBUG LOGGING
        if (world != null && !world.isClient() && world.getTime() % 20 == 0) {
            Circuitmod.LOGGER.info("[CRUSHER-DEBUG] At {}: hasRecipe={}, energyReceived={}, progress={}/{}", pos, hasRecipe(), energyReceived, progress, maxProgress);
        }
        
        if (isRunning) {
            increaseCrushProgress();
            markDirty(world, pos, state);
            if(hasCrushingFinished()) {
                if (world != null && !world.isClient()) {
                    Circuitmod.LOGGER.info("[CRUSHER-DEBUG] At {}: Crafting item!", pos);
                }
                craftItem();
                resetProgress();
            }
        } else {
            if (progress != 0 && world != null && !world.isClient()) {
                Circuitmod.LOGGER.info("[CRUSHER-DEBUG] At {}: Resetting progress.", pos);
            }
            resetProgress();
        }
        
        // Update block state if running state changed
        if (wasRunning != isRunning && world != null && !world.isClient()) {
            world.setBlockState(pos, state.with(CrusherBlock.RUNNING, isRunning), Block.NOTIFY_ALL);
        }

        // Reset energy received at the end of the tick
        energyReceived = 0;
    }

    private void craftItem() {
        Optional<RecipeEntry<CrusherRecipe>> recipe = getCurrentRecipe();
        if (recipe.isPresent()) {
            CrusherRecipe crusherRecipe = recipe.get().value();
            ItemStack output1 = crusherRecipe.output1().copy();
            ItemStack output2 = crusherRecipe.output2().copy();
            
            this.removeStack(INPUT_SLOT, 1);
            
            // Try to add output1 to any available output slot
            if (!output1.isEmpty()) {
                addToOutputSlot(output1);
            }
            
            // Try to add output2 to any available output slot
            if (!output2.isEmpty()) {
                addToOutputSlot(output2);
            }
        }
    }

    private void addToOutputSlot(ItemStack output) {
        // Find an empty slot or a slot with the same item
        for (int i = OUTPUT_SLOT_1; i <= OUTPUT_SLOT_5; i++) {
            ItemStack currentStack = this.getStack(i);
            if (currentStack.isEmpty()) {
                this.setStack(i, output);
                return;
            } else if (ItemStack.areItemsEqual(currentStack, output) && 
                      currentStack.getCount() < currentStack.getMaxCount()) {
                int spaceLeft = currentStack.getMaxCount() - currentStack.getCount();
                int toAdd = Math.min(spaceLeft, output.getCount());
                ItemStack newStack = currentStack.copy();
                newStack.increment(toAdd);
                this.setStack(i, newStack);
                return;
            }
        }
    }

    private void increaseCrushProgress() {
        this.progress++;
    }

    private boolean hasCrushingFinished() {
        return this.progress >= this.maxProgress;
    }

    private boolean hasRecipe() {
        Optional<RecipeEntry<CrusherRecipe>> recipe = getCurrentRecipe();
        if(recipe.isEmpty()) {
            return false;
        }
        
        CrusherRecipe crusherRecipe = recipe.get().value();
        ItemStack output1 = crusherRecipe.output1();
        ItemStack output2 = crusherRecipe.output2();
        
        // Check if we can output both items
        return canInsertItemIntoOutputSlots(output1) && canInsertItemIntoOutputSlots(output2);
    }

    private boolean canInsertItemIntoOutputSlots(ItemStack output) {
        if (output.isEmpty()) return true;
        
        for (int i = OUTPUT_SLOT_1; i <= OUTPUT_SLOT_5; i++) {
            ItemStack currentStack = this.getStack(i);
            if (currentStack.isEmpty()) {
                return true;
            } else if (ItemStack.areItemsEqual(currentStack, output) && 
                      currentStack.getCount() < currentStack.getMaxCount()) {
                return true;
            }
        }
        return false;
    }

    private Optional<RecipeEntry<CrusherRecipe>> getCurrentRecipe() {
        if (this.getWorld() instanceof ServerWorld serverWorld) {
            return serverWorld.getRecipeManager()
                    .getFirstMatch(ModRecipes.CRUSHER_TYPE, new CrusherRecipeInput(inventory.get(INPUT_SLOT)), this.getWorld());
        }
        return Optional.empty();
    }

    // Power consumption methods
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
            Circuitmod.LOGGER.info("[CRUSHER-NETWORK] Crusher at " + pos + " changing networks: " + this.network.getNetworkId() + " -> " + network.getNetworkId());
        } else if (network != null && this.network == null) {
            Circuitmod.LOGGER.info("[CRUSHER-NETWORK] Crusher at " + pos + " connecting to network: " + network.getNetworkId());
        } else if (this.network != null && network == null) {
            Circuitmod.LOGGER.info("[CRUSHER-NETWORK] Crusher at " + pos + " disconnecting from network: " + this.network.getNetworkId());
        }
        
        this.network = network;
    }

    @Override
    public int consumeEnergy(int energyOffered) {
        if (world == null || world.isClient()) {
            return 0;
        }

        int energyToConsume = Math.min(energyOffered, ENERGY_DEMAND_PER_TICK);
        if (energyToConsume > 0) {
            this.energyReceived += energyToConsume;
        }
        return energyToConsume;
    }

    @Override
    public int getEnergyDemand() {
        return ENERGY_DEMAND_PER_TICK;
    }

    @Override
    public Direction[] getInputSides() {
        return Direction.values(); // Can receive from all sides
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        return createNbt(registryLookup);
    }
}