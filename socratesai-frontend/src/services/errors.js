const messages = {
  INVALID_REQUEST: 'Check the form fields and limits, then try again.',
  AUTHENTICATION_REQUIRED: 'Your session has expired. Please sign in again.',
  FORBIDDEN: 'You do not have access to this resource.',
  NOT_FOUND: 'This resource is unavailable or has expired.',
  CONFLICT: 'This resource changed. Reload the latest version before trying again.',
  VERIFICATION_REQUIRED: 'Verify the current draft successfully before publishing.',
  CAPACITY_EXCEEDED: 'The service is busy. Wait a moment before trying again.',
  SANDBOX_UNAVAILABLE: 'The execution service is unavailable. Please try again later.',
  MENTOR_UNAVAILABLE: 'Mentor feedback is temporarily unavailable.',
  REQUEST_REJECTED: 'The connection request was rejected. Reconnect or sign in again.',
}

// Never show a proxy body, server exception, source buffer or database message.
export function apiErrorMessage(error) {
  const code = error?.response?.data?.code ?? error?.code
  if (Object.hasOwn(messages, code)) return messages[code]
  const status = error?.response?.status
  if (status === 401) return messages.AUTHENTICATION_REQUIRED
  if (status === 403) return messages.FORBIDDEN
  if (status === 409) return messages.CONFLICT
  if (status === 429) return messages.CAPACITY_EXCEEDED
  if (error?.code === 'ERR_NETWORK' || !error?.response) return 'Could not reach the service. Check your connection and try again.'
  return 'The request could not be completed. Please try again.'
}
