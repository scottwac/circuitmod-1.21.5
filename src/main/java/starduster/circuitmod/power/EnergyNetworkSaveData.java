package starduster.circuitmod.power;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple data container for storing energy networks.
 * For now, this is a basic in-memory storage that will be enhanced with
 * proper persistence later when the correct API is determined.
 */
public class EnergyNetworkSaveData {
    private static final Map<ServerWorld, EnergyNetworkSaveData> worldData = new HashMap<>();
    
    private final Map<String, EnergyNetwork> networks = new HashMap<>();
    
    public static EnergyNetworkSaveData get(ServerWorld world) {
        return worldData.computeIfAbsent(world, w -> new EnergyNetworkSaveData());
    }
    
    public static void clear(ServerWorld world) {
        worldData.remove(world);
    }
    
    public static void clearAll() {
        worldData.clear();
    }
    
    /**
     * Loads network data from NBT (placeholder for future implementation)
     */
    public void readFromNbt(NbtCompound nbt) {
        if (!nbt.contains("networks")) return;
        
        NbtList networkList = nbt.getList("networks").orElse(new NbtList()); // Get networks list
        for (int i = 0; i < networkList.size(); i++) {
            if (networkList.get(i) instanceof NbtCompound) {
                NbtCompound networkNbt = (NbtCompound) networkList.get(i);
                String networkId = networkNbt.getString("id").orElse("");
                if (!networkId.isEmpty()) {
                    EnergyNetwork network = new EnergyNetwork(networkId);
                    network.readFromNbt(networkNbt);
                    networks.put(networkId, network);
                }
            }
        }
    }
    
    /**
     * Saves network data to NBT (placeholder for future implementation)
     */
    public NbtCompound writeToNbt(NbtCompound nbt) {
        NbtList networkList = new NbtList();
        
        for (Map.Entry<String, EnergyNetwork> entry : networks.entrySet()) {
            NbtCompound networkNbt = new NbtCompound();
            networkNbt.putString("id", entry.getKey());
            entry.getValue().writeToNbt(networkNbt);
            networkList.add(networkNbt);
        }
        
        nbt.put("networks", networkList);
        return nbt;
    }
    
    public Map<String, EnergyNetwork> getNetworks() {
        return networks;
    }
    
    public void saveNetwork(EnergyNetwork network) {
        networks.put(network.getNetworkId(), network);
        // TODO: Mark dirty when proper persistence is implemented
    }
    
    public void removeNetwork(String networkId) {
        networks.remove(networkId);
        // TODO: Mark dirty when proper persistence is implemented
    }
}