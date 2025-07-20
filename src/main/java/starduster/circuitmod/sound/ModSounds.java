package starduster.circuitmod.sound;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;

public class ModSounds {

    public static final SoundEvent MINER_MACHINE_RUN = registerSoundEvent("miner_machine_run");
    public static final SoundEvent LASER_BEAM = registerSoundEvent("laser_beam");
    public static final SoundEvent NUKE_NEAR = registerSoundEvent("nuke_near");
    public static final SoundEvent NUKE_MID = registerSoundEvent("nuke_mid");
    public static final SoundEvent NUKE_FAR = registerSoundEvent("nuke_far");

    private static SoundEvent registerSoundEvent(String name) {
        Identifier id = Identifier.of(Circuitmod.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }
    public static void initialize() { Circuitmod.LOGGER.info("Registering mod sounds"); }
}
