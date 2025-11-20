package starduster.circuitmod.satellite;

import com.mojang.datafixers.util.Pair;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.structure.Structure;
import starduster.circuitmod.Circuitmod;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Reconnaissance satellite capable of scanning terrain and structures.
 */
public class ScanSatellite extends Satellite {
    private static final int BASE_COOLDOWN_TICKS = 20 * 30; // 30 seconds
    private static final int COOLDOWN_MULTIPLIER = 5;
    private static final int MAX_COOLDOWN_TICKS = BASE_COOLDOWN_TICKS * COOLDOWN_MULTIPLIER;
    private static final long DEFAULT_LIFETIME_MILLIS = TimeUnit.DAYS.toMillis(3);
    private static final Map<String, Integer> TASK_COOLDOWNS = Map.of(
        "scan", MAX_COOLDOWN_TICKS,
        "locate", MAX_COOLDOWN_TICKS
    );

    public ScanSatellite(String accessCode) {
        this(UUID.randomUUID(), accessCode, MAX_COOLDOWN_TICKS, 0, true, System.currentTimeMillis(), DEFAULT_LIFETIME_MILLIS);
    }

    private ScanSatellite(UUID id, String accessCode, int maxCooldown, int rechargeTicks, boolean active,
                          long createdAtMillis, long lifetimeMillis) {
        super(id, accessCode, maxCooldown, rechargeTicks, active, createdAtMillis, lifetimeMillis);
    }

