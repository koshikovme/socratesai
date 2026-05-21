package com.masters.socratesai.analyzer.engine;

import com.masters.socratesai.analyzer.model.SyntaxCheckResult;
import org.springframework.stereotype.Component;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SyntaxCheckEngine {

    private static final Pattern PUBLIC_CLASS_PATTERN = Pattern.compile("\\bpublic\\s+class\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern CLASS_PATTERN = Pattern.compile("\\bclass\\s+([A-Za-z_$][\\w$]*)");

    public SyntaxCheckResult check(String code, String language) {
        SyntaxCheckResult result = new SyntaxCheckResult();

        if (code == null || code.isBlank()) {
            result.setCompileSuccess(false);
            result.setSuspiciousRegion("empty editor");
            return result;
        }

        if (!"java".equalsIgnoreCase(language)) {
            result.setCompileSuccess(true);
            return result;
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return fallbackCheck(code);
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager standardFileManager = compiler.getStandardFileManager(
                diagnostics,
                Locale.ROOT,
                StandardCharsets.UTF_8
        );
             MemoryJavaFileManager fileManager = new MemoryJavaFileManager(standardFileManager)) {

            String className = classNameFor(code);
            String source = toCompilationUnit(code, className);
            JavaFileObject sourceFile = new SourceJavaFileObject(className, source);

            compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    List.of("-proc:none", "-Xlint:none"),
                    null,
                    List.of(sourceFile)
            ).call();

            Diagnostic<? extends JavaFileObject> syntaxDiagnostic = diagnostics.getDiagnostics().stream()
                    .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                    .filter(this::isSyntaxDiagnostic)
                    .findFirst()
                    .orElse(null);

            if (syntaxDiagnostic != null) {
                result.setCompileSuccess(false);
                result.setSuspiciousRegion(toSuspiciousRegion(syntaxDiagnostic));
                return result;
            }

            result.setCompileSuccess(true);
            return result;
        } catch (IOException ex) {
            return fallbackCheck(code);
        }
    }

    private SyntaxCheckResult fallbackCheck(String code) {
        SyntaxCheckResult result = new SyntaxCheckResult();
        if (!code.contains(";") && !code.contains("class")) {
            result.setCompileSuccess(false);
            result.setSuspiciousRegion("statement ending");
            return result;
        }
        result.setCompileSuccess(true);
        return result;
    }

    private String classNameFor(String code) {
        Matcher publicClass = PUBLIC_CLASS_PATTERN.matcher(code);
        if (publicClass.find()) {
            return publicClass.group(1);
        }

        Matcher anyClass = CLASS_PATTERN.matcher(code);
        if (anyClass.find()) {
            return anyClass.group(1);
        }

        return "SocratesSnippet";
    }

    private String toCompilationUnit(String code, String className) {
        if (CLASS_PATTERN.matcher(code).find()) {
            return code;
        }

        return "class " + className + " { void __socratesSnippet() { " + code + " } }";
    }

    private boolean isSyntaxDiagnostic(Diagnostic<? extends JavaFileObject> diagnostic) {
        String message = diagnostic.getMessage(Locale.ROOT).toLowerCase(Locale.ROOT);
        return message.contains("';' expected")
                || message.contains("')' expected")
                || message.contains("'(' expected")
                || message.contains("']' expected")
                || message.contains("'[' expected")
                || message.contains("'{' expected")
                || message.contains("'}' expected")
                || message.contains("illegal start")
                || message.contains("reached end of file while parsing")
                || message.contains("not a statement")
                || message.contains("else without if")
                || message.contains("orphaned")
                || message.contains("class, interface, enum, or record expected");
    }

    private String toSuspiciousRegion(Diagnostic<? extends JavaFileObject> diagnostic) {
        String message = diagnostic.getMessage(Locale.ROOT).toLowerCase(Locale.ROOT);
        if (message.contains("';' expected")) {
            return "statement ending";
        }
        if (diagnostic.getLineNumber() > 0 && diagnostic.getColumnNumber() > 0) {
            return "line " + diagnostic.getLineNumber() + ", column " + diagnostic.getColumnNumber();
        }
        return "syntax near current edit";
    }

    private static class SourceJavaFileObject extends SimpleJavaFileObject {
        private final String source;

        SourceJavaFileObject(String className, String source) {
            super(URI.create("string:///" + className + JavaFileObject.Kind.SOURCE.extension), JavaFileObject.Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    private static class MemoryJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> {
        MemoryJavaFileManager(JavaFileManager fileManager) {
            super(fileManager);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(
                Location location,
                String className,
                JavaFileObject.Kind kind,
                FileObject sibling
        ) {
            return new SimpleJavaFileObject(
                    URI.create("bytes:///" + className + kind.extension),
                    kind
            ) {
                @Override
                public OutputStream openOutputStream() {
                    return new ByteArrayOutputStream();
                }
            };
        }
    }
}
