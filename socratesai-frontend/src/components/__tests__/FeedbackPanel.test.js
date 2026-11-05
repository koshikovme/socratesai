import { describe, expect, test } from 'vitest'
import { mount } from '@vue/test-utils'
import FeedbackPanel from '../FeedbackPanel.vue'

describe('FeedbackPanel', () => {
    test('shows an AI failure where the hint belongs without a generic replacement', () => {
        const wrapper = mount(FeedbackPanel, { props: { error: 'The AI hint allowance is temporarily unavailable.' } })
        expect(wrapper.find('.feedback-card [role="alert"]').text()).toContain('AI hint allowance')
        expect(wrapper.findAll('.error-text')).toHaveLength(1)
        expect(wrapper.text()).not.toContain('Compare this part')
    })
    test('shows runtime failure rather than a misleading green compiled badge', () => {
        const wrapper = mount(FeedbackPanel, { props: { feedback: {
            action: 'CODE_HIGHLIGHT', compileSuccess: true, executionKnown: true, verdict: 'RUNTIME_ERROR',
            policyMode: 'GEMINI', feedbackSource: 'gemini', feedbackText: 'Your method needs a Main entry point.',
            mentorState: 'execution_safety', testsPassed: 0, testsFailed: 1,
        } } })
        expect(wrapper.find('.signal-error').text()).toBe('Runtime error')
        expect(wrapper.text()).toContain('Compilation succeeded, but the program failed')
        expect(wrapper.text()).toContain('Gemini classification and hint')
        expect(wrapper.text()).not.toContain('RULE')
        expect(wrapper.text()).toContain('Startup and runtime')
    })

    test('code-only AI hints never pretend a compiler or tests ran', () => {
        const wrapper = mount(FeedbackPanel, { props: { feedback: {
            action: 'GUIDING_QUESTION', compileSuccess: null, executionKnown: false,
            policyMode: 'ML', feedbackSource: 'gemini', feedbackText: 'Which input value does this expression use?',
            mentorState: 'semantic_debug', testsPassed: null, testsFailed: null,
        } } })
        expect(wrapper.text()).toContain('Code review only')
        expect(wrapper.text()).toContain('Not executed')
        expect(wrapper.text()).toContain('Python ML classification · Gemini hint')
    })
    test('renders formatted action label and metadata from feedback payload', () => {
        const wrapper = mount(FeedbackPanel, {
            props: {
                feedback: {
                    action: 'CODE_HIGHLIGHT',
                    compileSuccess: false,
                    feedbackText: 'Check the loop boundary.',
                    evidenceOrigin: 'SUBMISSION',
                    mentorState: 'semantic_debug',
                    testsPassed: 0,
                    testsFailed: 1,
                    interactionId: 'abc-123',
                    transport: 'WS',
                    sessionId: 'session-1'
                }
            }
        })

        expect(wrapper.text()).toContain('Code Highlight')
        expect(wrapper.text()).toContain('Check the loop boundary.')
        expect(wrapper.text()).toContain('Graded submission')
        expect(wrapper.text()).toContain('Logic and output')
        expect(wrapper.text()).toContain('Realtime')
        expect(wrapper.text()).not.toContain('session-1')
    })

    test('disables review buttons when interaction id is missing', () => {
        const wrapper = mount(FeedbackPanel, {
            props: {
                feedback: {
                    action: 'CONCEPTUAL_HINT',
                    compileSuccess: true,
                    feedbackText: 'Think about the condition.'
                }
            }
        })

        const buttons = wrapper.findAll('button')
        expect(buttons).toHaveLength(2)
        expect(buttons[0].attributes('disabled')).toBeDefined()
        expect(buttons[1].attributes('disabled')).toBeDefined()
    })
})
