<template>
  <div>
    <a
      class="kjx-skip"
      href="#main"
    >跳到主内容</a>
    <el-container class="shell">
      <el-aside
        class="aside"
        :width="collapsed ? '0px' : '228px'"
      >
        <div class="brand">
          <span
            class="mark"
            aria-hidden="true"
          />
          <div>
            <strong>叩君芯</strong>
            <small>分幕通行证 · 管理台</small>
          </div>
        </div>
        <el-menu
          router
          :default-active="active"
          class="menu"
        >
          <el-menu-item
            v-for="item in menus"
            :key="item.path"
            :index="item.path"
          >
            {{ item.title }}
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-container>
        <el-header class="header">
          <button
            class="nav-toggle"
            type="button"
            :aria-expanded="!collapsed"
            aria-controls="admin-nav"
            @click="collapsed = !collapsed"
          >
            菜单
          </button>
          <p class="role">
            {{ roleLabel }}
            <span class="sep">·</span>
            前端隐藏不是授权，后端才是权限边界
          </p>
          <el-button
            class="logout"
            @click="onLogout"
          >
            退出
          </el-button>
        </el-header>
        <el-main
          id="main"
          class="main"
          tabindex="-1"
        >
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { logoutAdmin } from "@/api/auth";
import { isHost, isOps, visibleMenu } from "@/lib/permissions";
import { sessionState } from "@/lib/session";

const route = useRoute();
const router = useRouter();
const collapsed = ref(false);
const active = computed(() => route.path);
const menus = computed(() => visibleMenu(sessionState().session));
const roleLabel = computed(() => {
  const session = sessionState().session;
  if (isOps(session)) {
    return session?.roles.includes("superadmin") ? "superadmin" : "OPERATOR";
  }
  if (isHost(session)) {
    return `HOST · 部门 ${session?.deptId ?? "-"}`;
  }
  return session?.username ?? "";
});

async function onLogout() {
  await logoutAdmin();
  await router.push("/login");
}
</script>

<style scoped>
.shell {
  min-height: 100vh;
}
.aside {
  overflow: hidden;
  background: var(--kjx-bg);
  transition: width 0.18s ease;
}
.brand {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 18px 16px;
  color: var(--kjx-ink);
}
.brand small {
  display: block;
  color: var(--kjx-muted);
  font-size: 12px;
}
.mark {
  width: 14px;
  height: 28px;
  border: 1px solid var(--kjx-accent);
  background: var(--kjx-cream);
}
.menu {
  border-right: 0;
  background: transparent;
}
.header {
  display: flex;
  gap: 12px;
  align-items: center;
  border-bottom: 1px solid #d7e3ea;
  background: #fff;
}
.role {
  flex: 1;
  margin: 0;
  color: #4d6570;
  font-size: 13px;
}
.sep {
  margin: 0 6px;
  color: #9aadb6;
}
.nav-toggle {
  display: none;
  min-height: var(--kjx-touch);
  padding: 0 12px;
  border: 1px solid var(--kjx-line);
  background: var(--kjx-cream);
  color: var(--kjx-ticket-ink);
}
.main {
  background: var(--kjx-paper);
}
:deep(.el-menu-item) {
  color: var(--kjx-muted);
}
:deep(.el-menu-item.is-active) {
  color: var(--kjx-cream);
  background: rgb(244 82 13 / 0.18);
}
@media (max-width: 768px) {
  .nav-toggle {
    display: inline-flex;
    align-items: center;
  }
  .role {
    font-size: 12px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .aside {
    transition: none;
  }
}
</style>
