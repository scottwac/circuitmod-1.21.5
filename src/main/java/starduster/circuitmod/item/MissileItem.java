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
        ItemStack stack = player.getStackInHand(hand);
        
        if (!world.isClient) {
            // Get player position and look direction
            Vec3d pos = player.getPos().add(0, player.getStandingEyeHeight(), 0);
            Vec3d lookDir = player.getRotationVector();
            
            // Spawn missile slightly in front of player
            Vec3d spawnPos = pos.add(lookDir.multiply(2.0));
            
            MissileEntity missile = new MissileEntity(world, spawnPos);
            
            // You can customize the target here
            // For now it defaults to 100 blocks east
            
            world.spawnEntity(missile);
            
            Circuitmod.LOGGER.info("Missile spawned at [{}, {}, {}] by {}", 
                spawnPos.x, spawnPos.y, spawnPos.z, player.getName().getString());
            
            if (!player.isCreative()) {
                stack.decrement(1);
            }
        }
        
        return ActionResult.SUCCESS;
    }
}

