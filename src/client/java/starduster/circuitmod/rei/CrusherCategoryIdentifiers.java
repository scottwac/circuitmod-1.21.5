// src/main/java/starduster/circuitmod/rei/CrusherCategoryIdentifiers.java
package starduster.circuitmod.rei;

import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import net.minecraft.util.Identifier;

public class CrusherCategoryIdentifiers {
    public static final CategoryIdentifier<CrusherREIDisplay> CRUSHER =
        // must use the new Identifier.of(...) factory instead of the constructor :contentReference[oaicite:0]{index=0}
        CategoryIdentifier.of(Identifier.of("circuitmod", "crusher"));
}
