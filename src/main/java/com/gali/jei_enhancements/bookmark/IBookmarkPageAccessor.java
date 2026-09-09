package com.gali.jei_enhancements.bookmark;

/** 书签列表的逻辑分页操作。 */
public interface IBookmarkPageAccessor {
    void jeiEnhancements$clearPage(int pageId);

    /** 通知 JEI 数据源变化，使当前逻辑页立即重新布局。 */
    void jeiEnhancements$refreshPage();
}
