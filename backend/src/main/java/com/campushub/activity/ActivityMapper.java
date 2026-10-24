package com.campushub.activity;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ActivityMapper {
    String FIELDS = """
            a.id, a.title, a.summary, a.content, a.category_id, c.name AS category_name,
            a.location, a.start_time, a.end_time, a.registration_start, a.registration_end,
            a.capacity, a.creator_id, a.status, a.version, a.created_at, a.updated_at,
            a.registered_count, a.capacity-a.registered_count AS remaining_capacity
            """;
    String JOIN = " FROM activity a JOIN activity_category c ON c.id=a.category_id ";
    String FILTER = """
            <where>
              <if test="ownerId != null"> AND a.creator_id=#{ownerId} </if>
              <if test="status != null"> AND a.status=#{status} </if>
              <if test="categoryId != null"> AND a.category_id=#{categoryId} </if>
              <if test="keyword != null">
                AND (a.title LIKE #{keyword} ESCAPE '!' OR a.summary LIKE #{keyword} ESCAPE '!')
              </if>
            </where>
            """;

    @Select("SELECT " + FIELDS + JOIN + "WHERE a.id=#{id}")
    ActivityView find(long id);

    @Select("SELECT " + FIELDS + JOIN + "WHERE a.id=#{id} FOR UPDATE OF a")
    ActivityView findForUpdate(long id);

    @Select("SELECT " + FIELDS + JOIN + "WHERE a.id=#{id} AND a.status='PUBLISHED'")
    ActivityView findPublished(long id);

    // Explicit column order matches the immutable projection's constructor.
    @Select("""
            <script>SELECT a.id, a.title, a.summary, NULL AS content, a.category_id,
            c.name AS category_name, a.location, a.start_time, a.end_time,
            a.registration_start, a.registration_end, a.capacity, a.creator_id,
            a.status, a.version, a.created_at, a.updated_at,
            a.registered_count, a.capacity-a.registered_count AS remaining_capacity
            """ + JOIN + FILTER + " ORDER BY a.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<ActivityView> list(@Param("ownerId") Long ownerId, @Param("status") ActivityStatus status,
            @Param("categoryId") Long categoryId, @Param("keyword") String keyword,
            @Param("size") int size, @Param("offset") long offset);

    @Select("<script>SELECT COUNT(*)" + JOIN + FILTER + "</script>")
    long count(@Param("ownerId") Long ownerId, @Param("status") ActivityStatus status,
            @Param("categoryId") Long categoryId, @Param("keyword") String keyword);

    @Select("SELECT COUNT(*) FROM activity_category WHERE id=#{id}")
    int categoryExists(long id);

    @Insert("""
            INSERT INTO activity(title,summary,content,category_id,location,start_time,end_time,
                registration_start,registration_end,capacity,creator_id,status)
            VALUES(#{v.title},#{v.summary},#{v.content},#{v.categoryId},#{v.location},#{v.startTime},
                #{v.endTime},#{v.registrationStart},#{v.registrationEnd},#{v.capacity},#{creatorId},'DRAFT')
            """)
    void insert(@Param("v") ActivityInput input, @Param("creatorId") long creatorId);

    @Select("SELECT LAST_INSERT_ID()")
    long insertedId();

    @Update("""
            UPDATE activity SET title=#{v.title},summary=#{v.summary},content=#{v.content},
                category_id=#{v.categoryId},location=#{v.location},start_time=#{v.startTime},
                end_time=#{v.endTime},registration_start=#{v.registrationStart},
                registration_end=#{v.registrationEnd},capacity=#{v.capacity},
                version=version+1,updated_at=UTC_TIMESTAMP(6)
            WHERE id=#{id} AND version=#{v.version} AND status=#{status}
                AND status IN ('DRAFT','UNPUBLISHED') AND (creator_id=#{actorId} OR #{admin})
            """)
    int edit(@Param("id") long id, @Param("v") ActivityInput input, @Param("status") ActivityStatus status,
            @Param("actorId") long actorId, @Param("admin") boolean admin);

    @Update("""
            UPDATE activity SET status=#{target},version=version+1,updated_at=UTC_TIMESTAMP(6)
            WHERE id=#{id} AND version=#{version} AND status=#{source}
                AND (creator_id=#{actorId} OR #{admin})
            """)
    int transition(@Param("id") long id, @Param("version") long version,
            @Param("source") ActivityStatus source, @Param("target") ActivityStatus target,
            @Param("actorId") long actorId, @Param("admin") boolean admin);
}
