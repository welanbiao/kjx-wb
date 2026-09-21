import { createRouter, createWebHistory } from "vue-router";
import AdminLayout from "@/layouts/AdminLayout.vue";
import AdminDashboard from "@/views/AdminDashboard.vue";
import AdminHealth from "@/views/AdminHealth.vue";
import AdminLogin from "@/views/AdminLogin.vue";

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: "/login",
      name: "login",
      component: AdminLogin,
    },
    {
      path: "/",
      component: AdminLayout,
      redirect: "/dashboard",
      children: [
        {
          path: "dashboard",
          name: "dashboard",
          component: AdminDashboard,
        },
        {
          path: "health",
          name: "health",
          component: AdminHealth,
        },
      ],
    },
  ],
});

export default router;
