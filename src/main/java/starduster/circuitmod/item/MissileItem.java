package starduster.circuitmod.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.entity.MissileEntity;

/**
 * Item that spawns and launches a missile when used
 */
public class MissileItem extends Item {
    
    public MissileItem(Settings settings) {
        super(settings);
    }
    
    @Override
    public ActionResult use(World world, PlayerEntity player, Hand hand) {
        // Missiles can only be placed on Missile Control Blocks
        // Right-clicking in the air or on the ground does nothing
        // The MissileControlBlock handles the placement logic
        return ActionResult.PASS;
    }
}

