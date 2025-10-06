package starduster.circuitmod.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Optional;

/**
 * Custom criterion for detecting when a player places a torch in the Luna dimension.
 */
public class TorchPlacementCriterion extends AbstractCriterion<TorchPlacementCriterion.Conditions> {

    @Override
    public Codec<Conditions> getConditionsCodec() {
        return Conditions.CODEC;
    }

    /**
     * Triggers the criterion for the given player.
     */
    public void trigger(ServerPlayerEntity player) {
        this.trigger(player, conditions -> true);
    }

    public record Conditions(Optional<LootContextPredicate> player) implements AbstractCriterion.Conditions {
        public static final Codec<Conditions> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                EntityPredicate.LOOT_CONTEXT_PREDICATE_CODEC.lenientOptionalFieldOf("player")
                    .forGetter(Conditions::player)
            ).apply(instance, Conditions::new)
        );

        public static Conditions create() {
            return new Conditions(Optional.empty());
        }
    }
}
