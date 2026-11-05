import { describe, expect, test } from 'vitest'
import { createStarterCode, validateStarterContract } from '../starterCodeService'

const pair = { functionName: 'sum', returnType: 'INTEGER', parameters: [{ name: 'firstNumber', type: 'INTEGER' }, { name: 'secondNumber', type: 'INTEGER' }] }

describe('typed full-program starters', () => {
  test('gives Java learners real named inputs while keeping Scanner in main', () => {
    const source = createStarterCode('java', pair)
    expect(source).toContain('static int sum(int firstNumber, int secondNumber)')
    expect(source).toContain('System.out.println(sum(firstNumber, secondNumber));')
    expect(source).toContain('int firstNumber = _input.nextInt();')
    expect(source).toContain('int secondNumber = _input.nextInt();')
    expect(source).toContain('public static void main(String[] args)')
    expect(source).not.toContain('sum(Scanner')
    expect(source).not.toContain('firstNumber + secondNumber')
  })

  test('gives C++ learners the same clear function contract and a complete input adapter', () => {
    const source = createStarterCode('cpp17', pair)
    expect(source).toContain('int sum(int firstNumber, int secondNumber)')
    expect(source).toContain('std::cin >> firstNumber;')
    expect(source).toContain("std::cout << sum(firstNumber, secondNumber) << '\\n';")
    expect(source).not.toContain('firstNumber + secondNumber')
  })

  test.each(['java', 'cpp17', 'python3'])('handles arrays with an explicit length token and named long/text inputs in %s', language => {
    const source = createStarterCode(language, { functionName: 'total', returnType: 'LONG', parameters: [
      { name: 'numbers', type: 'INTEGER_ARRAY' }, { name: 'limit', type: 'LONG' }, { name: 'word', type: 'TEXT' },
    ] })
    expect(source).toContain('numbers')
    expect(source).toContain('_length0')
    expect(source).toContain('limit')
    expect(source).toContain('word')
    expect(source).toContain('TODO')
  })

  test.each(['java', 'cpp17', 'python3'])('supports a string return without inserting an answer in %s', language => {
    const source = createStarterCode(language, { functionName: 'reverseWord', returnType: 'TEXT', parameters: [{ name: 'word', type: 'TEXT' }] })
    expect(source).toContain('return ""')
    expect(source).toContain('reverseWord(')
    expect(source).not.toContain('reverse(')
  })

  test.each(['class', 'print', 'main', 'std', '_input', 'x); injected(); //', 'next', 'True'])('rejects invalid or adapter-conflicting names: %s', name => {
    expect(validateStarterContract({ ...pair, functionName: name })).not.toBe('')
  })

  test('rejects duplicate, reserved, conflicting, missing or excessive parameters and unknown types', () => {
    const invalid = [
      { ...pair, parameters: [{ name: 'sum', type: 'INTEGER' }] },
      { ...pair, parameters: [{ name: 'class', type: 'INTEGER' }] },
      { ...pair, parameters: [pair.parameters[0], pair.parameters[0]] },
      { ...pair, parameters: [] },
      { ...pair, parameters: Array.from({ length: 7 }, (_, i) => ({ name: `value${i}`, type: 'INTEGER' })) },
      { ...pair, returnType: 'untrusted-type' },
      { ...pair, parameters: [{ name: 'value', type: 'untrusted-type' }] },
    ]
    invalid.forEach(contract => expect(() => createStarterCode('java', contract)).toThrow())
    expect(() => createStarterCode('constructor', pair)).toThrow('supported runtime')
  })
})
