// Role checks here improve navigation only; every backend endpoint enforces its own policy.
export function createAccessGuard(auth, loadProfile) {
  return async to => {
    if (to.meta.requiresAuth && !auth.token) return '/login'
    if (to.meta.requiresAuth) {
      const token = auth.token
      try {
        const profile = await loadProfile()
        if (token !== auth.token) return '/login'
        auth.role = profile.role
        auth.fullName = profile.fullName
        if (to.meta.roles && !to.meta.roles.includes(profile.role)) return '/dashboard'
      } catch {
        // A failed identity check never grants access using a cached browser role.
        if (!auth.token) return '/login'
        return '/unavailable'
      }
    }
    if (['/login', '/register'].includes(to.path) && auth.token) return '/dashboard'
    return true
  }
}
