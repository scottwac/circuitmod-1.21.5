package starduster.circuitmod.client.renderer.armor;

import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.item.EmuSuitArmorItem;

/**
 * Renderer for the {@link EmuSuitArmorItem} armor
 */
public final class EmuSuitArmorRenderer<R extends BipedEntityRenderState & GeoRenderState> extends GeoArmorRenderer<EmuSuitArmorItem, R> {
    public EmuSuitArmorRenderer() {
        super(new DefaultedItemGeoModel<>(Identifier.of(Circuitmod.MOD_ID, "armor/emu_suit")));

        // Add glowing layer for the glowmask textures
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}