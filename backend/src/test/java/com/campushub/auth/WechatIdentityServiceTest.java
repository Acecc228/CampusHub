package com.campushub.auth;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WechatIdentityServiceTest {
    final WechatIdentityMapper mapping = mock(WechatIdentityMapper.class);
    final IdentityMapper users = mock(IdentityMapper.class);
    final WechatIdentityService service = new WechatIdentityService(mapping, users);

    @Test void newUserGetsOnlyStudentRoleAndNoPassword() {
        when(mapping.lockUser("app", "openid")).thenReturn(null);
        doAnswer(call -> { ((WechatIdentityMapper.NewUser) call.getArgument(0)).setId(9); return 1; }).when(mapping).insertUser(any());
        when(users.findById(9)).thenReturn(new IdentityMapper.Account(9, "wx_opaque", "微信学生", null, true, false));
        when(users.roles(9)).thenReturn(List.of("STUDENT"));
        when(mapping.addStudentRole(9)).thenReturn(1);
        when(mapping.bind("app", "openid", 9)).thenReturn(1);
        assertThat(service.resolve("app", "openid").identity().roles()).containsExactly("STUDENT");
        verify(mapping).addStudentRole(9);
        verify(mapping).bind("app", "openid", 9);
    }

    @Test void existingMappingNeverPromotesManagerOrDisabledUser() {
        when(mapping.lockUser("app", "openid")).thenReturn(8L);
        when(users.findById(8)).thenReturn(new IdentityMapper.Account(8, "existing", "Existing", null, true, false));
        when(users.roles(8)).thenReturn(List.of("ADMIN"));
        assertThatThrownBy(() -> service.resolve("app", "openid")).hasMessage("微信登录失败");
        verify(mapping, never()).addStudentRole(anyLong());
        verify(mapping, never()).insertUser(any());
    }
}
