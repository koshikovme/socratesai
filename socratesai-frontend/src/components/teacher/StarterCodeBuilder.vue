<script setup>
import { computed, ref } from 'vue'
import { useI18n } from '../../i18n'
import { blankStarterContract, createStarterCode, parameterTypes, returnTypes, validateStarterContract } from '../../services/starterCodeService'

const props = defineProps({ languageKey: { type: String, required: true }, currentSource: { type: String, default: '' } })
const emit = defineEmits(['apply'])
const { t } = useI18n()
const contract = ref(blankStarterContract())
const error = computed(() => validateStarterContract(contract.value))
function apply() {
  if (error.value) return
  const source = createStarterCode(props.languageKey, contract.value)
  if (props.currentSource.trim() && source !== props.currentSource && !window.confirm(t('Replace the current starter code with this generated template?'))) return
  emit('apply', source)
}
</script>

<template>
  <details class="starter-builder">
    <summary>{{ t('Create starter with named parameters') }}</summary>
    <p class="muted">{{ t('Define the inputs students receive and the return value. The generated main reads input and prints the answer; students write the function logic.') }}</p>
    <p class="muted">{{ t('Parameters are read in order. Text is one word without spaces. An integer array is read as its length followed by its elements. For other formats, edit the generated input adapter.') }}</p>
    <div class="authoring-grid">
      <label>{{ t('Function name') }}<input v-model="contract.functionName" maxlength="40" /></label>
      <label>{{ t('Return type') }}<select v-model="contract.returnType"><option v-for="type in returnTypes" :key="type" :value="type">{{ t(type) }}</option></select></label>
    </div>
    <div v-for="(parameter, index) in contract.parameters" :key="index" class="authoring-entry">
      <div class="authoring-grid">
        <label>{{ t('Parameter name') }}<input v-model="parameter.name" maxlength="40" /></label>
        <label>{{ t('Parameter type') }}<select v-model="parameter.type"><option v-for="type in parameterTypes" :key="type" :value="type">{{ t(type) }}</option></select></label>
      </div>
      <button type="button" class="ghost-btn" @click="contract.parameters.splice(index, 1)">{{ t('Remove parameter') }}</button>
    </div>
    <p v-if="error" role="status" class="error-text">{{ t(error) }}</p>
    <div class="authoring-actions">
      <button type="button" class="ghost-btn" :disabled="contract.parameters.length >= 6" @click="contract.parameters.push({ name: `value${contract.parameters.length + 1}`, type: 'INTEGER' })">{{ t('Add parameter') }}</button>
      <button type="button" class="ghost-btn" :disabled="!!error" @click="apply">{{ t('Use generated starter') }}</button>
    </div>
  </details>
</template>

<style scoped>
.starter-builder { margin-top: 12px; }
</style>
