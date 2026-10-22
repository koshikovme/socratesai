import { describe, expect, test } from 'vitest'
import { mount } from '@vue/test-utils'
import ExecutionResult from '../ExecutionResult.vue'

describe('student judging result allowlist', () => {
  test('never renders injected hidden output, reference source, tokens or diagnostics', () => {
    const wrapper = mount(ExecutionResult, { props: {
      operation: { kind: 'submission', problemVersionId: 3, languageKey: 'java' },
      result: { submission: { status: 'FINISHED', verdict: 'WRONG_ANSWER', testsTotal: 1, testsPassed: 0 },
        stdout: 'TOP_LEVEL_SECRET', referenceCode: 'REFERENCE_SECRET',
        cases: [{ position: 0, hidden: true, verdict: 'WRONG_ANSWER', timeConsumedMs: 4, memoryConsumedBytes: 1024, stdout: 'HIDDEN_SECRET', stderr: 'STDERR_SECRET', compileOutput: 'COMPILER_SECRET', token: 'TOKEN_SECRET' }] },
    } })
    expect(wrapper.text()).toContain('Hidden test 1: WRONG_ANSWER')
    expect(wrapper.text()).toContain('4 ms')
    expect(wrapper.text()).not.toMatch(/SECRET/)
    expect(wrapper.findAll('pre')).toHaveLength(0)
  })
  test('sample acceptance cannot claim solved, and pending recovery remains visible', () => {
    const wrapper = mount(ExecutionResult, { props: { operation: { kind: 'sample' }, result: { status: 'SYSTEM_ERROR', retryScheduled: true } } })
    expect(wrapper.text()).toContain('Recovering')
    expect(wrapper.text()).toContain('Only an accepted graded submission counts as solved')
  })
})
