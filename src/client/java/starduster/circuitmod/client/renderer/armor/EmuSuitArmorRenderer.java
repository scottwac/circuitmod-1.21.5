package starduster.circuitmod.client.renderer.armor;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.item.EmuSuitArmorItem;
import starduster.circuitmod.item.ModItems;

import java.util.Set;

/**
 * {@link GeoRenderer} for the {@link EmuSuitArmorItem} armor item
 */
public final class EmuSuitArmorRenderer<R extends BipedEntityRenderState & GeoRenderState> extends GeoArmorRenderer<EmuSuitArmorItem, R> {
    public EmuSuitArmorRenderer() {
        super(new DefaultedItemGeoModel<>(Identifier.of(Circuitmod.MOD_ID, "armor/emu_suit")));

        // Add glowing layer for the glowmask textures
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    // Capture the worn stack data for later
    @Override
    public void addRenderData(EmuSuitArmorItem animatable, RenderData relatedObject, R renderState) {
        Set<Item> wornArmor = new ObjectOpenHashSet<>(4);

        boolean fullSetEffect = false;

        if (!(relatedObject.entity() instanceof ArmorStandEntity)) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR)
                    wornArmor.add(relatedObject.entity().getEquippedStack(slot).getItem());
            }

            fullSetEffect = wornArmor.containsAll(ObjectArrayList.of(
                    ModItems.EMU_SUIT_BOOTS,
                    ModItems.EMU_SUIT_LEGGINGS,
                    ModItems.EMU_SUIT_CHESTPLATE,
                    ModItems.EMU_SUIT_HELMET));
        }

        renderState.addGeckolibData(EmuSuitArmorItem.HAS_FULL_SET_EFFECT, fullSetEffect);
    }
}