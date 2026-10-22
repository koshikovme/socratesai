<template>
  <div class="card editor-card">
    <div class="editor-toolbar">
      <span>Language: {{ normalizedLanguage }}</span>
      <span>Full source buffer</span>
    </div>

    <div class="monaco-shell">
      <VueMonacoEditor
          v-model:value="localValue"
          :key="contextId"
          :language="normalizedLanguage"
          theme="vs-dark"
          :options="editorOptions"
          class="monaco-editor-instance"
      />
    </div>
  </div>
</template>

<script setup>
import '../monaco-setup'
import { computed } from 'vue'
import { VueMonacoEditor } from '@guolao/vue-monaco-editor'

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  language: {
    type: String,
    default: 'java'
  },
  contextId: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue'])

const localValue = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const normalizedLanguage = computed(() => {
  const lang = (props.language || 'java').toLowerCase().trim()

  const map = {
    js: 'javascript',
    ts: 'typescript',
    py: 'python',
    python3: 'python',
    cpp17: 'cpp',
    cpp: 'cpp',
    csharp: 'csharp'
  }

  return map[lang] || lang
})

const editorOptions = {
  automaticLayout: true,
  minimap: {enabled: false},
  fontSize: 14,
  fontFamily: 'Consolas, Monaco, "Courier New", monospace',
  tabSize: 4,
  insertSpaces: true,
  wordWrap: 'on',
  smoothScrolling: true,
  lineNumbers: 'on',
  readOnly: false,
  scrollBeyondLastLine: false
}
</script>