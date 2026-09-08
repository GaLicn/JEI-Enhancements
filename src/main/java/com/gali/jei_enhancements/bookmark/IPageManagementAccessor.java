package com.gali.jei_enhancements.bookmark;

import mezz.jei.common.util.ImmutableRect2i;

/** 暴露书签页管理按钮的区域，避免业务代码反射访问 JEI 私有状态。 */
public interface IPageManagementAccessor {
    ImmutableRect2i jeiEnhancements$getAddPageArea();

    ImmutableRect2i jeiEnhancements$getRemovePageArea();
}
