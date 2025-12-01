package starduster.circuitmod.expedition;

import com.mojang.serialization.Codec;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Global registry tracking all expeditions within the server.
 * Uses PersistentState for automatic saving/loading.
 */
public class ExpeditionRegistry extends PersistentState {
    private static final String DATA_ID = "circuitmod_expeditions";
    public static final int MAX_CONCURRENT_EXPEDITIONS = 3;

    private final Map<String, Expedition> allExpeditions = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> playerExpeditions = new ConcurrentHashMap<>();
    private RegistryWrapper.WrapperLookup cachedRegistries;

    private ExpeditionRegistry() {
    }

    private NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        NbtList list = new NbtList();
        for (Expedition expedition : allExpeditions.values()) {
            list.add(expedition.writeNbt(cachedRegistries));
        }
        nbt.put("Expeditions", list);
        return nbt;
    }

    private static ExpeditionRegistry createFromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        ExpeditionRegistry registry = new ExpeditionRegistry();
        registry.cachedRegistries = registryLookup;

        NbtList list = nbt.getListOrEmpty("Expeditions");
        for (int i = 0; i < list.size(); i++) {
            list.getCompound(i).ifPresent(compound -> {
                Expedition expedition = Expedition.fromNbt(compound, registryLookup);
                registry.registerExpedition(expedition);
            });
        }
        return registry;
    }

    private static Codec<ExpeditionRegistry> createCodec(PersistentState.Context context) {
        return NbtCompound.CODEC.xmap(
            nbt -> {
                ServerWorld world = context.world();
                RegistryWrapper.WrapperLookup lookup = world != null ? world.getRegistryManager() : null;
                if (lookup == null) {
                    return new ExpeditionRegistry();
                }
                ExpeditionRegistry registry = createFromNbt(nbt, lookup);
                registry.cachedRegistries = lookup;
                return registry;
            },
            registry -> {
                if (registry.cachedRegistries != null) {
                    return registry.toNbt();
                }
                return new NbtCompound();
            }
        );
    }

    public static final PersistentStateType<ExpeditionRegistry> TYPE = new PersistentStateType<>(
        DATA_ID,
        context -> {
            ExpeditionRegistry registry = new ExpeditionRegistry();
            if (context.world() != null) {
                registry.cachedRegistries = context.world().getRegistryManager();
            }
            return registry;
        },
        ExpeditionRegistry::createCodec,
        DataFixTypes.LEVEL
    );

    public static ExpeditionRegistry get(ServerWorld world) {
        ServerWorld overworld = world.getServer().getWorld(World.OVERWORLD);
        ExpeditionRegistry registry = overworld.getPersistentStateManager().getOrCreate(TYPE);
        registry.cachedRegistries = world.getRegistryManager();
        registry.markDirty();
        return registry;
    }

    /**
     * Register an expedition in the registry
     */
    private void registerExpedition(Expedition expedition) {
        allExpeditions.put(expedition.getExpeditionId(), expedition);
        playerExpeditions.computeIfAbsent(expedition.getPlayerId(), k -> new ArrayList<>())
            .add(expedition.getExpeditionId());
    }

    /**
     * Get all expeditions for a player
     */
    public List<Expedition> getPlayerExpeditions(UUID playerId) {
        List<String> expIds = playerExpeditions.get(playerId);
        if (expIds == null) {
            return Collections.emptyList();
        }
        return expIds.stream()
            .map(allExpeditions::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * Get active expeditions for a player
     */
    public List<Expedition> getActiveExpeditions(UUID playerId) {
        return getPlayerExpeditions(playerId).stream()
            .filter(e -> e.getStatus().isActive())
            .collect(Collectors.toList());
    }

    /**
     * Get completed expeditions for a player
     */
    public List<Expedition> getCompletedExpeditions(UUID playerId) {
        return getPlayerExpeditions(playerId).stream()
            .filter(e -> e.getStatus().isCompleted())
            .collect(Collectors.toList());
    }

    /**
     * Get an expedition by ID
     */
    public Optional<Expedition> getExpedition(String expeditionId) {
        return Optional.ofNullable(allExpeditions.get(expeditionId));
    }

    /**
     * Check if player can launch a new expedition
     */
    public boolean canLaunchExpedition(UUID playerId) {
        return getActiveExpeditions(playerId).size() < MAX_CONCURRENT_EXPEDITIONS;
    }

    /**
     * Get available expedition slots for a player
     */
    public int getAvailableSlots(UUID playerId) {
        return MAX_CONCURRENT_EXPEDITIONS - getActiveExpeditions(playerId).size();
    }

    /**
     * Create and register a new expedition
     */
    public Expedition createExpedition(UUID playerId, ExpeditionDestination destination, int fuelSpent) {
        String expeditionId = Expedition.generateExpeditionId();
        Expedition expedition = new Expedition(
            expeditionId,
            playerId,
            destination,
            System.currentTimeMillis(),
            fuelSpent
        );
        registerExpedition(expedition);
        markDirty();
        return expedition;
    }

    /**
     * Remove an expedition (after loot is claimed)
     */
    public boolean removeExpedition(String expeditionId) {
        Expedition removed = allExpeditions.remove(expeditionId);
        if (removed != null) {
            List<String> playerExps = playerExpeditions.get(removed.getPlayerId());
            if (playerExps != null) {
                playerExps.remove(expeditionId);
            }
            markDirty();
            return true;
        }
        return false;
    }

    /**
     * Get all expeditions
     */
    public Collection<Expedition> getAllExpeditions() {
        return Collections.unmodifiableCollection(allExpeditions.values());
    }

    /**
     * Tick all expeditions - called from server tick handler
     */
    public void tick(ServerWorld world) {
        boolean dirty = false;
        long now = System.currentTimeMillis();

        for (Expedition expedition : allExpeditions.values()) {
            if (expedition.getStatus() == ExpeditionStatus.IN_TRANSIT_OUTBOUND) {
                // Check if event should trigger
                if (now >= expedition.getEventTriggerTimestamp()) {
                    ExpeditionManager.triggerExpeditionEvent(expedition, world);
                    dirty = true;
                }
            } else if (expedition.getStatus() == ExpeditionStatus.IN_TRANSIT_RETURN) {
                // Check if expedition should complete
                if (now >= expedition.getExpectedReturnTimestamp()) {
                    ExpeditionManager.completeExpedition(expedition, world);
                    dirty = true;
                }
            }
        }

        if (dirty) {
            markDirty();
        }
    }
}

