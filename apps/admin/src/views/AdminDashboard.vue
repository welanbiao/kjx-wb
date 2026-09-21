<template>
  <div>
    <h1>工作台</h1>
    <el-alert
      title="当前是 WP-00C 最小壳，业务页由 WP-21 在 RuoYi-Vue-Plus v5.2.3 上实现。"
      type="info"
      show-icon
      :closable="false"
    />
    <el-descriptions
      :column="1"
      border
      style="margin-top: 16px"
    >
      <el-descriptions-item label="后续接入">
        RuoYi-Vue-Plus v5.2.3 plus-ui
      </el-descriptions-item>
      <el-descriptions-item label="契约前缀">
        /admin-api
      </el-descriptions-item>
      <el-descriptions-item label="当前行为">
        无真实登录、无菜单权限、无 token 缓存
      </el-descriptions-item>
    </el-descriptions>
  </div>
</template>
