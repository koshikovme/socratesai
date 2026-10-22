export const blankProblemDraft = () => ({
  lockVersion: 0, title: '', topic: '', difficulty: 'EASY', statement: '', inputFormat: '', outputFormat: '',
  constraintsText: '', comparisonMode: 'IGNORE_TRAILING_LINE_ENDINGS', visibility: 'AUTHENTICATED', languages: [], tests: [],
})

export const languageDefaults = (languageKey) => ({
  languageKey, starterCode: '', timeLimitMs: 2000, wallTimeLimitMs: 5000, memoryLimitKb: 262144, outputLimitBytes: 65536,
})

export function problemDraft(response) {
  const version = response.version
  return {
    ...blankProblemDraft(), lockVersion: response.lockVersion, visibility: response.visibility,
    ...Object.fromEntries(['title', 'topic', 'difficulty', 'statement', 'inputFormat', 'outputFormat', 'constraintsText', 'comparisonMode'].map((key) => [key, version[key]])),
    languages: version.languages.map((language) => Object.fromEntries(Object.keys(languageDefaults(language.languageKey)).map((key) => [key, language[key] ?? '']))),
    tests: version.tests.map(({ inputData, expectedOutput, hidden }, position) => ({ position, inputData, expectedOutput, hidden })),
  }
}

export function problemRequest(form) {
  return { ...form, tests: form.tests.map((test, position) => ({ ...test, position })) }
}

export function verificationAllowsPublish(verification, problemVersionId, dirty, now = Date.now()) {
  return !dirty && verification?.problemVersionId === problemVersionId && verification.status === 'FINISHED'
    && verification.verdict === 'ACCEPTED' && verification.testsTotal > 0 && verification.testsPassed === verification.testsTotal
    && Date.parse(verification.validUntil) > now
}

export function validateProblemDraft(form) {
  if (!form.title.trim() || !form.topic.trim() || !form.statement.trim()) return 'Fill in the title, topic and statement.'
  if (form.languages.some((language) => language.wallTimeLimitMs < language.timeLimitMs)) return 'Wall time must be at least the CPU time limit.'
  if (new Set(form.languages.map((language) => language.languageKey)).size !== form.languages.length) return 'Choose each language only once.'
  if (form.tests.some((test) => new TextEncoder().encode(test.inputData).length > 65536 || new TextEncoder().encode(test.expectedOutput).length > 65536)) return 'Each test input and output must fit within 65,536 UTF-8 bytes.'
  return ''
}

export const blankCollectionDraft = () => ({ lockVersion: 0, title: '', description: '', visibility: 'AUTHENTICATED', problems: [] })
export const collectionDraft = (response) => ({
  lockVersion: response.lockVersion, title: response.title, description: response.description,
  visibility: response.visibility, problems: response.problems.map((item) => ({ ...item })),
})
export const collectionRequest = (form) => ({
  ...form, problems: form.problems.map(({ problemId, problemVersionId }) => ({ problemId, problemVersionId })),
})
