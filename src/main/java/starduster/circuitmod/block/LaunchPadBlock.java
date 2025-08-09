package starduster.circuitmod.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import starduster.circuitmod.Circuitmod;
import net.minecraft.world.Heightmap;

/**
 * Launch Pad block: when stepped on, if there is a diamond block directly beneath,
 * instantly teleports the player between Overworld and Moon. Consumes the diamond block.
 */
public class LaunchPadBlock extends Block {
    private static final RegistryKey<World> MOON_DIM_KEY = RegistryKey.of(RegistryKeys.WORLD, Identifier.of("circuitmod", "moon"));

    public LaunchPadBlock(@NotNull AbstractBlock.Settings settings) {
        super(settings);
    }

    @Override
    public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
        if (world.isClient()) {
            super.onSteppedOn(world, pos, state, entity);
            return;
        }

        if (!(entity instanceof ServerPlayerEntity serverPlayer)) {
            super.onSteppedOn(world, pos, state, entity);
            return;
        }

        // Require a diamond block directly below the launch pad
        BlockPos below = pos.down();
        BlockState belowState = world.getBlockState(below);
        if (!belowState.isOf(Blocks.DIAMOND_BLOCK)) {
            super.onSteppedOn(world, pos, state, entity);
            return;
        }

        ServerWorld currentWorld = serverPlayer.getServerWorld();
        RegistryKey<World> currentKey = currentWorld.getRegistryKey();
        RegistryKey<World> targetKey = (currentKey == World.OVERWORLD)
                ? MOON_DIM_KEY
                : (currentKey == MOON_DIM_KEY ? World.OVERWORLD : null);

        if (targetKey == null) {
            super.onSteppedOn(world, pos, state, entity);
            return;
        }

        ServerWorld targetWorld = serverPlayer.getServer().getWorld(targetKey);
        if (targetWorld == null) {
            super.onSteppedOn(world, pos, state, entity);
            return;
        }

        // Spawn high above ground (~200), clamped within dimension logical height
        double targetY = Math.min(200.0, targetWorld.getDimension().logicalHeight() - 10);

        Circuitmod.LOGGER.info("[LaunchPad] Teleporting {} from {} to {} at ({}, {}, {})",
                serverPlayer.getName().getString(), currentKey.getValue(), targetKey.getValue(),
                serverPlayer.getX(), targetY, serverPlayer.getZ());

        // Perform teleport
        serverPlayer.teleport(targetWorld, serverPlayer.getX(), targetY, serverPlayer.getZ(),
                java.util.Set.of(), serverPlayer.getYaw(), serverPlayer.getPitch(), false);

        // No initial velocity boost
        serverPlayer.setVelocity(0.0, 0.0, 0.0);
        // Grant Slow Falling for 60 seconds to ensure safe descent
        serverPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 1200, 0, false, true, true));

        // Consume the diamond block from the ORIGINAL world after successful teleport
        if (world instanceof ServerWorld originalWorld) {
            originalWorld.setBlockState(below, Blocks.AIR.getDefaultState());
        }

        super.onSteppedOn(world, pos, state, entity);
    }
}

