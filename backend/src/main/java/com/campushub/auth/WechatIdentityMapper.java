package com.campushub.auth;

import org.apache.ibatis.annotations.*;

@Mapper
public interface WechatIdentityMapper {
    class NewUser {
        private long id;
        private final String loginName;
        public NewUser(String loginName) { this.loginName = loginName; }
        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public String getLoginName() { return loginName; }
    }

    // The unique key serializes first-login creation; the claim and user are committed together.
    @Insert("INSERT INTO app_wechat_identity(appid,openid) VALUES(#{appid},#{openid}) ON DUPLICATE KEY UPDATE appid=app_wechat_identity.appid")
    int claim(String appid, String openid);

    @Select("SELECT user_id FROM app_wechat_identity WHERE appid=#{appid} AND openid=#{openid} FOR UPDATE")
    Long lockUser(String appid, String openid);

    @Insert("INSERT INTO app_user(login_name,display_name,password_hash,enabled,demo_account) VALUES(#{loginName},'微信学生',NULL,TRUE,FALSE)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertUser(NewUser user);

    @Insert("INSERT INTO app_user_role(user_id,role_id) SELECT #{userId},id FROM app_role WHERE code='STUDENT'")
    int addStudentRole(long userId);

    @Update("UPDATE app_wechat_identity SET user_id=#{userId} WHERE appid=#{appid} AND openid=#{openid} AND user_id IS NULL")
    int bind(String appid, String openid, long userId);
}
