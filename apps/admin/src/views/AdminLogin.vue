<template>
  <div
    v-if="embedded"
    class="embed"
  >
    <el-form
      label-position="top"
      @submit.prevent="onSubmit"
    >
      <el-form-item
        label="账号"
        required
      >
        <el-input
          v-model="form.username"
          autocomplete="username"
          aria-label="账号"
        />
      </el-form-item>
      <el-form-item
        label="密码"
        required
      >
        <el-input
          v-model="form.password"
          type="password"
          show-password
          autocomplete="current-password"
          aria-label="密码"
        />
      </el-form-item>
      <el-button
        type="primary"
        native-type="submit"
        :loading="busy"
        style="width: 100%"
      >
        登录
      </el-button>
    </el-form>
  </div>
  <div
    v-else-if="standalone"
    class="login"
  >
    <el-card class="card">
      <p class="eyebrow">
        叩君芯 · 分幕通行证
      </p>
      <h1>管理台登录</h1>
      <p class="kjx-lead">
        后台令牌 loginType=admin。不会把密码、token 或验证码写进日志。
      </p>
      <el-form
        label-position="top"
        @submit.prevent="onSubmit"
      >
        <el-form-item
          label="账号"
          required
        >
          <el-input
            v-model="form.username"
            autocomplete="username"
            aria-label="账号"
          />
        </el-form-item>
        <el-form-item
          label="密码"
          required
        >
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="current-password"
            aria-label="密码"
          />
        </el-form-item>
        <el-button
          type="primary"
          native-type="submit"
          :loading="busy"
          style="width: 100%"
        >
          登录
        </el-button>
      </el-form>
      <el-alert
        v-if="mock"
        class="mock"
        type="info"
        :closable="false"
        title="当前为显式 Mock：host-a / host-b / operator / superadmin。密码均为 mock- 前缀，不是真实凭据。"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { loginAdmin } from "@/api/auth";
import { isMockApi } from "@/lib/env";
import { formatRequestFailure } from "@/lib/errors";

const router = useRouter();
const route = useRoute();
const mock = isMockApi();
const busy = ref(false);
const form = reactive({
  username: "",
  password: "",
});
const embedded = computed(() => route.query.embed === "1");
const standalone = !import.meta.env.BASE_URL.startsWith("/admin");

onMounted(() => {
  if (embedded.value) {
    document.body.style.background = "#faedd1";
    document.documentElement.style.background = "#faedd1";
  }
});

function deskUrl(redirect: string): string {
  if (redirect.startsWith("/admin/")) {
    return redirect;
  }
  const base = import.meta.env.BASE_URL.replace(/\/$/, "");
  const path = redirect.startsWith("/") ? redirect : `/${redirect}`;
  return `${base}${path}`;
}

async function onSubmit() {
  busy.value = true;
  try {
    await loginAdmin(form.username.trim(), form.password);
    const redirect = typeof route.query.redirect === "string" ? route.query.redirect : "/dashboard";
    if (embedded.value) {
      (window.top ?? window).location.assign(deskUrl(redirect));
      return;
    }
    await router.replace(redirect);
  } catch (error) {
    const failure = formatRequestFailure(error);
    ElMessage.error(failure.requestId ? `${failure.title}（${failure.requestId}）` : failure.title);
  } finally {
    busy.value = false;
  }
}
</script>

<style scoped>
.login {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background:
    radial-gradient(900px 420px at 70% -10%, rgb(19 135 192 / 0.55) 0%, transparent 60%),
    var(--kjx-bg);
}
.card {
  width: min(440px, 100%);
}
.embed {
  background: #faedd1;
}
.eyebrow {
  margin: 0 0 4px;
  color: var(--kjx-accent);
  letter-spacing: 0.12em;
  font-size: 12px;
  text-transform: uppercase;
}
h1 {
  margin: 0 0 8px;
  font-size: 24px;
}
.mock {
  margin-top: 16px;
}
</style>
