import { createRouter, createWebHistory } from "vue-router";
import { accessForPath, canAccess } from "@/lib/permissions";
import { getSession } from "@/lib/session";
import AdminLayout from "@/layouts/AdminLayout.vue";
import AdminDashboard from "@/views/AdminDashboard.vue";
import AdminHealth from "@/views/AdminHealth.vue";
import AdminLogin from "@/views/AdminLogin.vue";
import AdminForbidden from "@/views/AdminForbidden.vue";
import HostProfile from "@/views/host/HostProfile.vue";
import HostMembers from "@/views/host/HostMembers.vue";
import HostMemberEditor from "@/views/host/HostMemberEditor.vue";
import HostMemberDetail from "@/views/host/HostMemberDetail.vue";
import HostBindings from "@/views/host/HostBindings.vue";
import UnlockRecords from "@/views/shared/UnlockRecords.vue";
import OpsHosts from "@/views/ops/OpsHosts.vue";
import OpsHostEditor from "@/views/ops/OpsHostEditor.vue";
import OpsMembers from "@/views/ops/OpsMembers.vue";
import OpsMemberEditor from "@/views/ops/OpsMemberEditor.vue";
import OpsMemberDetail from "@/views/ops/OpsMemberDetail.vue";
import OpsBindings from "@/views/ops/OpsBindings.vue";
import OpsAppeals from "@/views/ops/OpsAppeals.vue";
import OpsWorks from "@/views/ops/OpsWorks.vue";
import OpsWorkEditor from "@/views/ops/OpsWorkEditor.vue";
import OpsEntitlements from "@/views/ops/OpsEntitlements.vue";

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: "/login",
      name: "login",
      component: AdminLogin,
      meta: { public: true },
    },
    {
      path: "/",
      component: AdminLayout,
      redirect: "/dashboard",
      children: [
        { path: "dashboard", name: "dashboard", component: AdminDashboard },
        { path: "health", name: "health", component: AdminHealth },
        { path: "forbidden", name: "forbidden", component: AdminForbidden },
        { path: "self/host", name: "self-host", component: HostProfile },
        { path: "self/members", name: "self-members", component: HostMembers },
        { path: "self/members/new", name: "self-member-new", component: HostMemberEditor },
        { path: "self/members/:id", name: "self-member-detail", component: HostMemberDetail },
        { path: "self/members/:id/edit", name: "self-member-edit", component: HostMemberEditor },
        { path: "self/bindings", name: "self-bindings", component: HostBindings },
        { path: "self/unlock-records", name: "self-unlock", component: UnlockRecords },
        { path: "ops/hosts", name: "ops-hosts", component: OpsHosts },
        { path: "ops/hosts/new", name: "ops-host-new", component: OpsHostEditor },
        { path: "ops/hosts/:id", name: "ops-host-edit", component: OpsHostEditor },
        { path: "ops/members", name: "ops-members", component: OpsMembers },
        { path: "ops/members/new", name: "ops-member-new", component: OpsMemberEditor },
        { path: "ops/members/:id", name: "ops-member-detail", component: OpsMemberDetail },
        { path: "ops/members/:id/edit", name: "ops-member-edit", component: OpsMemberEditor },
        { path: "ops/bindings", name: "ops-bindings", component: OpsBindings },
        { path: "ops/appeals", name: "ops-appeals", component: OpsAppeals },
        { path: "ops/works", name: "ops-works", component: OpsWorks },
        { path: "ops/works/new", name: "ops-work-new", component: OpsWorkEditor },
        { path: "ops/works/:id", name: "ops-work-edit", component: OpsWorkEditor },
        { path: "ops/entitlements", name: "ops-entitlements", component: OpsEntitlements },
        { path: "ops/unlock-records", name: "ops-unlock", component: UnlockRecords },
      ],
    },
  ],
});

router.beforeEach((to) => {
  if (to.meta.public) {
    return true;
  }
  const session = getSession();
  if (!session) {
    return { name: "login", query: { redirect: to.fullPath } };
  }
  const access = accessForPath(to.path);
  if (!canAccess(session, access)) {
    return { name: "forbidden" };
  }
  return true;
});

export default router;