    public static ScanSatellite fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        UUID id = readUuid(nbt, "Id");
        String code = readString(nbt, "AccessCode", "UNKNOWN");
        int recharge = readInt(nbt, "RechargeTicks", 0);
        int maxRecharge = readInt(nbt, "MaxRecharge", MAX_COOLDOWN_TICKS);
        boolean active = readBoolean(nbt, "Active", true);
        long createdAt = readLong(nbt, "CreatedAtMillis", System.currentTimeMillis());
        long lifetime = readLong(nbt, "LifetimeMillis", DEFAULT_LIFETIME_MILLIS);
        return new ScanSatellite(id, code, maxRecharge, recharge, active, createdAt, lifetime);
    }

    @Override
    public String getTypeId() {
        return "scan";
    }

    @Override
    public SatelliteResult executeSatelliteCommand(String command, String[] args, ServerWorld world, BlockPos terminalPos) {
        return switch (command.toLowerCase()) {
            case "scan" -> executeScan(args, world, terminalPos);
            case "locate" -> executeLocate(args, world, terminalPos);
            default -> SatelliteResult.failure("Scan satellite does not recognize command '" + command + "'");
        };
    }

    private SatelliteResult executeScan(String[] args, ServerWorld world, BlockPos terminalPos) {
        if (!isReady()) {
            int seconds = Math.max(1, getRechargeTicks() / 20);
            return SatelliteResult.failure("Satellite charging. Ready in " + seconds + "s.");
        }

        if (args.length < 2) {
            return SatelliteResult.failure("Usage: scan <x> <z> [size]\nGenerates a visual map of the area (default size: 64x64)");
        }

        int centerX, centerZ, size;
        try {
            centerX = Integer.parseInt(args[0]);
            centerZ = Integer.parseInt(args[1]);
            size = args.length >= 3 ? Integer.parseInt(args[2]) : 64;
            size = Math.min(Math.max(size, 32), 128); // Clamp between 32 and 128
            // Make size even for centering
            if (size % 2 != 0) size++;
        } catch (NumberFormatException ex) {
            return SatelliteResult.failure("Invalid coordinates or size.");
        }

        // Generate map data that can be rendered
        byte[] mapColors = generateMapData(world, centerX, centerZ, size);
        
        // Store map data in the result for rendering
        String scanResult = "MAP_DATA:" + size + ":" + java.util.Base64.getEncoder().encodeToString(mapColors);
        
        startRecharge("scan", TASK_COOLDOWNS);
        return SatelliteResult.success(scanResult);
    }

    private SatelliteResult executeLocate(String[] args, ServerWorld world, BlockPos terminalPos) {
        if (!isReady()) {
            int seconds = Math.max(1, getRechargeTicks() / 20);
            return SatelliteResult.failure("Satellite charging. Ready in " + seconds + "s.");
        }

        if (args.length < 2) {
            return SatelliteResult.failure(
                "Usage: locate <type> <name>\n" +
                "Types: structure, biome\n" +
                "Examples:\n" +
                "  locate structure village\n" +
                "  locate structure stronghold\n" +
                "  locate biome desert\n" +
                "  locate biome jungle"
            );
        }

        String type = args[0].toLowerCase();
        String name = args[1].toLowerCase();
        
        String locateResult = switch (type) {
            case "structure" -> locateStructure(world, terminalPos, name);
            case "biome" -> locateBiome(world, terminalPos, name);
            default -> "Unknown locate type: " + type + "\nUse 'structure' or 'biome'";
        };
        
        startRecharge("locate", TASK_COOLDOWNS);
        return SatelliteResult.success(locateResult);
    }

    /**
     * Generate map color data similar to Minecraft maps
     * Returns a byte array where each byte represents a color in the Minecraft map color palette
     */
    private byte[] generateMapData(ServerWorld world, int centerX, int centerZ, int size) {
        byte[] mapColors = new byte[size * size];
        int halfSize = size / 2;
        int startX = centerX - halfSize;
        int startZ = centerZ - halfSize;

        Circuitmod.LOGGER.info("[SCAN-SATELLITE] Generating map data at {}, {} ({}x{})", centerX, centerZ, size, size);

        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int worldX = startX + x;
                int worldZ = startZ + z;
                
                try {
                    WorldChunk chunk = world.getChunk(ChunkSectionPos.getSectionCoord(worldX), ChunkSectionPos.getSectionCoord(worldZ));
                    
                    if (!chunk.isEmpty()) {
                        int surfaceY = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, worldX, worldZ);
                        BlockPos surfacePos = new BlockPos(worldX, Math.max(surfaceY - 1, world.getBottomY()), worldZ);
                        BlockState surfaceState = chunk.getBlockState(surfacePos);
                        
                        // Get the Minecraft map color for this block
                        MapColor mapColor = surfaceState.getMapColor(world, surfacePos);
                        
                        // Convert to color byte (use NORMAL brightness)
                        byte colorByte = mapColor.getRenderColorByte(MapColor.Brightness.NORMAL);
                        mapColors[z * size + x] = colorByte;
                    } else {
                        // Unloaded chunk - black
                        mapColors[z * size + x] = 0;
                    }
                } catch (Exception e) {
                    // Error getting block - mark as black
                    mapColors[z * size + x] = 0;
                }
            }
        }

        return mapColors;
    }

    /**
     * Convert a map color byte to an RGB color for rendering
     * This mirrors Minecraft's map color system
     */
    public static int getColorFromByte(byte colorByte) {
        int colorIndex = colorByte & 0xFF;
        if (colorIndex / 4 == 0) {
            return 0; // Transparent/black
        }
        
        // Get the base color and brightness
        MapColor mapColor = MapColor.get(colorIndex / 4);
        int brightnessIndex = colorIndex & 3;
        MapColor.Brightness brightness = switch(brightnessIndex) {
            case 0 -> MapColor.Brightness.LOW;
            case 1 -> MapColor.Brightness.NORMAL;
            case 2 -> MapColor.Brightness.HIGH;
            case 3 -> MapColor.Brightness.LOWEST;
            default -> MapColor.Brightness.NORMAL;
        };
        
        // Get the RGB color from the map color
        return mapColor.getRenderColor(brightness);
    }

    /**
     * Locate a structure (village, temple, stronghold, etc.)
     */
    private String locateStructure(ServerWorld world, BlockPos startPos, String structureName) {
        try {
            Registry<Structure> registry = world.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
            
            // Try to find the structure by name
            Identifier structureId = getStructureIdentifier(structureName);
            if (structureId == null) {
                return "Unknown structure: " + structureName + "\n" +
                       "Common structures: village, stronghold, mansion,\n" +
                       "temple, monument, fortress, bastion, endcity";
            }
            
            RegistryKey<Structure> structureKey = RegistryKey.of(RegistryKeys.STRUCTURE, structureId);
            Optional<RegistryEntry.Reference<Structure>> structureEntry = registry.getOptional(structureKey);
            if (structureEntry.isEmpty()) {
                return "Structure not found in registry: " + structureName;
            }
            
            // Create a list with just this structure
            RegistryEntryList<Structure> structureList = RegistryEntryList.of(structureEntry.get());
            
            // Locate the structure (search radius of 100 chunks)
            Pair<BlockPos, RegistryEntry<Structure>> result = world.getChunkManager()
                .getChunkGenerator()
                .locateStructure(world, structureList, startPos, 100, false);
            
            if (result == null) {
                return "No " + structureName + " found within search range.";
            }
            
            BlockPos foundPos = result.getFirst();
            int distance = (int) Math.sqrt(startPos.getSquaredDistance(foundPos));
            
            return "=== STRUCTURE LOCATED ===\n" +
                   "Type: " + structureName + "\n" +
                   "Coordinates: " + foundPos.getX() + ", " + foundPos.getY() + ", " + foundPos.getZ() + "\n" +
                   "Distance: " + distance + " blocks\n" +
                   "Direction: " + getDirection(startPos, foundPos);
        } catch (Exception e) {
            Circuitmod.LOGGER.error("[SCAN-SATELLITE] Error locating structure: " + structureName, e);
            return "Error locating structure: " + e.getMessage();
        }
    }

    /**
     * Locate a biome
     */
    private String locateBiome(ServerWorld world, BlockPos startPos, String biomeName) {
        try {
            Registry<Biome> registry = world.getRegistryManager().getOrThrow(RegistryKeys.BIOME);
            
            // Try to find the biome by name
            Identifier biomeId = getBiomeIdentifier(biomeName);
            if (biomeId == null) {
                return "Unknown biome: " + biomeName + "\n" +
                       "Common biomes: desert, jungle, plains, forest,\n" +
                       "ocean, taiga, savanna, badlands, ice_spikes";
            }
            
            RegistryKey<Biome> biomeKey = RegistryKey.of(RegistryKeys.BIOME, biomeId);
            Optional<RegistryEntry.Reference<Biome>> biomeEntry = registry.getOptional(biomeKey);
            if (biomeEntry.isEmpty()) {
                return "Biome not found in registry: " + biomeName;
            }
            
            // Create predicate that matches this specific biome
            RegistryEntryPredicateWrapper predicate = new RegistryEntryPredicateWrapper(biomeEntry.get());
            
            // Locate the biome (6400 block radius, check every 32 horizontal, 64 vertical)
            Pair<BlockPos, RegistryEntry<Biome>> result = world.locateBiome(
                predicate::test,
                startPos,
                6400,
                32,
                64
            );
            
            if (result == null) {
                return "No " + biomeName + " found within search range.";
            }
            
            BlockPos foundPos = result.getFirst();
            int distance = (int) Math.sqrt(startPos.getSquaredDistance(foundPos));
            
            return "=== BIOME LOCATED ===\n" +
                   "Type: " + biomeName + "\n" +
                   "Coordinates: " + foundPos.getX() + ", ~, " + foundPos.getZ() + "\n" +
                   "Distance: " + distance + " blocks\n" +
                   "Direction: " + getDirection(startPos, foundPos);
        } catch (Exception e) {
            Circuitmod.LOGGER.error("[SCAN-SATELLITE] Error locating biome: " + biomeName, e);
            return "Error locating biome: " + e.getMessage();
        }
    }

    /**
     * Simple wrapper for biome predicate
     */
    private static class RegistryEntryPredicateWrapper {
        private final RegistryEntry<Biome> target;
        
        public RegistryEntryPredicateWrapper(RegistryEntry<Biome> target) {
            this.target = target;
        }
        
        public boolean test(RegistryEntry<Biome> entry) {
            return entry.equals(target);
        }
    }

    /**
     * Get cardinal direction from start to end
     */
    private String getDirection(BlockPos start, BlockPos end) {
        int dx = end.getX() - start.getX();
        int dz = end.getZ() - start.getZ();
        
        double angle = Math.atan2(dz, dx) * 180 / Math.PI;
        
        if (angle < -157.5 || angle >= 157.5) return "West";
        if (angle >= -157.5 && angle < -112.5) return "Northwest";
        if (angle >= -112.5 && angle < -67.5) return "North";
        if (angle >= -67.5 && angle < -22.5) return "Northeast";
        if (angle >= -22.5 && angle < 22.5) return "East";
        if (angle >= 22.5 && angle < 67.5) return "Southeast";
        if (angle >= 67.5 && angle < 112.5) return "South";
        return "Southwest";
    }

    /**
     * Map common structure names to Minecraft identifiers
     */
    private Identifier getStructureIdentifier(String name) {
        return switch (name.toLowerCase()) {
            case "village", "villages" -> Identifier.ofVanilla("village_plains");
            case "desert_village" -> Identifier.ofVanilla("village_desert");
            case "savanna_village" -> Identifier.ofVanilla("village_savanna");
            case "taiga_village" -> Identifier.ofVanilla("village_taiga");
            case "snowy_village" -> Identifier.ofVanilla("village_snowy");
            case "stronghold", "strongholds" -> Identifier.ofVanilla("stronghold");
            case "mansion", "woodland_mansion" -> Identifier.ofVanilla("mansion");
            case "temple", "jungle_temple", "jungle_pyramid" -> Identifier.ofVanilla("jungle_pyramid");
            case "desert_temple", "desert_pyramid" -> Identifier.ofVanilla("desert_pyramid");
            case "igloo" -> Identifier.ofVanilla("igloo");
            case "swamp_hut", "witch_hut" -> Identifier.ofVanilla("swamp_hut");
            case "monument", "ocean_monument" -> Identifier.ofVanilla("monument");
            case "fortress", "nether_fortress" -> Identifier.ofVanilla("fortress");
            case "bastion", "bastion_remnant" -> Identifier.ofVanilla("bastion_remnant");
            case "endcity", "end_city" -> Identifier.ofVanilla("end_city");
            case "mineshaft" -> Identifier.ofVanilla("mineshaft");
            case "shipwreck" -> Identifier.ofVanilla("shipwreck");
            case "pillager_outpost", "outpost" -> Identifier.ofVanilla("pillager_outpost");
            case "ruined_portal", "portal" -> Identifier.ofVanilla("ruined_portal");
            case "ancient_city" -> Identifier.ofVanilla("ancient_city");
            case "trail_ruins" -> Identifier.ofVanilla("trail_ruins");
            default -> null;
        };
    }

    /**
     * Map common biome names to Minecraft identifiers
     */
    private Identifier getBiomeIdentifier(String name) {
        return switch (name.toLowerCase()) {
            case "desert" -> Identifier.ofVanilla("desert");
            case "jungle" -> Identifier.ofVanilla("jungle");
            case "plains" -> Identifier.ofVanilla("plains");
            case "forest" -> Identifier.ofVanilla("forest");
            case "birch_forest", "birch" -> Identifier.ofVanilla("birch_forest");
            case "dark_forest" -> Identifier.ofVanilla("dark_forest");
            case "ocean" -> Identifier.ofVanilla("ocean");
            case "deep_ocean" -> Identifier.ofVanilla("deep_ocean");
            case "taiga" -> Identifier.ofVanilla("taiga");
            case "savanna" -> Identifier.ofVanilla("savanna");
            case "badlands", "mesa" -> Identifier.ofVanilla("badlands");
            case "ice_spikes" -> Identifier.ofVanilla("ice_spikes");
            case "mushroom", "mushroom_fields" -> Identifier.ofVanilla("mushroom_fields");
            case "swamp" -> Identifier.ofVanilla("swamp");
            case "mangrove_swamp", "mangrove" -> Identifier.ofVanilla("mangrove_swamp");
            case "beach" -> Identifier.ofVanilla("beach");
            case "mountains", "mountain" -> Identifier.ofVanilla("jagged_peaks");
            case "snowy_plains", "snowy" -> Identifier.ofVanilla("snowy_plains");
            case "bamboo_jungle", "bamboo" -> Identifier.ofVanilla("bamboo_jungle");
            case "cherry_grove", "cherry" -> Identifier.ofVanilla("cherry_grove");
            case "flower_forest", "flowers" -> Identifier.ofVanilla("flower_forest");
            default -> null;
        };
    }


    @Override
    protected void writeCustomData(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        // No custom data yet
    }

    @Override
    public String describe() {
        return "SCAN Satellite\n" +
            "  Type   : Reconnaissance\n" +
            "  Range  : 64 blocks\n" +
            "  " + getStatusLine();
    }
}

