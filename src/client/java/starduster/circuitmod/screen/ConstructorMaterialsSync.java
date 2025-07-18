package starduster.circuitmod.screen;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.math.BlockPos;

/**
 * Client-side materials sync handler for Constructor
 */
public class ConstructorMaterialsSync {
    // Static maps to hold synced materials per constructor position
    private static final Map<BlockPos, Map<String, Integer>> syncedRequiredMaterials = new HashMap<>();
    private static final Map<BlockPos, Map<String, Integer>> syncedAvailableMaterials = new HashMap<>();

    public static void updateMaterialsFromServer(BlockPos pos, Map<String, Integer> required, Map<String, Integer> available) {
        syncedRequiredMaterials.put(pos, required);
        syncedAvailableMaterials.put(pos, available);
    }

    public static Map<String, Integer> getSyncedRequiredMaterials(BlockPos pos) {
        return syncedRequiredMaterials.getOrDefault(pos, new HashMap<>());
    }

    public static Map<String, Integer> getSyncedAvailableMaterials(BlockPos pos) {
        return syncedAvailableMaterials.getOrDefault(pos, new HashMap<>());
    }
} 