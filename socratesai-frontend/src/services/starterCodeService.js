// Full stdin/stdout programs, never a local execution harness. Teachers define the input contract.
export const parameterTypes = ['INTEGER', 'LONG', 'TEXT', 'INTEGER_ARRAY']
export const returnTypes = ['INTEGER', 'LONG', 'TEXT']
export const blankStarterContract = () => ({ functionName: 'solve', returnType: 'INTEGER', parameters: [{ name: 'value', type: 'INTEGER' }] })

const reserved = new Set(('abstract assert auto boolean bool break byte case catch char class const constexpr continue default def delete do double else enum except extends false final finally float for friend from goto if implements import in inline instanceof int interface lambda long main Main native new None null nullptr package pass private protected public raise register return Scanner short signed sizeof static std strictfp String struct super switch synchronized template this throw throws transient true try typedef typename union unsigned using var virtual void volatile while with yield as and or not is nonlocal global async await elif del False True sys print iter next range str list operator noexcept thread_local typeid decltype static_assert wchar_t char16_t char32_t').split(' '))
const identifier = value => /^[a-zA-Z][a-zA-Z0-9_]{0,39}$/.test(value || '') && !reserved.has(value)

export function validateStarterContract(contract) {
  if (!identifier(contract?.functionName)) return 'Use a valid function name, starting with a letter and avoiding reserved words.'
  if (!returnTypes.includes(contract.returnType)) return 'Choose a supported return type.'
  if (!Array.isArray(contract.parameters) || !contract.parameters.length || contract.parameters.length > 6) return 'Add between one and six input parameters.'
  if (contract.parameters.some(parameter => !identifier(parameter.name) || !parameterTypes.includes(parameter.type))) return 'Use valid parameter names and supported types.'
  if (new Set(contract.parameters.map(parameter => parameter.name)).size !== contract.parameters.length) return 'Parameter names must be unique.'
  if (contract.parameters.some(parameter => parameter.name === contract.functionName)) return 'Use parameter names different from the function name.'
  return ''
}

const javaTypes = { INTEGER: 'int', LONG: 'long', TEXT: 'String', INTEGER_ARRAY: 'int[]' }
const cppTypes = { INTEGER: 'int', LONG: 'long long', TEXT: 'std::string', INTEGER_ARRAY: 'std::vector<int>' }
const pythonTypes = { INTEGER: 'int', LONG: 'int', TEXT: 'str', INTEGER_ARRAY: 'list[int]' }
const indent = (lines, spaces) => lines.map(line => ' '.repeat(spaces) + line).join('\n')

function java(contract) {
  const reads = contract.parameters.flatMap(({ name, type }, index) => type === 'INTEGER_ARRAY' ? [
    `int _length${index} = _input.nextInt();`, `int[] ${name} = new int[_length${index}];`,
    `for (int _index${index} = 0; _index${index} < _length${index}; _index${index}++) {`,
    `    ${name}[_index${index}] = _input.nextInt();`, '}',
  ] : [`${javaTypes[type]} ${name} = _input.${type === 'LONG' ? 'nextLong' : type === 'TEXT' ? 'next' : 'nextInt'}();`])
  const argumentsList = contract.parameters.map(parameter => parameter.name).join(', ')
  const parameters = contract.parameters.map(parameter => `${javaTypes[parameter.type]} ${parameter.name}`).join(', ')
  const placeholder = contract.returnType === 'TEXT' ? '""' : contract.returnType === 'LONG' ? '0L' : '0'
  return `import java.util.Scanner;\n\npublic class Main {\n    // Write your solution here. main handles reading inputs and printing the returned answer.\n    static ${javaTypes[contract.returnType]} ${contract.functionName}(${parameters}) {\n        // TODO: Replace the placeholder with your logic.\n        return ${placeholder};\n    }\n\n    // Input adapter: keep this part unless the problem uses a different input format.\n    public static void main(String[] args) {\n        Scanner _input = new Scanner(System.in);\n${indent(reads, 8)}\n        System.out.println(${contract.functionName}(${argumentsList}));\n    }\n}\n`
}

function cpp(contract) {
  const reads = contract.parameters.flatMap(({ name, type }, index) => type === 'INTEGER_ARRAY' ? [
    `int _length${index};`, `std::cin >> _length${index};`, `std::vector<int> ${name}(_length${index});`,
    `for (int& _value${index} : ${name}) std::cin >> _value${index};`,
  ] : [`${cppTypes[type]} ${name};`, `std::cin >> ${name};`])
  const argumentsList = contract.parameters.map(parameter => parameter.name).join(', ')
  const parameters = contract.parameters.map(parameter => `${cppTypes[parameter.type]} ${parameter.name}`).join(', ')
  return `#include <iostream>\n#include <string>\n#include <vector>\n\n// Write your solution here. main handles input and prints the returned answer.\n${cppTypes[contract.returnType]} ${contract.functionName}(${parameters}) {\n    // TODO: Replace the placeholder with your logic.\n    return ${contract.returnType === 'TEXT' ? '""' : '0'};\n}\n\nint main() {\n    std::ios::sync_with_stdio(false);\n    std::cin.tie(nullptr);\n${indent(reads, 4)}\n    std::cout << ${contract.functionName}(${argumentsList}) << '\\n';\n    return 0;\n}\n`
}

function python(contract) {
  const reads = contract.parameters.flatMap(({ name, type }, index) => type === 'INTEGER_ARRAY' ? [
    `_length${index} = int(next(_tokens))`, `${name} = [int(next(_tokens)) for _ in range(_length${index})]`,
  ] : [`${name} = ${type === 'TEXT' ? 'next(_tokens)' : 'int(next(_tokens))'}`])
  const argumentsList = contract.parameters.map(parameter => parameter.name).join(', ')
  const parameters = contract.parameters.map(parameter => `${parameter.name}: ${pythonTypes[parameter.type]}`).join(', ')
  return `import sys\n\n\ndef ${contract.functionName}(${parameters}) -> ${pythonTypes[contract.returnType]}:\n    # TODO: Replace the placeholder with your logic.\n    return ${contract.returnType === 'TEXT' ? '""' : '0'}\n\n\nif __name__ == "__main__":\n    _tokens = iter(sys.stdin.read().split())\n${indent(reads, 4)}\n    print(${contract.functionName}(${argumentsList}))\n`
}

export function createStarterCode(languageKey, contract) {
  const error = validateStarterContract(contract)
  if (error) throw new Error(error)
  const generators = { java, cpp17: cpp, python3: python }
  if (!Object.hasOwn(generators, languageKey)) throw new Error('Choose a supported runtime for the starter.')
  return generators[languageKey](contract)
}
