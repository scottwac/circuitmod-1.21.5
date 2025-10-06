package starduster.circuitmod.util;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Helper class to share player context between mixins for torch placement.
 * This is outside the mixin package to avoid Mixin's restrictions on non-mixin classes.
 */
public class TorchPlacementHelper {
    public static final ThreadLocal<ServerPlayerEntity> placingPlayer = new ThreadLocal<>();
}
