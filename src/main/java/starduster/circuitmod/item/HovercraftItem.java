package starduster.circuitmod.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import starduster.circuitmod.entity.HovercraftEntity;

public class HovercraftItem extends Item {
    
    public HovercraftItem(Settings settings) {
        super(settings);
    }
    
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        BlockPos blockPos = context.getBlockPos();
        PlayerEntity player = context.getPlayer();
        ItemStack itemStack = context.getStack();
        
        if (!world.isClient) {
            ServerWorld serverWorld = (ServerWorld) world;
            
            // Position the hovercraft slightly above the clicked block
            Vec3d spawnPos = new Vec3d(
                blockPos.getX() + 0.5,
                blockPos.getY() + 1.1, // Just above the block surface
                blockPos.getZ() + 0.5
            );
            
            // Create and spawn the hovercraft
            HovercraftEntity hovercraft = new HovercraftEntity(world, spawnPos.x, spawnPos.y, spawnPos.z);
            
            // Set the hovercraft's rotation to match the player's facing direction
            if (player != null) {
                hovercraft.setYaw(player.getYaw());
            }
            
            // Spawn the entity in the world
            serverWorld.spawnEntity(hovercraft);
            
            // Emit a placement sound/event
            serverWorld.emitGameEvent(GameEvent.ENTITY_PLACE, blockPos, GameEvent.Emitter.of(player, serverWorld.getBlockState(blockPos)));
            
            // Consume the item (unless in creative mode)
            if (player == null || !player.getAbilities().creativeMode) {
                itemStack.decrement(1);
            }
            
            return ActionResult.SUCCESS;
        }
        
        return ActionResult.CONSUME; // On client side, just consume the action
    }
}
