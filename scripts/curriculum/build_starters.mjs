import fs from 'node:fs'
import { fileURLToPath } from 'node:url'
import { createStarterCode } from '../../socratesai-frontend/src/services/starterCodeService.js'

const flag = process.argv.indexOf('--output')
if (flag < 0 || !process.argv[flag + 1]) throw Error('Provide a protected --output JSON path')
const curriculum = JSON.parse(fs.readFileSync(fileURLToPath(new URL('../../content/basics/problems.json', import.meta.url)), 'utf8'))
const output = Object.fromEntries(curriculum.problems.map(problem => [problem.key,
  Object.fromEntries(curriculum.languages.map(language => [language, createStarterCode(language, problem.contract)])),
]))
fs.writeFileSync(process.argv[flag + 1], JSON.stringify(output, null, 2), { mode: 0o600 })
console.log(`Generated ${curriculum.problems.length * curriculum.languages.length} full-program starters; no code was executed`)
