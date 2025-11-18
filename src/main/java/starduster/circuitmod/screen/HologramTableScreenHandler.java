package starduster.circuitmod.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import starduster.circuitmod.block.entity.HologramTableBlockEntity;

public class HologramTableScreenHandler extends ScreenHandler {
    private final BlockPos blockEntityPos;
    private final PropertyDelegate propertyDelegate;
    
    public HologramTableScreenHandler(int syncId, PlayerInventory playerInventory, ModScreenHandlers.HologramTableData data) {
        this(syncId, playerInventory, data.pos(), new ArrayPropertyDelegate(HologramTableBlockEntity.PROPERTY_COUNT));
    }
    
    public HologramTableScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos, PropertyDelegate delegate) {
        super(ModScreenHandlers.HOLOGRAM_TABLE_SCREEN_HANDLER, syncId);
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
    
    public int getMinX() {
        return propertyDelegate.get(0);
    }
    
    public int getMaxX() {
        return propertyDelegate.get(1);
    }
    
    public int getMinZ() {
        return propertyDelegate.get(2);
    }
    
    public int getMaxZ() {
        return propertyDelegate.get(3);
    }
    
    public int getMinY() {
        return propertyDelegate.get(4);
    }
    
    public boolean isCustomArea() {
        return propertyDelegate.get(5) == 1;
    }
}

