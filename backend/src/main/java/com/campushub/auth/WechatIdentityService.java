package com.campushub.auth;

import com.campushub.error.BusinessException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WechatIdentityService {
    private final WechatIdentityMapper mapping;
    private final IdentityMapper users;

    public WechatIdentityService(WechatIdentityMapper mapping, IdentityMapper users) {
        this.mapping = mapping;
        this.users = users;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CampusPrincipal resolve(String appid, String openid) {
        mapping.claim(appid, openid);
        Long userId = mapping.lockUser(appid, openid);
        if (userId == null) {
            var user = new WechatIdentityMapper.NewUser("wx_" + UUID.randomUUID().toString().replace("-", ""));
            mapping.insertUser(user);
            userId = user.getId();
            if (mapping.addStudentRole(userId) != 1 || mapping.bind(appid, openid, userId) != 1) {
                throw new IllegalStateException("Student identity could not be initialized");
            }
        }
        var account = users.findById(userId);
        var roles = users.roles(userId);
        if (account == null || !account.enabled() || account.demoAccount() || !roles.equals(List.of("STUDENT"))) {
            throw new BusinessException(401, "UNAUTHORIZED", "微信登录失败");
        }
        return new CampusPrincipal(account, roles);
    }
}
