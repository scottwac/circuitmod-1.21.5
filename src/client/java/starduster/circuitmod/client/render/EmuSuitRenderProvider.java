package starduster.circuitmod.client.render;

import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.equipment.EquipmentModel;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import starduster.circuitmod.client.renderer.EmuSuitRenderer;

/**
 * Client-side render provider for EMU Suit armor
 */
public class EmuSuitRenderProvider implements GeoRenderProvider {
    private EmuSuitRenderer<?> renderer;

    @Nullable
    @Override
    public <S extends BipedEntityRenderState> GeoArmorRenderer<?, ?> getGeoArmorRenderer(
            @Nullable S renderState,
            ItemStack itemStack,
            EquipmentSlot equipmentSlot,
            EquipmentModel.LayerType type,
            @Nullable BipedEntityModel<S> original) {
        if (this.renderer == null) {
            this.renderer = new EmuSuitRenderer<>();
        }
        return this.renderer;
    }
}
