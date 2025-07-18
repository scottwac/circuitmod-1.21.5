package starduster.circuitmod.block.entity;

import net.minecraft.block.AirBlock;
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
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.recipe.*;
import starduster.circuitmod.screen.BloomeryScreenHandler;
import starduster.circuitmod.screen.CrusherScreenHandler;
import starduster.circuitmod.util.ImplementedInventory;

import java.util.Optional;

public class CrusherBlockEntity extends BlockEntity implements NamedScreenHandlerFactory, ImplementedInventory {
    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(3, ItemStack.EMPTY);

    private static final int INPUT_SLOT = 0;
    private static final int OUTPUT_SLOT_1 = 1;
    private static final int OUTPUT_SLOT_2 = 2;
    private static final int OUTPUT_SLOT_3 = 3;
    private static final int OUTPUT_SLOT_4 = 4;
    private static final int OUTPUT_SLOT_5 = 5;

    protected final PropertyDelegate propertyDelegate;
    private int progress = 0;
    private int maxProgress = 10;

    private int item1reservedSlot = 0;
    private int item2reservedSlot = 0;

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
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        Inventories.readNbt(nbt, inventory, registryLookup);
        progress = nbt.getInt("crusher.progress").get();
        maxProgress = nbt.getInt("crusher.max_progress").get();
        super.readNbt(nbt, registryLookup);

    }

    // Add network handling logic to the tick method
    public void tick(World world, BlockPos pos, BlockState state, CrusherBlockEntity entity) {
//        if(!hasRecipe() && hasPower()) {
//            consumeIdlePower();
//        }
        if (hasRecipe()) { //add hosPower() later on
            //consumeRunningPower();
            increaseCrushProgress();
            markDirty(world, pos, state);
            if(hasCrushingFinished()) {
                craftItem();
                resetProgress();
            }

        } else {
            resetProgress();
        }
    }

    private void craftItem() {
        Optional<RecipeEntry<CrusherRecipe>> recipe = getCurrentRecipe();
        ItemStack outputSlot1 = recipe.get().value().output1(); //recipe.output2.copy();
        ItemStack outputSlot2 = recipe.get().value().output2(); //recipe.output2.copy();
        this.removeStack(INPUT_SLOT, 1);

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
        return false; //checkIf1CanOutput() && checkIf2CanOutput();
    }

