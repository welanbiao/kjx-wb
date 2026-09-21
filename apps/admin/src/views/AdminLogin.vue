<template>
  <div class="login">
    <el-card class="card">
      <h1>管理端登录占位</h1>
      <p class="hint">
        不会写入 token。后续由 RuoYi-Vue-Plus v5.2.3 接管认证，而不是当前伪造其源码。
      </p>
      <el-form
        :model="form"
        label-position="top"
        @submit.prevent="onSubmit"
      >
        <el-form-item label="账号">
          <el-input
            v-model="form.username"
            autocomplete="username"
          />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>
        <el-button
          type="primary"
          native-type="submit"
          style="width: 100%"
        >
          进入骨架
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";

const router = useRouter();
const form = reactive({
  username: "admin",
  password: "",
});

function onSubmit() {
  ElMessage.info("登录仅为骨架占位，未保存任何凭证。");
  void router.push("/dashboard");
}
</script>

<style scoped>
.login {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: #f2f3f5;
}
.card {
  width: 420px;
}
h1 {
  margin: 0 0 8px;
  font-size: 20px;
}
.hint {
  margin: 0 0 16px;
  color: #909399;
  line-height: 1.6;
}
</style>
