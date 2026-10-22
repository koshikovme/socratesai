import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

export default defineConfig({
    plugins: [vue()],
    test: {
        environment: 'jsdom',
        globals: true,
        exclude: ['node_modules/**', 'e2e/**', 'dist/**'],
        clearMocks: true,
        coverage: {
            provider: 'v8',
            reportsDirectory: 'coverage-native',
            reporter: ['text', 'html'],
            include: ['src/services/**/*.js', 'src/stores/**/*.js', 'src/composables/**/*.js', 'src/components/**/*.vue', 'src/views/**/*.vue', 'src/router/access.js'],
            exclude: ['**/__tests__/**'],
            thresholds: {
                lines: 55,
                functions: 55,
                statements: 55,
                branches: 45
            }
        }
    }
})
