import { describe, expect, test, vi } from 'vitest'
import { createAccessGuard } from '../access'
const staff = { path: '/teacher', meta: { requiresAuth: true, roles: ['TEACHER', 'ADMIN'] } }
describe('role-aware navigation', () => {
  test('anonymous never loads protected profile', async () => {
    const profile = vi.fn()
    expect(await createAccessGuard({ token: '' }, profile)(staff)).toBe('/login')
    expect(profile).not.toHaveBeenCalled()
  })
  test('server role overrides forged local role', async () => {
    const auth = { token: 'signed', role: 'ADMIN' }
    expect(await createAccessGuard(auth, async () => ({ role: 'STUDENT' }))(staff)).toBe('/dashboard')
    expect(auth.role).toBe('STUDENT')
  })
  test('teacher can enter authoring, but cannot enter administrator route', async () => {
    const guard = createAccessGuard({ token: 'signed' }, async () => ({ role: 'TEACHER' }))
    expect(await guard(staff)).toBe(true)
    expect(await guard({ path: '/admin/users', meta: { requiresAuth: true, roles: ['ADMIN'] } })).toBe('/dashboard')
  })
  test('failed server check fails closed', async () => {
    expect(await createAccessGuard({ token: 'signed', role: 'ADMIN' }, async () => { throw new Error() })(staff)).toBe('/unavailable')
  })
})
