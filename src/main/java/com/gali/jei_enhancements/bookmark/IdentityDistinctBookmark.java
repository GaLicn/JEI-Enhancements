package com.gali.jei_enhancements.bookmark;

/** Marks mod-created bookmarks that must remain distinct for identical ingredients. */
public interface IdentityDistinctBookmark {
    void jeiEnhancements$setIdentityDistinct(boolean identityDistinct);
}
