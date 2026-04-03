<template>
  <div class="card editor-card">
    <div class="editor-toolbar">
      <span>Language: {{ normalizedLanguage }}</span>
      <span>Attempt: {{ attemptNo }}</span>
    </div>

    <div class="monaco-shell">
      <VueMonacoEditor
          v-model:value="localValue"
          :language="normalizedLanguage"
          theme="vs-dark"
          :options="editorOptions"
          class="monaco-editor-instance"
      />
    </div>
  </div>
</template>

<script setup>
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
  attemptNo: {
    type: Number,
    default: 1
  }
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