import { loader } from '@guolao/vue-monaco-editor'
import * as monaco from 'monaco-editor/esm/vs/editor/editor.api'
import 'monaco-editor/esm/vs/basic-languages/java/java.contribution'
import 'monaco-editor/esm/vs/basic-languages/python/python.contribution'
import 'monaco-editor/esm/vs/basic-languages/cpp/cpp.contribution'
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker'

// Only the native runtime languages are loaded, with a same-origin bundled worker.
self.MonacoEnvironment = { getWorker: () => new editorWorker() }
loader.config({ monaco })
