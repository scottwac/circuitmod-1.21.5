package starduster.circuitmod.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import starduster.circuitmod.block.ModBlocks;
import net.minecraft.registry.Registries;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.util.LaunchPadController;

@Mixin(PlayerEntity.class)
public abstract class LaunchPadPlayerMixin {

    @Unique
    private int circuitmod$launchCooldownTicks = 0;

    @Unique
    private boolean circuitmod$launchedUpwards = false;

    private static final int LAUNCH_COOLDOWN_TICKS = 80;
    private static final double LAUNCH_VELOCITY_Y = 30.0;
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

        // Check for a 3x3 launch pad underfoot when on ground and not on cooldown
        if (self.isOnGround() && circuitmod$launchCooldownTicks == 0) {
            BlockPos base = BlockPos.ofFloored(self.getX(), self.getY() - 1.0, self.getZ());
            boolean hasPad = circuitmod$hasThreeByThreeLaunchPadNear(self.getWorld(), base);
            Circuitmod.LOGGER.info("[LaunchPad] onGround={}, cooldown=0, base={}, has3x3={} (world={})",
                    self.isOnGround(), base, hasPad, self.getWorld().getRegistryKey().getValue());
            if (hasPad) {
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

    @Unique
    private static boolean circuitmod$hasThreeByThreeLaunchPadNear(World world, BlockPos baseBelow) {
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                BlockPos center = baseBelow.add(cx, 0, cz);
                boolean allPads = true;
                for (int dx = -1; dx <= 1 && allPads; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos p = center.add(dx, 0, dz);
                        BlockState state = world.getBlockState(p);
                        if (!state.isOf(ModBlocks.LAUNCH_PAD)) {
                            if (allPads) {
                                // Log first mismatch for this candidate center
                                Circuitmod.LOGGER.info("[LaunchPad] Mismatch at {}: found {} (id={})",
                                        p,
                                        state.getBlock().getClass().getSimpleName(),
                                        Registries.BLOCK.getId(state.getBlock()));
                            }
                            allPads = false;
                            break;
                        }
                    }
                }
                if (allPads) {
                    Circuitmod.LOGGER.info("[LaunchPad] Found 3x3 at center {}", center);
                    return true;
                }
            }
        }
        return false;
    }
}

