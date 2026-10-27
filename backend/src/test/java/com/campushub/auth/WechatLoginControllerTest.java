package com.campushub.auth;

import com.campushub.error.BusinessException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WechatLoginControllerTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void successfulLoginRotatesSessionClearsCsrfAndExplicitlySavesContext() {
        var upstream=mock(WechatHttpClient.class);
        var identities=mock(WechatIdentityService.class);
        when(upstream.appid()).thenReturn("appid");
        when(upstream.exchange("code")).thenReturn("openid");
        var principal=new CampusPrincipal(new IdentityMapper.Account(1,"student","Student",null,true,false),List.of("STUDENT"));
        when(identities.resolve("appid","openid")).thenReturn(principal);
        var request=new MockHttpServletRequest();
        var response=new MockHttpServletResponse();
        var session=request.getSession(); String oldId=session.getId();
        var csrf=new HttpSessionCsrfTokenRepository();
        csrf.saveToken(csrf.generateToken(request),request,response);
        new WechatLoginController(upstream,identities).login(new WechatLoginController.LoginCode("code"),request,response);
        assertThat(request.getSession().getId()).isNotEqualTo(oldId);
        assertThat(csrf.loadToken(request)).isNull();
        var context=(org.springframework.security.core.context.SecurityContext) session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context.getAuthentication().getPrincipal()).isSameAs(principal);
        assertThat(context.getAuthentication().getCredentials()).isNull();
    }

    @Test void failedExchangeInvalidatesExistingSessionAndClearsContext() {
        var upstream=mock(WechatHttpClient.class);
        when(upstream.exchange("expired")).thenThrow(new BusinessException(401,"UNAUTHORIZED","微信登录失败"));
        var request=new MockHttpServletRequest();
        var session=(MockHttpSession) request.getSession();
        assertThatThrownBy(()->new WechatLoginController(upstream,mock(WechatIdentityService.class))
                .login(new WechatLoginController.LoginCode("expired"),request,new MockHttpServletResponse())).isInstanceOf(BusinessException.class);
        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test void codeMustBeJsonString() {
        assertThatThrownBy(()->new ObjectMapper().readValue("{\"code\":123}",WechatLoginController.LoginCode.class))
                .isInstanceOf(tools.jackson.databind.exc.MismatchedInputException.class);
    }
}
