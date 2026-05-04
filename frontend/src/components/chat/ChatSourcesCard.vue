<template>
  <article class="card card--span-3">
    <div class="card-head">
      <div>
        <h3>来源片段</h3>
        <p>问答依据仅来自当前知识库内的召回片段。</p>
      </div>
    </div>
    <el-empty v-if="sources.length === 0" description="暂无来源片段" />
    <el-collapse v-else>
      <el-collapse-item v-for="(source, index) in sources" :key="index" :name="index">
        <template #title>
          来源 {{ index + 1 }} · {{ source.documentName }} · chunk {{ source.chunkIndex }}
        </template>
        <div class="source-meta">
          <span>{{ source.knowledgeBaseName }}</span>
          <span>score {{ formatScore(source.score) }}</span>
        </div>
        <pre class="source-content">{{ source.content }}</pre>
      </el-collapse-item>
    </el-collapse>
  </article>
</template>

<script setup>
import { formatScore } from '../../composables/useUtils'

defineProps({
  sources: {
    type: Array,
    default: () => []
  }
})
</script>
