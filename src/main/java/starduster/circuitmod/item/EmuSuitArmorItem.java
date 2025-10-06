package starduster.circuitmod.item;

import net.minecraft.item.Item;
import net.minecraft.item.equipment.ArmorMaterial;
import net.minecraft.item.equipment.EquipmentType;
import org.apache.commons.lang3.mutable.MutableObject;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * GeoAnimatable armor item implementation for the Emu Suit.
 * Uses MutableObject pattern for split-source compatibility.
 */
public final class EmuSuitArmorItem extends Item implements GeoItem {
    // Create the object to store the RenderProvider (set from client code)
    public final MutableObject<GeoRenderProvider> renderProviderHolder = new MutableObject<>();
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public EmuSuitArmorItem(ArmorMaterial armorMaterial, EquipmentType type, Item.Settings properties) {
        super(properties.armor(armorMaterial, type));
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        // Return the cached RenderProvider (will be set from client initialization)
        consumer.accept(this.renderProviderHolder.getValue());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // No animations needed for this armor
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}