import { describe, expect, test } from 'vitest'
import { mount } from '@vue/test-utils'
import FeedbackPanel from '../FeedbackPanel.vue'

describe('FeedbackPanel', () => {
    test('renders formatted action label and metadata from feedback payload', () => {
        const wrapper = mount(FeedbackPanel, {
            props: {
                feedback: {
                    action: 'CODE_HIGHLIGHT',
                    compileSuccess: false,
                    feedbackText: 'Check the loop boundary.',
                    errorType: 'OFF_BY_ONE',
                    suspiciousRegion: 'line 3',
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
        expect(wrapper.text()).toContain('OFF_BY_ONE')
        expect(wrapper.text()).toContain('line 3')
        expect(wrapper.text()).toContain('Realtime')
        expect(wrapper.text()).toContain('Session session-1')
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