//    private boolean checkIf1CanOutput() {
//        Optional<RecipeEntry<CrusherRecipe>> recipe = getCurrentRecipe();
//        ItemStack outputSlot1 = recipe.get().value().output1(); //recipe.output2.copy();
//        //ItemStack outputSlot2 = recipe.get().value().output2(); //recipe.output2.copy();
//
//        int max1Count = this.getStack(OUTPUT_SLOT_1).isEmpty() ? 64 : this.getStack(OUTPUT_SLOT_1).getMaxCount();
//        int current1Count = this.getStack(OUTPUT_SLOT_1).getCount();
//
//        int max2Count = this.getStack(OUTPUT_SLOT_2).isEmpty() ? 64 : this.getStack(OUTPUT_SLOT_2).getMaxCount();
//        int current2Count = this.getStack(OUTPUT_SLOT_2).getCount();
//
//        int max3Count = this.getStack(OUTPUT_SLOT_3).isEmpty() ? 64 : this.getStack(OUTPUT_SLOT_3).getMaxCount();
//        int current3Count = this.getStack(OUTPUT_SLOT_3).getCount();
//
//        int max4Count = this.getStack(OUTPUT_SLOT_4).isEmpty() ? 64 : this.getStack(OUTPUT_SLOT_4).getMaxCount();
//        int current4Count = this.getStack(OUTPUT_SLOT_4).getCount();
//
//        int max5Count = this.getStack(OUTPUT_SLOT_5).isEmpty() ? 64 : this.getStack(OUTPUT_SLOT_5).getMaxCount();
//        int current5Count = this.getStack(OUTPUT_SLOT_5).getCount();
//
//        if(this.getStack(OUTPUT_SLOT_1).isEmpty()) {
//            item1reservedSlot = 1;
//        } else if (this.getStack(OUTPUT_SLOT_2).isEmpty()) {
//            item1reservedSlot = 2;
//        } else if (this.getStack(OUTPUT_SLOT_3).isEmpty()) {
//            item1reservedSlot = 3;
//        } else if (this.getStack(OUTPUT_SLOT_4).isEmpty()) {
//            item1reservedSlot = 4;
//        } else if (this.getStack(OUTPUT_SLOT_5).isEmpty()) {
//            item1reservedSlot = 5;
//        } else if (max1Count >= current1Count + outputSlot1.getCount()) {
//            item1reservedSlot = 1;
//        } else if (max2Count >= current2Count + outputSlot1.getCount()) {
//            item1reservedSlot = 2;
//        } else if (max3Count >= current3Count + outputSlot1.getCount()) {
//            item1reservedSlot = 3;
//        } else if (max4Count >= current4Count + outputSlot1.getCount()) {
//            item1reservedSlot = 4;
//        } else if (max5Count >= current5Count + outputSlot1.getCount()) {
//            item1reservedSlot = 5;
//        } else {
//            item1reservedSlot = 0;
//        }
//        return item1reservedSlot > 0;
//    }
//
//    private boolean checkIf2CanOutput() {
//        Optional<RecipeEntry<CrusherRecipe>> recipe = getCurrentRecipe();
//        ItemStack outputSlot2 = recipe.get().value().output2(); //recipe.output2.copy();
//
//        int max1Count = this.getStack(OUTPUT_SLOT_1).isEmpty() ? 64 : this.getStack(OUTPUT_SLOT_1).getMaxCount();
//        int current1Count = this.getStack(OUTPUT_SLOT_1).getCount();
//
//        int max2Count = this.getStack(OUTPUT_SLOT_2).isEmpty() ? 64 : this.getStack(OUTPUT_SLOT_2).getMaxCount();
//        int current2Count = this.getStack(OUTPUT_SLOT_2).getCount();
//
//        int max3Count = this.getStack(OUTPUT_SLOT_3).isEmpty() ? 64 : this.getStack(OUTPUT_SLOT_3).getMaxCount();
//        int current3Count = this.getStack(OUTPUT_SLOT_3).getCount();
//
//        int max4Count = this.getStack(OUTPUT_SLOT_4).isEmpty() ? 64 : this.getStack(OUTPUT_SLOT_4).getMaxCount();
//        int current4Count = this.getStack(OUTPUT_SLOT_4).getCount();
//
//        int max5Count = this.getStack(OUTPUT_SLOT_5).isEmpty() ? 64 : this.getStack(OUTPUT_SLOT_5).getMaxCount();
//        int current5Count = this.getStack(OUTPUT_SLOT_5).getCount();
//
//        if(outputSlot2.isOf(Items.AIR)) {
//            item2reservedSlot = 0;
//        } else if(this.getStack(OUTPUT_SLOT_1).isEmpty() && item1reservedSlot != 1) {
//            item2reservedSlot = 1;
//        } else if (this.getStack(OUTPUT_SLOT_2).isEmpty() && item1reservedSlot != 2) {
//            item2reservedSlot = 2;
//        } else if (this.getStack(OUTPUT_SLOT_3).isEmpty() && item1reservedSlot != 3) {
//            item2reservedSlot = 3;
//        } else if (this.getStack(OUTPUT_SLOT_4).isEmpty() && item1reservedSlot != 4) {
//            item2reservedSlot = 4;
//        } else if (this.getStack(OUTPUT_SLOT_5).isEmpty() && item1reservedSlot != 5) {
//            item2reservedSlot = 5;
//        } else if (max1Count >= current1Count + outputSlot2.getCount() && item1reservedSlot != 1) {
//            item2reservedSlot = 1;
//        } else if (max2Count >= current2Count + outputSlot2.getCount() && item1reservedSlot != 2) {
//            item2reservedSlot = 2;
//        } else if (max3Count >= current3Count + outputSlot2.getCount() && item1reservedSlot != 3) {
//            item2reservedSlot = 3;
//        } else if (max4Count >= current4Count + outputSlot2.getCount() && item1reservedSlot != 4) {
//            item2reservedSlot = 4;
//        } else if (max5Count >= current5Count + outputSlot2.getCount() && item1reservedSlot != 5) {
//            item2reservedSlot = 5;
//        } else {
//            item2reservedSlot = 0;
//        }
//        return item2reservedSlot > 0;
//    }

    private Optional<RecipeEntry<CrusherRecipe>> getCurrentRecipe() {
        return ((ServerWorld) this.getWorld()).getRecipeManager()
                .getFirstMatch(ModRecipes.CRUSHER_TYPE, new CrusherRecipeInput(inventory.get(INPUT_SLOT)), this.getWorld());
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