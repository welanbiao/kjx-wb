<template>
  <header class="head">
    <div>
      <h1>{{ title }}</h1>
      <p
        v-if="lead"
        class="kjx-lead"
      >
        {{ lead }}
      </p>
    </div>
    <div class="kjx-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup lang="ts">
defineProps<{
  title: string;
  lead?: string;
}>();
</script>

<style scoped>
.head {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
</style>
