import { mount } from '@vue/test-utils'
import { describe, it, expect } from 'vitest'
import SafeMarkdown from '../SafeMarkdown.vue'

describe('SafeMarkdown', () => {
  it('escapes author-controlled HTML and displays Markdown links as text', () => {
    const content = '# Task\n<img src=x onerror=alert(1)>\n<script>alert(2)</script>\n[link](javascript:alert(3))'
    const wrapper = mount(SafeMarkdown, { props: { content } })
    expect(wrapper.text()).toBe(content)
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.find('a').exists()).toBe(false)
    expect(wrapper.html()).toContain('&lt;img')
  })

  it('treats updated content through the same escaping boundary', async () => {
    const wrapper = mount(SafeMarkdown)
    await wrapper.setProps({ content: '<svg onload=alert(1)> & "quoted"' })
    expect(wrapper.find('svg').exists()).toBe(false)
    expect(wrapper.text()).toBe('<svg onload=alert(1)> & "quoted"')
  })
})
