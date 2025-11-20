package starduster.circuitmod.satellite;

import com.mojang.serialization.Codec;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;
import starduster.circuitmod.Circuitmod;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global registry tracking all satellites within the server.
 */
public class SatelliteRegistry extends PersistentState {
    private static final String DATA_ID = "circuitmod_satellites";

    @FunctionalInterface
    public interface SatelliteDeserializer {
        Satellite create(NbtCompound tag, RegistryWrapper.WrapperLookup lookup);
    }

    private static final Map<String, SatelliteDeserializer> DESERIALIZERS = new HashMap<>();

    private final Map<UUID, Satellite> satellites = new ConcurrentHashMap<>();
    private final Map<String, UUID> accessCodeIndex = new ConcurrentHashMap<>();
    private RegistryWrapper.WrapperLookup cachedRegistries;

    public static void registerType(String id, SatelliteDeserializer deserializer) {
        DESERIALIZERS.put(id.toLowerCase(Locale.ROOT), deserializer);
    }

    // Constructor for creating a new registry
    private SatelliteRegistry() {
    }

    // Serialize to NBT (used by Codec)
    private NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        NbtList list = new NbtList();
        for (Satellite satellite : satellites.values()) {
            NbtCompound satNbt = satellite.writeNbt(cachedRegistries);
            satNbt.putString("Type", satellite.getTypeId());
            list.add(satNbt);
        }
        nbt.put("Satellites", list);
        return nbt;
    }

    // Static method to deserialize from NBT
    private static SatelliteRegistry createFromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        SatelliteRegistry registry = new SatelliteRegistry();
        registry.cachedRegistries = registryLookup;
        
        NbtList list = nbt.getListOrEmpty("Satellites");
        for (int i = 0; i < list.size(); i++) {
            list.getCompound(i).ifPresent(compound -> {
                String type = compound.getString("Type").orElse("").toLowerCase(Locale.ROOT);
                SatelliteDeserializer deserializer = DESERIALIZERS.get(type);
                if (deserializer == null) {
                    return;
                }
                Satellite satellite = deserializer.create(compound, registryLookup);
                registry.satellites.put(satellite.getId(), satellite);
                registry.accessCodeIndex.put(satellite.getAccessCode(), satellite.getId());
            });
        }
        return registry;
    }

    // Codec for serialization/deserialization using NBT
    private static Codec<SatelliteRegistry> createCodec(PersistentState.Context context) {
        return NbtCompound.CODEC.xmap(
            nbt -> {
                ServerWorld world = context.world();
                RegistryWrapper.WrapperLookup lookup = world != null ? world.getRegistryManager() : null;
                if (lookup == null) {
                    return new SatelliteRegistry();
                }
                SatelliteRegistry registry = createFromNbt(nbt, lookup);
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

    public static final PersistentStateType<SatelliteRegistry> TYPE = new PersistentStateType<>(
        DATA_ID,
        context -> {
            SatelliteRegistry registry = new SatelliteRegistry();
            if (context.world() != null) {
                registry.cachedRegistries = context.world().getRegistryManager();
            }
            return registry;
        },
        SatelliteRegistry::createCodec,
        DataFixTypes.LEVEL
    );

    public static SatelliteRegistry get(ServerWorld world) {
        ServerWorld overworld = world.getServer().getWorld(World.OVERWORLD);
        SatelliteRegistry registry = overworld.getPersistentStateManager().getOrCreate(TYPE);
        // Cache the registries for writeNbt
        registry.cachedRegistries = world.getRegistryManager();
        // Mark dirty so changes are saved
        registry.markDirty();
        return registry;
    }

    public Optional<Satellite> findByAccessCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        UUID id = accessCodeIndex.get(code);
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(satellites.get(id));
    }

    public Optional<Satellite> getSatellite(UUID id) {
        return Optional.ofNullable(satellites.get(id));
    }

    public Collection<Satellite> getAllSatellites() {
        return Collections.unmodifiableCollection(satellites.values());
    }

    public Optional<Satellite> createSatellite(String type, String accessCode) {
        if (accessCode == null || accessCode.isEmpty()) {
            return Optional.empty();
        }
        if (accessCodeIndex.containsKey(accessCode)) {
            return Optional.empty();
        }
        Satellite satellite = switch (type.toLowerCase(Locale.ROOT)) {
            case "strike" -> new StrikeSatellite(accessCode);
            case "scan" -> new ScanSatellite(accessCode);
            case "mining" -> new MiningSatellite(accessCode);
            default -> null;
        };
        if (satellite == null) {
            return Optional.empty();
        }
        satellites.put(satellite.getId(), satellite);
        accessCodeIndex.put(accessCode, satellite.getId());
        markDirty();
        return Optional.of(satellite);
    }

    public boolean removeSatellite(UUID id) {
        Satellite removed = satellites.remove(id);
        if (removed != null) {
            accessCodeIndex.remove(removed.getAccessCode());
            markDirty();
            return true;
        }
        return false;
    }

    public void tick() {
        boolean dirty = false;
        long now = System.currentTimeMillis();
        Iterator<Satellite> iterator = satellites.values().iterator();
        while (iterator.hasNext()) {
            Satellite satellite = iterator.next();
            int before = satellite.getRechargeTicks();
            satellite.tick();
            if (before != satellite.getRechargeTicks()) {
                dirty = true;
            }
            if (satellite.hasExpired(now)) {
                iterator.remove();
                accessCodeIndex.remove(satellite.getAccessCode());
                Circuitmod.LOGGER.warn("[SATELLITE-REGISTRY] Satellite {} expired and was decommissioned.", satellite.formatLabel());
                dirty = true;
            }
        }
        if (dirty) {
            markDirty();
        }
    }
}

