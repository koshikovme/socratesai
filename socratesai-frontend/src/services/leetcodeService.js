import api from './api'

const LEETCODE_STORAGE_KEY = 'leetcode_tasks_cache'

export async function fetchLeetCodeTasks(params = {}) {
    const requestBody = {
        categorySlug: params.categorySlug || 'all-code-essentials',
        limit: params.limit ?? 20,
        skip: params.skip ?? 0,
        filters: {}
    }

    if (params.difficulty) {
        requestBody.filters.difficulty = params.difficulty
    }

    const { data } = await api.post('/api/leetcode/problems', requestBody)

    const normalizedTasks = (data.tasks || []).map(item => ({
        id: `leetcode-${item.questionFrontendId}`,
        externalId: item.questionFrontendId,
        source: 'leetcode',
        title: item.title,
        topic: 'LeetCode',
        language: 'java',
        difficulty: item.difficulty,
        description: stripHtml(item.content).slice(0, 220) + '...',
        content: item.content,
        isPaidOnly: item.isPaidOnly,
        starterCode: getDefaultStarterCode('java', item.title)
    }))

    sessionStorage.setItem(LEETCODE_STORAGE_KEY, JSON.stringify(normalizedTasks))

    return {
        totalNum: data.totalNum || 0,
        tasks: normalizedTasks
    }
}

export function getCachedLeetCodeTaskByRouteId(routeId) {
    const raw = sessionStorage.getItem(LEETCODE_STORAGE_KEY)
    if (!raw) return null

    const tasks = JSON.parse(raw)
    return tasks.find(task => task.id === routeId) || null
}

function stripHtml(html = '') {
    const div = document.createElement('div')
    div.innerHTML = html
    return div.textContent || div.innerText || ''
}

function getDefaultStarterCode(language = 'java', title = '') {
    if (language === 'java') {
        return `class Solution {\n    public int[] solve(int[] nums, int target) {\n        return new int[]{};\n    }\n}`
    }

    return ''
}