<template>
  <nav class="nav-list">
    <button
      v-for="item in items"
      :key="item.name"
      type="button"
      :class="['nav-item', { active: activeName === item.name }]"
      @click="$emit('navigate', item.name)"
    >
      <span class="nav-badge">{{ item.badge }}</span>
      <span class="nav-body">
        <strong>{{ item.label }}</strong>
        <small>{{ item.description }}</small>
      </span>
    </button>
  </nav>
</template>

<script setup>
defineProps({
  items: {
    type: Array,
    default: () => []
  },
  activeName: {
    type: String,
    default: ''
  }
})

defineEmits(['navigate'])
</script>
