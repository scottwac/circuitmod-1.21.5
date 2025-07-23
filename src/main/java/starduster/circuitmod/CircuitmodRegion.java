package starduster.circuitmod;

import com.mojang.datafixers.util.Pair;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.biome.source.util.MultiNoiseUtil.NoiseHypercube;
import terrablender.api.Region;
import terrablender.api.RegionType;
import starduster.circuitmod.worldgen.ModBiomes;

import java.util.function.Consumer;

public class CircuitmodRegion extends Region {
    
    public CircuitmodRegion(Identifier name, int weight) {
        super(name, RegionType.OVERWORLD, weight);
    }
    
    @Override
    public void addBiomes(Registry<Biome> registry, Consumer<Pair<MultiNoiseUtil.NoiseHypercube, RegistryKey<Biome>>> mapper) {
        // Add the techno jungle biome to specific climate conditions
        // Define climate parameters similar to jungle but slightly different
        this.addBiome(mapper, 
            MultiNoiseUtil.ParameterRange.of(0.4F, 1.0F),      // temperature (hot)
            MultiNoiseUtil.ParameterRange.of(0.3F, 1.0F),      // humidity (humid)
            MultiNoiseUtil.ParameterRange.of(0.3F, 1.0F),      // continentalness (land only, avoids coasts and oceans)
            MultiNoiseUtil.ParameterRange.of(-0.5F, 0.5F),     // erosion (moderate)
            MultiNoiseUtil.ParameterRange.of(0.0F),            // depth (surface)
            MultiNoiseUtil.ParameterRange.of(-0.5F, 0.5F),     // weirdness (moderate)
            0.0F,                                               // offset
            ModBiomes.TECHNO_JUNGLE);                           // biome
    }
} 