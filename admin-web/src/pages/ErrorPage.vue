<script setup lang="ts">
import { useRoute } from "vue-router";
const route = useRoute();
</script>
<template>
  <div class="panel state" style="min-height: 400px">
    <div class="eyebrow">CAMPUSHUB / {{ route.meta.status }}</div>
    <h1>{{ route.meta.title }}</h1>
    <p>
      {{
        route.meta.status === 403
          ? "当前账号没有管理权限。请退出并使用组织者或管理员账号登录。"
          : route.meta.status === 503
            ? "无法连接后端服务，请检查网络和服务状态后重新尝试。"
            : "这个页面不存在，或链接已经发生变化。"
      }}
    </p>
    <a v-if="route.meta.status === 503" href="/">重新连接</a>
    <RouterLink v-else-if="route.meta.status !== 403" to="/"
      >返回工作台</RouterLink
    >
  </div>
</template>
