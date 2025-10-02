package starduster.circuitmod.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import starduster.circuitmod.block.ModBlocks;

/**
 * Mixin to intercept torch placement in the Luna dimension and replace with extinguished torches.
 */
@Mixin(World.class)
public class TorchPlacementMixin {

    /**
     * Intercepts setBlockState to replace torch blocks with extinguished versions in Luna dimension.
     */
    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z", at = @At("HEAD"), cancellable = true)
    private void circuitmod$replaceTorchesInLuna(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<Boolean> cir) {
        World world = (World) (Object) this;
        
        // Only apply to Luna dimension
        if (world.getRegistryKey().getValue().equals(Identifier.of("circuitmod", "luna"))) {
            Block block = state.getBlock();
            
            // Check if placing a regular torch
            if (block == Blocks.TORCH) {
                // Replace with extinguished torch, preserving all other state properties
                BlockState extinguishedState = ModBlocks.EXTINGUISHED_TORCH.getDefaultState();
                
                // Call the original method with the extinguished torch instead
                boolean result = ((World) (Object) this).setBlockState(pos, extinguishedState, flags);
                
                // Play extinguish sound effect
                if (result) {
                    world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXTINGUISH_FIRE, SoundCategory.BLOCKS, 0.5f, 0.8f);
                }
                
                cir.setReturnValue(result);
                return;
            }
            
            // Check if placing a wall torch
            if (block == Blocks.WALL_TORCH) {
                // Replace with extinguished wall torch, preserving facing direction
                BlockState extinguishedState = ModBlocks.EXTINGUISHED_WALL_TORCH.getDefaultState();
                
                // Copy the facing property from the original wall torch
                if (state.contains(WallTorchBlock.FACING)) {
                    extinguishedState = extinguishedState.with(WallTorchBlock.FACING, state.get(WallTorchBlock.FACING));
                }
                
                // Call the original method with the extinguished wall torch instead
                boolean result = ((World) (Object) this).setBlockState(pos, extinguishedState, flags);
                
                // Play extinguish sound effect
                if (result) {
                    world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXTINGUISH_FIRE, SoundCategory.BLOCKS, 0.5f, 1.0f);
                }
                
                cir.setReturnValue(result);
                return;
            }
        }
    }
}
