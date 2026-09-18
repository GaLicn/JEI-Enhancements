package com.gali.jei_enhancements.bookmark;

import mezz.jei.common.util.ImmutableRect2i;

/** 提供书签页管理控件。 */
public interface IPageManagementAccessor {
    ImmutableRect2i jeiEnhancements$getAddPageArea();

    ImmutableRect2i jeiEnhancements$getRemovePageArea();

    boolean jeiEnhancements$isPageManagementVisible();
}
