const cleanups = new Set()

export function registerSessionCleanup(cleanup) {
  cleanups.add(cleanup)
  return () => cleanups.delete(cleanup)
}

export function clearSessionResources() {
  // Each resource gets a chance to discard private data even if another fails.
  Array.from(cleanups).forEach(cleanup => {
    try { cleanup() } catch { /* Do not log source-bearing transport errors. */ }
  })
}
