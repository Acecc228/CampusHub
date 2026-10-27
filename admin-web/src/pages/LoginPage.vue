<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElButton } from "element-plus";
import { School } from "@element-plus/icons-vue";
import FormField from "../components/FormField.vue";
import { currentUser, signIn } from "../composables/auth";
import { canManage, safeReturnPath } from "../domain/activity";
import { errorMessage } from "../api/client";
const demoLogin =
  import.meta.env.DEV && import.meta.env.VITE_DEMO_LOGIN === "true";
const props = defineProps<{ recovery?: boolean }>();
const route = useRoute(),
  router = useRouter();
const username = ref(""),
  password = ref(""),
  error = ref(""),
  busy = ref(false);
const usernameError = ref(""),
  passwordError = ref("");
onMounted(() => document.getElementById("username")?.focus());
async function login() {
  if (busy.value) return;
  usernameError.value = username.value.trim() ? "" : "请输入账号。";
  passwordError.value = password.value ? "" : "请输入密码。";
  if (usernameError.value || passwordError.value) {
    document
      .getElementById(usernameError.value ? "username" : "password")
      ?.focus();
    return;
  }
  busy.value = true;
  error.value = "";
  try {
    const sameIdentity = await signIn(username.value.trim(), password.value);
    if (!canManage(currentUser.value?.roles || []))
      await router.replace("/403");
    else if (!props.recovery || !sameIdentity)
      await router.replace(
        props.recovery ? "/" : safeReturnPath(route.query.redirect),
      );
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    password.value = "";
    busy.value = false;
  }
}
</script>
<template>
  <div class="login-screen">
    <section class="login-story" aria-label="CampusHub 简介">
      <div class="brand">
        <span class="brand-mark"><School aria-hidden="true" /></span>CampusHub
      </div>
      <div>
        <div class="eyebrow">A place for campus connections</div>
        <h1>让好想法，<br />在校园里发生。</h1>
        <p>
          把筹备的细节留给工作台，<br />把更多时间留给每一场值得相聚的活动。
        </p>
        <div class="story-schedule">
          <div>
            <strong>有条理地筹备</strong
            ><span>从草稿到发布，掌握每一步进度。</span>
          </div>
          <div>
            <strong>看得见的参与</strong
            ><span>实时查看报名人数与活动名单。</span>
          </div>
          <div>
            <strong>恰到好处的协作</strong
            ><span>各司其职，让活动管理更清晰。</span>
          </div>
        </div>
      </div>
      <span class="muted small">CAMPUSHUB / 校园活动管理平台</span>
    </section>
    <main class="login-form-wrap" tabindex="-1">
      <div class="login-form">
        <div class="eyebrow">Your campus workspace</div>
        <h2>{{ recovery ? "重新连接工作台" : "欢迎回到 CampusHub" }}</h2>
        <p class="muted">
          {{
            recovery
              ? "登录状态已失效。使用同一账号登录，可继续当前编辑。"
              : "登录管理中心，开始筹备下一场精彩。"
          }}
        </p>
        <form novalidate @submit.prevent="login">
          <div v-if="error" class="notice error" role="alert">{{ error }}</div>
          <FormField
            id="username"
            :label="demoLogin ? '演示账号' : '账号'"
            :error="usernameError"
            :help="demoLogin ? 'demo_organizer 或 demo_admin' : undefined"
            required
            v-slot="f"
            ><input
              id="username"
              v-model="username"
              autocomplete="username"
              :aria-invalid="f.invalid"
              :aria-describedby="f.describedby"
              :disabled="busy"
              :placeholder="demoLogin ? '输入演示账号' : '输入账号'"
          /></FormField>
          <FormField
            id="password"
            label="密码"
            :error="passwordError"
            required
            v-slot="f"
            ><input
              id="password"
              v-model="password"
              type="password"
              autocomplete="current-password"
              :aria-invalid="f.invalid"
              :aria-describedby="f.describedby"
              :disabled="busy"
              placeholder="输入对应账号密码"
          /></FormField>
          <ElButton native-type="submit" type="primary" :loading="busy"
            >登录管理中心</ElButton
          >
        </form>
        <p v-if="demoLogin" class="login-foot">
          开发环境演示登录<br />仅限组织者和管理员。请使用本机配置的演示账号密码。
        </p>
      </div>
    </main>
  </div>
</template>
