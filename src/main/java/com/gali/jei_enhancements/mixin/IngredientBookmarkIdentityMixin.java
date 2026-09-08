package com.gali.jei_enhancements.mixin;

import com.gali.jei_enhancements.bookmark.IdentityDistinctBookmark;
import mezz.jei.gui.bookmarks.IngredientBookmark;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps only mod-created duplicate bookmarks distinct inside JEI's HashSet. */
@Mixin(value = IngredientBookmark.class, remap = false)
public abstract class IngredientBookmarkIdentityMixin implements IdentityDistinctBookmark {
    @Unique
    private boolean jeiEnhancements$identityDistinct;

    @Override
    public void jeiEnhancements$setIdentityDistinct(boolean identityDistinct) {
        this.jeiEnhancements$identityDistinct = identityDistinct;
    }

    @Inject(method = "hashCode", at = @At("HEAD"), cancellable = true)
    private void jeiEnhancements$useIdentityHashCode(CallbackInfoReturnable<Integer> cir) {
        if (jeiEnhancements$identityDistinct) {
            cir.setReturnValue(System.identityHashCode(this));
        }
    }

    @Inject(method = "equals", at = @At("HEAD"), cancellable = true)
    private void jeiEnhancements$useIdentityEquality(Object other, CallbackInfoReturnable<Boolean> cir) {
        if (jeiEnhancements$identityDistinct) {
            cir.setReturnValue(this == other);
        }
    }
}
