<template>
  <div>
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
    />
    <p
      v-if="requestId"
      class="kjx-request-id"
    >
      requestId：{{ requestId }}
    </p>
    <el-skeleton
      v-if="loading"
      :rows="6"
      animated
    />
    <el-empty
      v-else-if="empty && !error"
      :description="emptyText"
    />
    <div
      v-else-if="!error"
      class="kjx-table-wrap"
    >
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  loading: boolean;
  error: string;
  empty: boolean;
  requestId: string;
  emptyText?: string;
}>();
</script>
