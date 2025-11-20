package starduster.circuitmod.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class SatelliteControlScreenHandler extends ScreenHandler {
    private final BlockPos blockEntityPos;
    private List<String> outputLines;

    public SatelliteControlScreenHandler(int syncId, PlayerInventory playerInventory, ModScreenHandlers.SatelliteControlData data) {
        this(syncId, playerInventory, data.pos());
        this.outputLines = new ArrayList<>(data.outputLines());
    }

    public SatelliteControlScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos) {
        super(ModScreenHandlers.SATELLITE_CONTROL_SCREEN_HANDLER, syncId);
        this.blockEntityPos = pos;
        this.outputLines = new ArrayList<>();
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

    public List<String> getOutputLines() {
        return outputLines;
    }

    public void setOutputLines(List<String> lines) {
        this.outputLines = new ArrayList<>(lines);
    }
}

