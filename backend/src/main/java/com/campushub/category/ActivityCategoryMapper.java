package com.campushub.category;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ActivityCategoryMapper {
    @Select("SELECT id, code, name, sort_order FROM activity_category ORDER BY sort_order, id")
    List<ActivityCategory> findAll();
}
