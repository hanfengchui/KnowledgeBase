<template>
  <article class="card card--span-2">
    <div class="card-head">
      <div>
        <h3>知识问答</h3>
        <p>当前知识库范围内检索，并按权限触发业务工具。</p>
      </div>
      <div class="inline-actions">
        <span class="field-label">Top K</span>
        <el-input-number v-model="topKModel" :min="1" :max="10" size="small" />
      </div>
    </div>

    <div class="question-chips">
      <el-button v-for="item in questionExamples" :key="item" class="chip" :title="item" @click="questionModel = item">
        {{ item }}
      </el-button>
    </div>

    <el-input
      v-model="questionModel"
      type="textarea"
      :rows="4"
      placeholder="例如：企业标准退款规则是什么？或 查询 ORD-2026-0001 当前状态"
    />

    <div class="action-row">
      <span class="muted">
        {{ canAsk ? '问答可用' : '问答不可用' }} · {{ canUseTools ? '工具可用' : '工具不可用' }}
      </span>
      <el-button type="primary" :loading="asking" :disabled="!canAsk" @click="$emit('submit')">
        提交
      </el-button>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  question: {
    type: String,
    default: ''
  },
  topK: {
    type: Number,
    default: 5
  },
  asking: {
    type: Boolean,
    default: false
  },
  canAsk: {
    type: Boolean,
    default: false
  },
  canUseTools: {
    type: Boolean,
    default: false
  },
  questionExamples: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:question', 'update:topK', 'submit'])

const questionModel = computed({
  get: () => props.question,
  set: (value) => emit('update:question', value)
})

const topKModel = computed({
  get: () => props.topK,
  set: (value) => emit('update:topK', value)
})
</script>
