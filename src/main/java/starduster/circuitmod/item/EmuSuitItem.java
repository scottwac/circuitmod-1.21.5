package starduster.circuitmod.item;

import net.minecraft.item.Item;
import net.minecraft.item.equipment.ArmorMaterial;
import net.minecraft.item.equipment.EquipmentType;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * EMU Space Suit armor item with GeckoLib animation support
 */
public final class EmuSuitItem extends Item implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    
    public static final DataTicket<Boolean> HAS_FULL_SET_EFFECT = DataTicket.create("circuitmod_has_full_set_effect", Boolean.class);
    
    public EmuSuitItem(ArmorMaterial armorMaterial, EquipmentType type, Item.Settings properties) {
        super(properties
                .maxDamage(type.getMaxDamage(armorMaterial.durability()))
                .attributeModifiers(armorMaterial.createAttributeModifiers(type))
                .enchantable(armorMaterial.enchantmentValue())
                .repairable(armorMaterial.repairIngredient())
                .component(net.minecraft.component.DataComponentTypes.EQUIPPABLE,
                    net.minecraft.component.type.EquippableComponent.builder(type.getEquipmentSlot())
                        .equipSound(armorMaterial.equipSound())
                        .build()));
    }

    private static String getSlotName(EquipmentType type) {
        return switch (type) {
            case HELMET -> "helmet";
            case CHESTPLATE -> "chestplate";
            case LEGGINGS -> "leggings";
            case BOOTS -> "boots";
            default -> "helmet";
        };
    }

    // Create our armor model/renderer and return it
    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        // Use reflection to load the client-side render provider
        try {
            Class<?> providerClass = Class.forName("starduster.circuitmod.client.render.EmuSuitRenderProvider");
            GeoRenderProvider provider = (GeoRenderProvider) providerClass.getDeclaredConstructor().newInstance();
            consumer.accept(provider);
        } catch (Exception e) {
            // Server side - no provider needed
        }
    }
    
    // Register animation controllers (none for now, but can be added later)
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // No animations for now, but you can add them here later
        // Example:
        // controllers.add(new AnimationController<>(20, animTest -> {
        //     if (animTest.getData(HAS_FULL_SET_EFFECT))
        //         return animTest.setAndContinue(DefaultAnimations.IDLE);
        //     return PlayState.STOP;
        // }));
    }
    
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
