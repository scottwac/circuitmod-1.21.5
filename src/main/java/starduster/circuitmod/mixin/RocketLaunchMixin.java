package starduster.circuitmod.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.entity.RocketEntity;

import java.util.List;

/**
 * Mixin to handle rocket launch mechanics by overriding gravity and applying upward acceleration
 * Similar to how MoonGravityMixin works for players on Luna
 */
@Mixin(RocketEntity.class)
public class RocketLaunchMixin {
    
    private static final float MAX_LAUNCH_SPEED = 2.0f;
    private static final int LAUNCH_ACCELERATION_TIME = 600; // 3 seconds at 20 ticks/second
    private static final double LUNA_ARRIVAL_HEIGHT = 2000.0; // Height to teleport to Luna from Overworld
    private static final double EARTH_ARRIVAL_HEIGHT = 1000.0; // Height to teleport to Earth from Luna
    private static final double LUNA_SPAWN_HEIGHT = 500.0; // Height to spawn at on Luna
    private static final double EARTH_SPAWN_HEIGHT = 500.0; // Height to spawn at on Earth
    
    @Inject(method = "tick", at = @At("TAIL"))
    private void circuitmod$handleRocketLaunch(CallbackInfo ci) {
        RocketEntity rocket = (RocketEntity) (Object) this;
        
        // Only apply launch mechanics if rocket is launching
        if (!rocket.isLaunching()) {
            // Restore normal gravity when not launching
            if (rocket.hasNoGravity()) {
                rocket.setNoGravity(false);
            }
            return;
        }
        
        // Check if we've reached teleport height (depends on current dimension)
        if (!rocket.getWorld().isClient) {
            boolean isInLuna = rocket.getWorld().getRegistryKey().getValue().equals(Identifier.of("circuitmod", "luna"));
            boolean isInOverworld = rocket.getWorld().getRegistryKey().getValue().equals(Identifier.of("minecraft", "overworld"));
            
            // From Overworld to Luna at Y=2000
            if (isInOverworld && rocket.getY() >= LUNA_ARRIVAL_HEIGHT) {
                Circuitmod.LOGGER.info("Rocket reached Luna arrival height! Teleporting to Luna...");
                teleportToLuna(rocket);
                return;
            }
            
            // From Luna to Earth at Y=1000
            if (isInLuna && rocket.getY() >= EARTH_ARRIVAL_HEIGHT) {
                Circuitmod.LOGGER.info("Rocket reached Earth arrival height! Teleporting to Earth...");
                teleportToEarth(rocket);
                return;
            }
        }
        
        // Disable vanilla gravity during launch
        rocket.setNoGravity(true);
        
        // Get current velocity
        Vec3d velocity = rocket.getVelocity();
        
        // Calculate launch speed based on launch ticks (progressive acceleration)
        int launchTicks = rocket.getLaunchTicks();
        float progress = Math.min((float) launchTicks / LAUNCH_ACCELERATION_TIME, 1.0f);
        
        // Use quadratic easing for smooth acceleration
        float easedProgress = progress * progress;
        float launchSpeed = easedProgress * MAX_LAUNCH_SPEED;
        
        // Once we reach max speed, maintain it
        if (progress >= 1.0f) {
            launchSpeed = MAX_LAUNCH_SPEED;
        }
        
        // Apply upward velocity
        rocket.setVelocity(velocity.x, launchSpeed, velocity.z);
        rocket.velocityModified = true; // Mark velocity as modified so it syncs to client
        
        // Debug logging every 20 ticks (once per second)
        if (launchTicks % 20 == 0) {
            Circuitmod.LOGGER.info("Rocket launch - Y: {}, Speed: {}, Ticks: {}, Progress: {}, HasNoGravity: {}", 
                String.format("%.1f", rocket.getY()), launchSpeed, launchTicks, String.format("%.2f", progress), rocket.hasNoGravity());
        }
        
        // Increment launch ticks
        rocket.incrementLaunchTicks();
    }
    
    private void teleportToLuna(RocketEntity rocket) {
        World world = rocket.getWorld();
        
        if (world.isClient || !(world instanceof ServerWorld serverWorld)) {
            return;
        }
        
        // Get Luna dimension
        RegistryKey<World> lunaKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of("circuitmod", "luna"));
        ServerWorld lunaWorld = serverWorld.getServer().getWorld(lunaKey);
        
        if (lunaWorld == null) {
            Circuitmod.LOGGER.error("Luna dimension not found!");
            return;
        }
        
        // Get all passengers before teleporting
        List<Entity> passengers = rocket.getPassengerList();
        
