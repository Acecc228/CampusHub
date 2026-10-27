package com.campushub.auth;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface IdentityMapper {
    record Account(long id, String loginName, String displayName, String passwordHash, boolean enabled, boolean demoAccount) {}

    @Select("SELECT id,login_name,display_name,password_hash,enabled,demo_account FROM app_user WHERE login_name=#{loginName}")
    Account findByLoginName(String loginName);

    @Select("SELECT id,login_name,display_name,password_hash,enabled,demo_account FROM app_user WHERE id=#{id}")
    Account findById(long id);

    @Select("SELECT r.code FROM app_role r JOIN app_user_role ur ON ur.role_id=r.id WHERE ur.user_id=#{userId} ORDER BY r.code")
    List<String> roles(long userId);
}
