package starduster.circuitmod;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import starduster.circuitmod.screen.ModScreenHandlers;
import starduster.circuitmod.screen.QuarryScreen;

public class CircuitmodClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Register screens
		HandledScreens.register(ModScreenHandlers.QUARRY_SCREEN_HANDLER, QuarryScreen::new);
	}
}