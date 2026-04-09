import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

export default defineConfig({
    plugins: [vue()],
    test: {
        environment: 'jsdom',
        globals: true,
        clearMocks: true,
        coverage: {
            provider: 'v8',
            reporter: ['text', 'html'],
            include: [
                'src/services/authService.js',
                'src/services/mentorService.js',
                'src/services/websocketService.js',
                'src/stores/**/*.js',
                'src/components/FeedbackPanel.vue'
            ],
            thresholds: {
                lines: 55,
                functions: 55,
                statements: 55,
                branches: 45
            }
        }
    }
})
