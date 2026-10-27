package com.campushub.auth;

import com.campushub.error.BusinessException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.DependsOn;
import tools.jackson.databind.ObjectMapper;

@Component
@DependsOn("demoPolicy")
public class WechatHttpClient {
    private final HttpClient http;
    private final ObjectMapper json;
    private final String appid;
    private final String secret;

    @Autowired
    public WechatHttpClient(ObjectMapper json, @Value("${campushub.wechat.app-id:}") String appid,
            @Value("${campushub.wechat.app-secret:}") String secret) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build(), json, appid, secret);
    }

    WechatHttpClient(HttpClient http, ObjectMapper json, String appid, String secret) {
        this.http = http; this.json = json; this.appid = appid; this.secret = secret;
    }

    public String appid() { return appid; }

    public String exchange(String code) {
        if (code == null || !code.matches("[a-zA-Z0-9_-]{1,512}")) {
            throw new BusinessException(400, "INVALID_INPUT", "请求格式或参数无效");
        }
        if (appid.isBlank() || secret.isBlank()) throw unavailable();
        // No configurable URL, redirects, retry, request logging, or session_key field.
        var uri = URI.create("https://api.weixin.qq.com/sns/jscode2session?appid=" + encode(appid)
                + "&secret=" + encode(secret) + "&js_code=" + encode(code) + "&grant_type=authorization_code");
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build();
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            pending = http.sendAsync(request, ignored -> new LimitedBodySubscriber());
            var response = pending.get(5, TimeUnit.SECONDS);
                if (response.statusCode() != 200) throw unavailable();
                byte[] bytes = response.body();
                if (bytes.length > 16384) throw unavailable();
                var result = json.readTree(bytes);
                if (result == null || !result.isObject()) throw unavailable();
                int error = result.path("errcode").asInt(0);
                if (error == 40029 || error == 40163) throw new BusinessException(401, "UNAUTHORIZED", "微信登录失败");
                if (error != 0 || !result.path("openid").isString()) throw unavailable();
                String openid = result.path("openid").asText();
                if (!openid.matches("[a-zA-Z0-9_-]{1,128}")) throw unavailable();
                return openid;
        } catch (InterruptedException exception) {
            if (pending != null) pending.cancel(true);
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (TimeoutException exception) {
            if (pending != null) pending.cancel(true);
            throw unavailable();
        } catch (ExecutionException exception) { throw unavailable();
        } catch (BusinessException exception) { throw exception;
        } catch (RuntimeException exception) { throw unavailable(); }
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static BusinessException unavailable() { return new BusinessException(503, "SERVICE_UNAVAILABLE", "微信登录暂时不可用"); }

    static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody() { return body; }
        @Override public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (buffer.remaining() > 16384 - bytes.size()) {
                    subscription.cancel();
                    body.completeExceptionally(new IOException("Upstream response exceeded limit"));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { body.completeExceptionally(error); }
        @Override public void onComplete() { body.complete(bytes.toByteArray()); }
    }
}
