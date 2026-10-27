package com.campushub.auth;

import jakarta.servlet.http.*;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.*;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.annotation.JsonDeserialize;
import com.campushub.activity.StrictStringDeserializer;

@RestController
public class WechatLoginController {
    public record LoginCode(@JsonDeserialize(using = StrictStringDeserializer.class) String code) {}
    private final WechatHttpClient wechat;
    private final WechatIdentityService identities;
    private final HttpSessionSecurityContextRepository contexts = new HttpSessionSecurityContextRepository();

    public WechatLoginController(WechatHttpClient wechat,WechatIdentityService identities) {
        this.wechat = wechat; this.identities = identities;
    }

    public static SessionAuthenticationStrategy sessionStrategy() {
        return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(new HttpSessionCsrfTokenRepository())));
    }

    @PostMapping("/api/v1/auth/wechat-login")
    public CampusPrincipal.Identity login(@RequestBody LoginCode input,HttpServletRequest request,HttpServletResponse response) {
        try {
            var principal = identities.resolve(wechat.appid(),wechat.exchange(input.code()));
            var authentication = UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities());
            sessionStrategy().onAuthentication(authentication,request,response);
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            contexts.saveContext(context,request,response);
            return principal.identity();
        } catch (RuntimeException exception) {
            SecurityContextHolder.clearContext();
            var session = request.getSession(false);
            if (session != null) session.invalidate();
            throw exception;
        }
    }
}
