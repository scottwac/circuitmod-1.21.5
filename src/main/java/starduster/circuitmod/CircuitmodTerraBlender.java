package starduster.circuitmod;


import terrablender.api.TerraBlenderApi;
import terrablender.api.Regions;
import net.minecraft.util.Identifier;
import starduster.circuitmod.worldgen.ModBiomes;

public class CircuitmodTerraBlender implements TerraBlenderApi {
    
    @Override
    public void onTerraBlenderInitialized() {
        // Initialize biomes first
        ModBiomes.initialize();
        
        // Register the techno jungle region
        Regions.register(new CircuitmodRegion(Identifier.of(Circuitmod.MOD_ID, "techno_region"), 2));
    }
} 