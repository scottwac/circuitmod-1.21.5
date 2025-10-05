package starduster.circuitmod.item;

import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.equipment.EquipmentModel;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.equipment.ArmorMaterial;
import net.minecraft.item.equipment.EquipmentType;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animatable.processing.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.constant.DefaultAnimations;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;
import starduster.circuitmod.client.renderer.armor.EmuSuitArmorRenderer;

import java.util.function.Consumer;

/**
 * GeoAnimatable armor item implementation for the Emu Suit
 */
public final class EmuSuitArmorItem extends Item implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public static final DataTicket<Boolean> HAS_FULL_SET_EFFECT = DataTicket.create("circuitmod_has_full_set_effect", Boolean.class);

    public EmuSuitArmorItem(ArmorMaterial armorMaterial, EquipmentType type, Item.Settings properties) {
        super(properties.armor(armorMaterial, type));
    }

    // Create our armor model/renderer and return it
    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private EmuSuitArmorRenderer<?> renderer;

            @Nullable
            @Override
            public <S extends BipedEntityRenderState> GeoArmorRenderer<?, ?> getGeoArmorRenderer(@Nullable S renderState, ItemStack itemStack, EquipmentSlot equipmentSlot,
                                                                                              EquipmentModel.LayerType type, @Nullable BipedEntityModel<S> original) {
                if (this.renderer == null)
                    this.renderer = new EmuSuitArmorRenderer<>();
                // Defer creation of our renderer then cache it so that it doesn't get instantiated too early

                return this.renderer;
            }
        });
    }

    // Register animation controllers
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(20, animTest -> {
            // Play the animation if the full set is being worn, otherwise stop
            if (animTest.getData(HAS_FULL_SET_EFFECT))
                return animTest.setAndContinue(DefaultAnimations.IDLE);

            return PlayState.STOP;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}