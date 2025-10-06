package starduster.circuitmod.mixin;

import net.minecraft.block.*;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.ModBlocks;
import starduster.circuitmod.util.TorchPlacementHelper;

/**
 * Mixin to intercept torch placement in the Luna dimension and replace with extinguished torches.
 */
@Mixin(World.class)
public class TorchPlacementMixin {

    /**
     * Intercepts setBlockState to replace torch blocks with extinguished versions in Luna dimension.
     */
    @SuppressWarnings("resource")
    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z", at = @At("HEAD"), cancellable = true)
    private void circuitmod$replaceTorchesInLuna(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<Boolean> cir) {
        World world = (World) (Object) this;
        EnumProperty<Direction> FACING = HorizontalFacingBlock.FACING;
        
        // Only apply to Luna dimension
        if (world.getRegistryKey().getValue().equals(Identifier.of("circuitmod", "luna"))) {
            Block block = state.getBlock();
            
            // Check if placing a regular torch
            if (block == Blocks.TORCH || block == Blocks.SOUL_TORCH || block == Blocks.REDSTONE_TORCH) {
                // Replace with extinguished torch, preserving all other state properties
                BlockState extinguishedState = ModBlocks.EXTINGUISHED_TORCH.getDefaultState();
                
                // Call the original method with the extinguished torch instead
                boolean result = ((World) (Object) this).setBlockState(pos, extinguishedState, flags);
                
                // Play extinguish sound effect and grant advancement
                if (result) {
                    world.playSound(null, pos, SoundEvents.BLOCK_CANDLE_EXTINGUISH, SoundCategory.BLOCKS, 1f, 1f);
                    double d = pos.getX() + 0.5;
                    double e = pos.getY() + 0.7;
                    double f = pos.getZ() + 0.5;
                    world.addParticleClient(ParticleTypes.SMOKE, d, e, f, 0.0, 0.0, 0.0);
                    
                    // Grant advancement to the player who placed the torch
                    circuitmod$grantTorchAdvancementDirect(world, pos);
                }
                
                cir.setReturnValue(result);
                return;
            }
            
            // Check if placing a wall torch
            if (block == Blocks.WALL_TORCH || block == Blocks.SOUL_WALL_TORCH || block == Blocks.REDSTONE_WALL_TORCH) {
                // Replace with extinguished wall torch, preserving facing direction
                BlockState extinguishedState = ModBlocks.EXTINGUISHED_WALL_TORCH.getDefaultState();
                
                // Copy the facing property from the original wall torch
                if (state.contains(WallTorchBlock.FACING)) {
                    extinguishedState = extinguishedState.with(WallTorchBlock.FACING, state.get(WallTorchBlock.FACING));
                }
                
                // Call the original method with the extinguished wall torch instead
                boolean result = ((World) (Object) this).setBlockState(pos, extinguishedState, flags);
                
                // Play extinguish sound effect and grant advancement
                if (result) {
                    world.playSound(null, pos, SoundEvents.BLOCK_CANDLE_EXTINGUISH, SoundCategory.BLOCKS, 1f, 1f);
                    Direction direction = state.get(FACING);
                    double d = pos.getX() + 0.5;
                    double e = pos.getY() + 0.7;
                    double f = pos.getZ() + 0.5;
                    Direction direction2 = direction.getOpposite();
                    world.addParticleClient(ParticleTypes.SMOKE, d + 0.27 * direction2.getOffsetX(), e + 0.20, f + 0.27 * direction2.getOffsetZ(), 0.0, 0.0, 0.0);
                    
                    // Grant advancement to the player who placed the torch
                    circuitmod$grantTorchAdvancementDirect(world, pos);
                }
                
                cir.setReturnValue(result);
                return;
            }
        }
    }
    
    /**
     * Grants the luna torch advancement to the player stored in ThreadLocal.
     */
    @Unique
    private void circuitmod$grantTorchAdvancement() {
        ServerPlayerEntity player = TorchPlacementHelper.placingPlayer.get();
        if (player != null) {
            // Trigger our custom criterion
            Circuitmod.LOGGER.info("[TORCH] Triggering torch placement advancement for player: {}", player.getName().getString());
            Circuitmod.TORCH_PLACED_IN_LUNA.trigger(player);
            TorchPlacementHelper.placingPlayer.remove(); // Clean up ThreadLocal
        } else {
            Circuitmod.LOGGER.warn("[TORCH] Player is null when trying to grant torch advancement!");
        }
    }
    
    /**
     * Grants the torch advancement by finding nearby players directly.
     */
    @Unique
    private void circuitmod$grantTorchAdvancementDirect(World world, BlockPos pos) {
        if (!world.isClient && world instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            // Find all players within 10 blocks of the torch placement
            java.util.List<ServerPlayerEntity> nearbyPlayers = serverWorld.getPlayers(player -> 
                player.squaredDistanceTo(pos.getX(), pos.getY(), pos.getZ()) <= 100.0 // 10 blocks squared
            );
            
            for (ServerPlayerEntity player : nearbyPlayers) {
                Circuitmod.LOGGER.info("[TORCH] Triggering torch placement advancement for nearby player: {}", player.getName().getString());
                Circuitmod.TORCH_PLACED_IN_LUNA.trigger(player);
            }
        }
    }
}

/**
 * Mixin to capture the player when they use an item to place a block.
 */
@Mixin(net.minecraft.item.ItemStack.class)
abstract class ItemStackPlacementMixin {
    
    @Inject(method = "useOnBlock", at = @At("HEAD"))
    private void circuitmod$capturePlayerOnUse(net.minecraft.item.ItemUsageContext context, CallbackInfoReturnable<net.minecraft.util.ActionResult> cir) {
        if (context.getPlayer() instanceof ServerPlayerEntity player) {
            // Store the player in ThreadLocal so the World mixin can access it
            Circuitmod.LOGGER.info("[TORCH] Captured player in useOnBlock: {}", player.getName().getString());
            TorchPlacementHelper.placingPlayer.set(player);
        }
    }
}
