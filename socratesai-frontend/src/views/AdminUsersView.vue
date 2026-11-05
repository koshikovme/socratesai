<script setup>
import { useI18n } from '../i18n'
import { onBeforeUnmount, ref } from 'vue'
import AppHeader from '../components/AppHeader.vue'
import { teacherService } from '../services/teacherService'
import { apiErrorMessage } from '../services/errors'
import { registerSessionCleanup } from '../services/sessionLifecycle'
import '../components/teacher/authoring.css'

const { t } = useI18n()

const userId = ref('')
const expectedRole = ref('STUDENT')
const role = ref('TEACHER')
const reason = ref('')
const busy = ref(false)
const error = ref('')
const notice = ref('')
const controller = new AbortController()
const clear = () => { controller.abort(); userId.value = ''; reason.value = ''; error.value = ''; notice.value = '' }
const unregister = registerSessionCleanup(clear)
onBeforeUnmount(() => { unregister(); clear() })

async function save() {
  if (busy.value || controller.signal.aborted) return
  if (!Number.isSafeInteger(Number(userId.value)) || Number(userId.value) < 1 || !reason.value.trim()) { error.value = 'Enter a valid user ID and a reason.'; return }
  busy.value = true; error.value = ''; notice.value = ''
  try {
    const result = await teacherService.changeRole(Number(userId.value), { expectedRole: expectedRole.value, role: role.value, reason: reason.value.trim() }, { signal: controller.signal })
    if (controller.signal.aborted) return
    notice.value = { id: result.id, role: result.role }
    expectedRole.value = result.role; reason.value = ''
  } catch (failure) { if (!controller.signal.aborted) error.value = apiErrorMessage(failure) }
  finally { if (!controller.signal.aborted) busy.value = false }
}
</script>

<template>
  <div class="page-shell"><AppHeader />
    <main class="page-content authoring-page">
      <section class="card"><h1>{{ t('Administrative role assignment') }}</h1><p class="muted">{{ t('Use the existing account’s numeric user ID and current role. The server checks the current role and records the reason for this change.') }}</p></section>
      <form class="card authoring-form" @submit.prevent="save">
        <p v-if="error" class="error-text" role="alert">{{ t(error) }}</p>
        <p v-if="notice" class="success-text" role="status">{{ t('Role for user #{id} changed to {role}. The action was recorded in the audit log.', { id: notice.id, role: t(notice.role) }) }}</p>
        <fieldset :disabled="busy">
          <legend>{{ t('Change an existing user’s role') }}</legend>
          <label>{{ t('User ID') }}<input v-model="userId" type="number" min="1" max="9007199254740991" required /></label>
          <div class="authoring-grid">
            <label>{{ t('Expected current role') }}<select v-model="expectedRole" :aria-label="t('Expected current role')"><option value="STUDENT">{{ t('STUDENT') }}</option><option value="TEACHER">{{ t('TEACHER') }}</option><option value="ADMIN">{{ t('ADMIN') }}</option></select></label>
            <label>{{ t('New role') }}<select v-model="role" :aria-label="t('New role')"><option value="STUDENT">{{ t('STUDENT') }}</option><option value="TEACHER">{{ t('TEACHER') }}</option><option value="ADMIN">{{ t('ADMIN') }}</option></select></label>
          </div>
          <label>{{ t('Reason for change') }}<textarea v-model="reason" maxlength="500" rows="3" required /></label>
        </fieldset>
        <button class="primary-btn" :disabled="busy || expectedRole === role">{{ t(busy ? 'Saving…' : 'Apply audited role change') }}</button>
      </form>
    </main>
  </div>
</template>
