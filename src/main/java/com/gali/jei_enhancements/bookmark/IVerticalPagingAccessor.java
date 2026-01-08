package com.gali.jei_enhancements.bookmark;

import java.util.List;

/**
 * 用于访问IngredientGridWithNavigationMixin添加的方法
 */
public interface IVerticalPagingAccessor {
    
    List<int[]> jei_enhancements$getGroupRanges();
    
    int jei_enhancements$getRowsPerPage();
    
    int jei_enhancements$getPageCount();
    
    int jei_enhancements$getPageNumber();
    
    boolean jei_enhancements$nextPage();
    
    boolean jei_enhancements$previousPage();
}
