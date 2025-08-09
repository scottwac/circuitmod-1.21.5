package starduster.circuitmod.mixin;

import net.minecraft.block.BlockState;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import starduster.circuitmod.block.ModBlocks;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.util.LaunchPadController;

@Mixin(PlayerEntity.class)
public abstract class LaunchPadPlayerMixin {

    @Unique
    private int circuitmod$launchCooldownTicks = 0;

    @Unique
    private boolean circuitmod$launchedUpwards = false;

    private static final int LAUNCH_COOLDOWN_TICKS = 80;

    private static final double TELEPORT_TRIGGER_Y = 500.0; // unused when instant-teleport is enabled

    @Unique
    private int circuitmod$launchBoostTicks = 0;

    private static final RegistryKey<World> MOON_DIM_KEY = RegistryKey.of(RegistryKeys.WORLD, Identifier.of("circuitmod", "moon"));

    @Inject(method = "tick", at = @At("TAIL"))
    private void circuitmod$handleLaunchPads(CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;

        if (!(self instanceof ServerPlayerEntity serverPlayer)) return; // server-side only

        if (circuitmod$launchCooldownTicks > 0) {
            circuitmod$launchCooldownTicks--;
        }

        // Check for a single launch pad block directly underfoot when on ground and not on cooldown
        if (self.isOnGround() && circuitmod$launchCooldownTicks == 0) {
            BlockPos below = BlockPos.ofFloored(self.getX(), self.getY() - 1.0, self.getZ());
            BlockState belowState = self.getWorld().getBlockState(below);
            boolean onPad = belowState.isOf(ModBlocks.LAUNCH_PAD);
            Circuitmod.LOGGER.info("[LaunchPad] onGround={}, cooldown=0, below={}, onPad={} (world={})",
                    self.isOnGround(), below, onPad, self.getWorld().getRegistryKey().getValue());
            if (onPad) {
                // Instant-teleport: make pad usage near-instant in both directions
                ServerWorld currentWorld = serverPlayer.getServerWorld();
                RegistryKey<World> currentKey = currentWorld.getRegistryKey();
                RegistryKey<World> targetKey = (currentKey == World.OVERWORLD) ? MOON_DIM_KEY : (currentKey == MOON_DIM_KEY ? World.OVERWORLD : null);

                if (targetKey != null) {
                    ServerWorld targetWorld = serverPlayer.getServer().getWorld(targetKey);
                    if (targetWorld != null) {
                        double targetY = Math.min(350.0, targetWorld.getDimension().logicalHeight() - 10);
                        Circuitmod.LOGGER.info("[LaunchPad] Instant-teleport {} from {} to {} at ({}, {}, {})",
                                self.getName().getString(), currentKey.getValue(), targetKey.getValue(), self.getX(), targetY, self.getZ());
                        serverPlayer.teleport(targetWorld, self.getX(), targetY, self.getZ(), java.util.Set.of(), serverPlayer.getYaw(), serverPlayer.getPitch(), false);
                        serverPlayer.setVelocity(0.0, -0.1, 0.0);
                        // Grant Slow Falling for safe descent regardless of direction (moon <-> overworld)
                        serverPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 1200, 0, false, true, true));
                    }
                    // Cooldown and cleanup
                    circuitmod$launchedUpwards = false;
                    circuitmod$launchBoostTicks = 0;
                    LaunchPadController.clear(self);
                    circuitmod$launchCooldownTicks = LAUNCH_COOLDOWN_TICKS;
                }
            }
        }

        // Boost phase disabled for instant-teleport mode

        // Teleport when reaching the trigger altitude after a launch
        if (circuitmod$launchedUpwards && self.getY() >= TELEPORT_TRIGGER_Y) {
            ServerWorld currentWorld = serverPlayer.getServerWorld();
            RegistryKey<World> currentKey = currentWorld.getRegistryKey();

            RegistryKey<World> targetKey = null;
            if (currentKey == World.OVERWORLD) {
                targetKey = MOON_DIM_KEY;
            } else if (currentKey == MOON_DIM_KEY) {
                targetKey = World.OVERWORLD;
            }

            if (targetKey != null) {
                ServerWorld targetWorld = serverPlayer.getServer().getWorld(targetKey);
                if (targetWorld != null) {
                    double targetY = Math.min(350.0, targetWorld.getDimension().logicalHeight() - 10);
                    Circuitmod.LOGGER.info("[LaunchPad] Teleporting {} from {} to {} at ({}, {}, {})",
                            self.getName().getString(), currentKey.getValue(), targetKey.getValue(), self.getX(), targetY, self.getZ());
                    serverPlayer.teleport(targetWorld, self.getX(), targetY, self.getZ(), java.util.Set.of(), serverPlayer.getYaw(), serverPlayer.getPitch(), false);
                    serverPlayer.setVelocity(0.0, -0.1, 0.0); // start gentle descent
                    // Grant Slow Falling for safe descent regardless of direction (moon <-> overworld)
                    serverPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 1200, 0, false, true, true));
                }
                circuitmod$launchedUpwards = false;
                circuitmod$launchBoostTicks = 0;
                circuitmod$launchCooldownTicks = LAUNCH_COOLDOWN_TICKS;
                LaunchPadController.clear(self);
            } else {
                Circuitmod.LOGGER.warn("[LaunchPad] No target dimension mapping from {}", currentKey.getValue());
            }
        }
    }

}

