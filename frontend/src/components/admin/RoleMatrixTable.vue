<template>
  <article class="card">
    <div class="card-head">
      <div>
        <h3>角色与权限</h3>
        <p>本期采用固定角色模型，不支持动态自定义角色。</p>
      </div>
    </div>
    <el-table :data="roles" stripe>
      <el-table-column prop="code" label="角色代码" width="180" />
      <el-table-column prop="name" label="角色名称" width="160" />
      <el-table-column prop="scopeType" label="作用域" width="140" />
      <el-table-column prop="description" label="说明" min-width="220" />
      <el-table-column label="权限点" min-width="280">
        <template #default="{ row }">
          <div class="tag-row">
            <el-tag v-for="code in row.permissionCodes" :key="code" size="small" effect="plain">{{ code }}</el-tag>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </article>
</template>

<script setup>
defineProps({
  roles: {
    type: Array,
    default: () => []
  }
})
</script>
