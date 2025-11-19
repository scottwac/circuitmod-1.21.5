package starduster.circuitmod.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import starduster.circuitmod.block.entity.MissileControlBlockEntity;

public class MissileControlScreenHandler extends ScreenHandler {
    private final BlockPos blockEntityPos;
    private final PropertyDelegate propertyDelegate;

    public MissileControlScreenHandler(int syncId, PlayerInventory playerInventory, ModScreenHandlers.MissileControlData data) {
        this(syncId, playerInventory, data.pos(), new ArrayPropertyDelegate(MissileControlBlockEntity.PROPERTY_COUNT));
    }

    public MissileControlScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos, PropertyDelegate delegate) {
        super(ModScreenHandlers.MISSILE_CONTROL_SCREEN_HANDLER, syncId);
        this.blockEntityPos = pos;
        this.propertyDelegate = delegate;
        this.addProperties(delegate);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        if (player == null || blockEntityPos == null) {
            return false;
        }
        double maxDistance = 64.0;
        return player.squaredDistanceTo(
            blockEntityPos.getX() + 0.5,
            blockEntityPos.getY() + 0.5,
            blockEntityPos.getZ() + 0.5
        ) <= maxDistance;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    public BlockPos getBlockEntityPos() {
        return blockEntityPos;
    }

    public int getTargetX() {
        return propertyDelegate.get(0);
    }

    public int getTargetY() {
        return propertyDelegate.get(1);
    }

    public int getTargetZ() {
        return propertyDelegate.get(2);
    }
}

