import { readdirSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import { parse } from '@vue/compiler-sfc'
import { baseParse } from '@vue/compiler-dom'
import { expect, test } from 'vitest'

test('screens keep visible static copy and accessible labels in the translation layer', () => {
  const missing = []
  for (const directory of ['components', 'views']) {
    const folder = resolve(dirname(fileURLToPath(import.meta.url)), '../..', directory)
    for (const name of readdirSync(folder).filter(file => file.endsWith('.vue'))) {
      const { descriptor } = parse(readFileSync(`${folder}/${name}`, 'utf8'))
      function visit(node, ignored = false) {
        const skip = ignored || ['code', 'pre', 'svg'].includes(node.tag)
        if (!skip && node.type === 2 && /[A-Za-z]/.test(node.content) && node.content.trim() !== 'SocratesAI') missing.push(`${name}: ${node.content.trim()}`)
        for (const prop of node.props || []) {
          if (prop.type === 6 && ['placeholder', 'aria-label', 'title'].includes(prop.name)) missing.push(`${name}: ${prop.name}`)
        }
        for (const child of node.children || []) visit(child, skip)
      }
      visit(baseParse(descriptor.template.content))
    }
  }
  expect(missing).toEqual([])
})
