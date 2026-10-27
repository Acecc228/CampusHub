package com.campushub.auth;

import java.io.IOException;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WechatHttpClientTest {
    final HttpClient http = mock(HttpClient.class);
    final WechatHttpClient client = new WechatHttpClient(http, new ObjectMapper(), "wx1234567890abcdef", "0123456789abcdef0123456789abcdef");

    @SuppressWarnings("unchecked")
    void body(String body, int status) throws Exception {
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(response));
    }

    @Test void fixedHttpsEndpointAndBoundedTimeoutReturnOnlyOpenid() throws Exception {
        body("{\"openid\":\"open-id\",\"session_key\":\"discard-private-key\"}", 200);
        assertThat(client.exchange("one-time-code")).isEqualTo("open-id");
        var request = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).sendAsync(request.capture(), any());
        assertThat(request.getValue().uri().getHost()).isEqualTo("api.weixin.qq.com");
        assertThat(request.getValue().uri().getPath()).isEqualTo("/sns/jscode2session");
        assertThat(request.getValue().uri().getScheme()).isEqualTo("https");
        assertThat(request.getValue().timeout()).contains(java.time.Duration.ofSeconds(5));
    }

    @Test void invalidExpiredOrReusedCodeHasSafeFailure() throws Exception {
        body("{\"errcode\":40029,\"errmsg\":\"secret-upstream-marker\"}", 200);
        assertThatThrownBy(() -> client.exchange("code")).hasMessage("微信登录失败");
        verify(http, times(1)).sendAsync(any(), any());
    }

    @Test void timeoutMalformedAndOversizedResponseNeverRetry() throws Exception {
        for (String body : new String[]{"invalid-json", "{}", "x".repeat(17000)}) {
            body(body, 200);
            assertThatThrownBy(() -> client.exchange("code")).hasMessage("微信登录暂时不可用");
        }
        when(http.sendAsync(any(), any())).thenReturn(java.util.concurrent.CompletableFuture.failedFuture(new IOException("private-transport-details")));
        assertThatThrownBy(() -> client.exchange("code")).hasMessage("微信登录暂时不可用");
        verify(http, times(4)).sendAsync(any(), any());
    }

    @Test void inputCannotSupplyIdentityOrEndpoint() {
        for (String code : new String[]{"", " ", "x".repeat(513), "https://attacker/code"}) {
            assertThatThrownBy(() -> client.exchange(code)).hasMessage("请求格式或参数无效");
        }
        verifyNoInteractions(http);
    }

    @Test void boundedSubscriberCancelsBeforeRetainingOversizedUpstreamBody() {
        var subscriber = new WechatHttpClient.LimitedBodySubscriber();
        var subscription = mock(java.util.concurrent.Flow.Subscription.class);
        subscriber.onSubscribe(subscription);
        subscriber.onNext(java.util.List.of(java.nio.ByteBuffer.wrap(new byte[16385])));
        assertThat(subscriber.getBody().toCompletableFuture()).isCompletedExceptionally();
        verify(subscription).cancel();
    }
}
