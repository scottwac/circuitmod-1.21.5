package starduster.circuitmod.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import starduster.circuitmod.entity.ModEntities;
import starduster.circuitmod.entity.RocketEntity;

/**
 * Item used to spawn and place rocket entities
 */
public class RocketItem extends Item {
    
    public RocketItem(Settings settings) {
        super(settings);
    }
    
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        PlayerEntity player = context.getPlayer();
        
        if (!world.isClient && player != null) {
            // Spawn the rocket above the clicked block
            Vec3d spawnPos = Vec3d.ofBottomCenter(pos).add(0, 1, 0);
            
            // Check if there's enough space
            if (world.isSpaceEmpty(null, ModEntities.ROCKET.getDimensions().getBoxAt(spawnPos))) {
                RocketEntity rocket = new RocketEntity(world, spawnPos.x, spawnPos.y, spawnPos.z);
                rocket.setYaw(player.getYaw());
                
                world.spawnEntity(rocket);
                
                // Play placement sound
                world.playSound(null, pos, SoundEvents.BLOCK_METAL_PLACE, SoundCategory.BLOCKS, 1.0f, 1.0f);
                
                // Consume item if not in creative mode
                if (!player.getAbilities().creativeMode) {
                    context.getStack().decrement(1);
                }
                
                return ActionResult.SUCCESS;
            } else {
                // Not enough space
                player.sendMessage(Text.translatable("item.circuitmod.rocket.no_space"), true);
                return ActionResult.FAIL;
            }
        }
        
        return ActionResult.SUCCESS;
    }
    
    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        
        if (!world.isClient) {
            // Spawn rocket in front of player
            Vec3d spawnPos = user.getPos().add(user.getRotationVector().multiply(3));
            spawnPos = new Vec3d(spawnPos.x, user.getY() + 1, spawnPos.z);
            
            // Check if there's enough space
            if (world.isSpaceEmpty(null, ModEntities.ROCKET.getDimensions().getBoxAt(spawnPos))) {
                RocketEntity rocket = new RocketEntity(world, spawnPos.x, spawnPos.y, spawnPos.z);
                rocket.setYaw(user.getYaw());
                
                world.spawnEntity(rocket);
                
                // Play placement sound
                world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_METAL_PLACE, SoundCategory.BLOCKS, 1.0f, 1.0f);
                
                // Consume item if not in creative mode
                if (!user.getAbilities().creativeMode) {
                    stack.decrement(1);
                }
                
                return ActionResult.SUCCESS;
            } else {
                // Not enough space
                user.sendMessage(Text.translatable("item.circuitmod.rocket.no_space"), true);
                return ActionResult.FAIL;
            }
        }
        
        return ActionResult.SUCCESS;
    }
}
