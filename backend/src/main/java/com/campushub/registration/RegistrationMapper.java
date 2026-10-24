package com.campushub.registration;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface RegistrationMapper {
    String FIELDS = "id,activity_id,user_id,status,version,created_at,updated_at";

    @Select("SELECT " + FIELDS + " FROM activity_registration WHERE activity_id=#{activityId} AND user_id=#{userId}")
    RegistrationView find(@Param("activityId") long activityId, @Param("userId") long userId);

    @Select("SELECT " + FIELDS + " FROM activity_registration WHERE activity_id=#{activityId} AND user_id=#{userId} FOR UPDATE")
    RegistrationView lock(@Param("activityId") long activityId, @Param("userId") long userId);

    @Insert("INSERT INTO activity_registration(activity_id,user_id,status,version) VALUES(#{activityId},#{userId},'CONFIRMED',1)")
    void insert(@Param("activityId") long activityId, @Param("userId") long userId);

    @Update("UPDATE activity_registration SET status=#{status},version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE id=#{id}")
    int change(@Param("id") long id, @Param("status") String status);

    @Update("""
            UPDATE activity SET registered_count=registered_count+1
            WHERE id=#{id} AND status='PUBLISHED' AND registered_count<capacity
            """)
    int occupy(long id);

    @Update("UPDATE activity SET registered_count=registered_count-1 WHERE id=#{id} AND registered_count>0")
    int release(long id);

    String FILTER = """
            <where>
              <if test="userId != null">AND user_id=#{userId}</if>
              <if test="activityId != null">AND activity_id=#{activityId}</if>
            </where>
            """;
    @Select("<script>SELECT " + FIELDS + " FROM activity_registration " + FILTER
            + " ORDER BY id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<RegistrationView> list(@Param("userId") Long userId, @Param("activityId") Long activityId,
            @Param("size") int size, @Param("offset") long offset);

    @Select("<script>SELECT COUNT(*) FROM activity_registration " + FILTER + "</script>")
    long count(@Param("userId") Long userId, @Param("activityId") Long activityId);
}
