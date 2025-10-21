package starduster.circuitmod.block.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.Item;
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
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.block.ModBlocks;
import starduster.circuitmod.block.RubberTapBlock;
import starduster.circuitmod.block.SharingaLogBlock;
import starduster.circuitmod.block.machines.BloomeryBlock;
import starduster.circuitmod.item.ModItems;
import starduster.circuitmod.recipe.BloomeryRecipe;
import starduster.circuitmod.recipe.BloomeryRecipeInput;
import starduster.circuitmod.recipe.ModRecipes;
import starduster.circuitmod.screen.BloomeryScreenHandler;
import starduster.circuitmod.screen.RubberTapScreenHandler;
import starduster.circuitmod.util.ImplementedInventory;

import java.util.Optional;

public class RubberTapBlockEntity extends BlockEntity implements NamedScreenHandlerFactory, ImplementedInventory {
    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(3, ItemStack.EMPTY);

    private static final int OUTPUT_SLOT = 0;

    protected final PropertyDelegate propertyDelegate;
    private int progress = 0;
    private int absMaxProgress = 3600; //time per rubber in ticks. 36000 = 30 minutes per item.
    private int maxProgress = absMaxProgress;

    //TODO set absMaxProgress to 36000 once finished

    private int isOnLog = 0;

    public RubberTapBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RUBBER_TAP_BLOCK_ENTITY, pos, state);
        this.propertyDelegate = new PropertyDelegate() {
            @Override
            public int get(int index) {
                switch (index) {
                    case 0 -> {return RubberTapBlockEntity.this.progress;}
                    case 1 -> {return RubberTapBlockEntity.this.maxProgress;}
                    case 2 -> {return RubberTapBlockEntity.this.isOnLog;}
                    default -> {return 0;}
                }
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                    case 0 -> RubberTapBlockEntity.this.progress = value;
                    case 1 -> RubberTapBlockEntity.this.maxProgress = value;
                    case 2 -> RubberTapBlockEntity.this.isOnLog = value;
                }
            }

            @Override
            public int size() {
                return 3;
            }
        };
    }

    @Override
    public Text getDisplayName() {
        return Text.literal("");
    }
//    public Text getDisplayName() {
//        return Text.translatable("block.circuitmod.rubber_tap");
//    }

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new RubberTapScreenHandler(syncId, playerInventory, this, this.propertyDelegate);
    }

    @Override
    public DefaultedList<ItemStack> getItems() {
        return inventory;
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        Inventories.writeNbt(nbt, inventory, registryLookup);
        nbt.putInt("rubber_tap.progress", progress);
        nbt.putInt("rubber_tap.max_progress", maxProgress);
        nbt.putInt("rubber_tap.is_on_log", isOnLog);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        Inventories.readNbt(nbt, inventory, registryLookup);
        progress = nbt.getInt("rubber_tap.progress").get();
        maxProgress = nbt.getInt("rubber_tap.max_progress").get();
        isOnLog = nbt.getInt("rubber_tap.is_on_log").get();
        super.readNbt(nbt, registryLookup);
    }


    public void tick(World world, BlockPos pos, BlockState state, RubberTapBlockEntity blockEntity) {
        int levelBefore = blockEntity.fillLevel();
        //Logic to generate natural rubber.
        //Item generated can be changed by modifying craftItem() and canOutput()
        //Generation time can be changed by modifying absMaxProgress
        if(isOnLog(state) == true) {
            setIsOnLog();
        } else {setIsNotOnLog();}

        if(canOutput() && isOnLog(state)) {
            if (hasFinished()) {
                craftItem();
                resetProgress();
            } else {
                increaseProgress();
            }
        } else {
            resetProgress();
        }

        if (levelBefore != blockEntity.fillLevel() || fillLevel() == 0) {
            world.setBlockState(pos, world.getBlockState(pos).with(RubberTapBlock.FILL_LEVEL, blockEntity.fillLevel()), Block.NOTIFY_ALL);
        }

    }

    public int fillLevel() {
        int currentRubberCount = this.getStack(OUTPUT_SLOT).getCount();
        int mappedLevel = 0;
        if(currentRubberCount == 0) {
            mappedLevel = 0;
        } else if (currentRubberCount > 0 && currentRubberCount < 30) {
            mappedLevel = 1;
        } else if (currentRubberCount >= 30 && currentRubberCount < 60) {
            mappedLevel = 2;
        } else if (currentRubberCount >= 60) {
            mappedLevel = 3;
        }
        return mappedLevel;
    }

    private void resetProgress() {
        this.progress = 0;
        this.maxProgress = absMaxProgress; //default max time
    }

    private void setIsOnLog() {
        this.isOnLog = 1;
    }

    private void setIsNotOnLog() {
        this.isOnLog = 0;
    }

    private void craftItem() {
        ItemStack output = new ItemStack(ModItems.NATURAL_RUBBER, 1);
        this.setStack(OUTPUT_SLOT, new ItemStack(output.getItem(), this.getStack(OUTPUT_SLOT).getCount() + output.getCount()));
    }

    private boolean canOutput() {
        ItemStack output = new ItemStack(ModItems.NATURAL_RUBBER, 1);
        return canInsertAmountIntoOutputSlot(output.getCount()) && canInsertItemIntoOutputSlot(output);
    }

    private boolean isOnLog(BlockState state) {
        Direction facing = state.get(Properties.HORIZONTAL_FACING);
        BlockState oppositeBlockState = world.getBlockState(pos.offset(facing.getOpposite()));
        return oppositeBlockState.isOf(ModBlocks.SHARINGA_LOG) && oppositeBlockState.get(SharingaLogBlock.NATURAL);
    }

    private boolean hasFinished() {
        return this.progress >= this.maxProgress;
    }

    private void increaseProgress() {
        this.progress++;
    }


    private boolean canInsertItemIntoOutputSlot(ItemStack output) {
        return this.getStack(OUTPUT_SLOT).isEmpty() || this.getStack(OUTPUT_SLOT).getItem() == output.getItem();
    }

    private boolean canInsertAmountIntoOutputSlot(int count) {
        int maxCount = this.getStack(OUTPUT_SLOT).isEmpty() ? 64 : this.getStack(OUTPUT_SLOT).getMaxCount();
        int currentCount = this.getStack(OUTPUT_SLOT).getCount();

        return maxCount >= currentCount + count;
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