        // Calculate spawn position on Luna (same X/Z, high Y)
        double x = rocket.getX();
        double y = LUNA_SPAWN_HEIGHT;
        double z = rocket.getZ();
        
        Circuitmod.LOGGER.info("Teleporting rocket and {} passengers to Luna at ({}, {}, {})", 
            passengers.size(), x, y, z);
        
        // Detach all passengers first
        for (Entity passenger : passengers) {
            passenger.detach();
        }
        
        // Teleport the rocket first
        TeleportTarget rocketTarget = new TeleportTarget(
            lunaWorld,
            new Vec3d(x, y, z),
            Vec3d.ZERO, // No velocity
            rocket.getYaw(),
            rocket.getPitch(),
            TeleportTarget.NO_OP
        );
        
        Entity teleportedRocket = rocket.teleportTo(rocketTarget);
        
        if (teleportedRocket instanceof RocketEntity lunaRocket) {
            // Stop launching
            lunaRocket.setLaunching(false);
            
            // Re-enable gravity for landing
            lunaRocket.setNoGravity(false);
            
            Circuitmod.LOGGER.info("Rocket successfully teleported to Luna!");
            
            // Teleport and remount passengers
            for (Entity passenger : passengers) {
                TeleportTarget target = new TeleportTarget(
                    lunaWorld,
                    new Vec3d(x, y + 1, z), // Spawn slightly higher than rocket
                    Vec3d.ZERO, // No velocity
                    passenger.getYaw(),
                    passenger.getPitch(),
                    TeleportTarget.NO_OP
                );
                
                Entity teleportedPassenger = passenger.teleportTo(target);
                if (teleportedPassenger != null) {
                    Circuitmod.LOGGER.info("Teleported passenger {} to Luna", passenger.getName().getString());
                    
                    // Remount passenger on the rocket
                    teleportedPassenger.startRiding(lunaRocket, true);
                }
            }
        }
    }
    
    private void teleportToEarth(RocketEntity rocket) {
        World world = rocket.getWorld();
        
        if (world.isClient || !(world instanceof ServerWorld serverWorld)) {
            return;
        }
        
        // Get Overworld dimension
        RegistryKey<World> overworldKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of("minecraft", "overworld"));
        ServerWorld overworldWorld = serverWorld.getServer().getWorld(overworldKey);
        
        if (overworldWorld == null) {
            Circuitmod.LOGGER.error("Overworld dimension not found!");
            return;
        }
        
        // Get all passengers before teleporting
        List<Entity> passengers = rocket.getPassengerList();
        
        // Calculate spawn position on Earth (same X/Z, high Y)
        double x = rocket.getX();
        double y = EARTH_SPAWN_HEIGHT;
        double z = rocket.getZ();
        
        Circuitmod.LOGGER.info("Teleporting rocket and {} passengers to Earth at ({}, {}, {})", 
            passengers.size(), x, y, z);
        
        // Detach all passengers first
        for (Entity passenger : passengers) {
            passenger.detach();
        }
        
        // Teleport the rocket first
        TeleportTarget rocketTarget = new TeleportTarget(
            overworldWorld,
            new Vec3d(x, y, z),
            Vec3d.ZERO, // No velocity
            rocket.getYaw(),
            rocket.getPitch(),
            TeleportTarget.NO_OP
        );
        
        Entity teleportedRocket = rocket.teleportTo(rocketTarget);
        
        if (teleportedRocket instanceof RocketEntity earthRocket) {
            // Stop launching
            earthRocket.setLaunching(false);
            
            // Re-enable gravity for landing
            earthRocket.setNoGravity(false);
            
            // Mark that this rocket came from Luna (for despawn logic)
            earthRocket.setCameFromLuna(true);
            
            Circuitmod.LOGGER.info("Rocket successfully teleported to Earth!");
            
            // Teleport and remount passengers
            for (Entity passenger : passengers) {
                TeleportTarget target = new TeleportTarget(
                    overworldWorld,
                    new Vec3d(x, y + 1, z), // Spawn slightly higher than rocket
                    Vec3d.ZERO, // No velocity
                    passenger.getYaw(),
                    passenger.getPitch(),
                    TeleportTarget.NO_OP
                );
                
                Entity teleportedPassenger = passenger.teleportTo(target);
                if (teleportedPassenger != null) {
                    Circuitmod.LOGGER.info("Teleported passenger {} to Earth", passenger.getName().getString());
                    
                    // Remount passenger on the rocket
                    teleportedPassenger.startRiding(earthRocket, true);
                }
            }
        }
    }
}
