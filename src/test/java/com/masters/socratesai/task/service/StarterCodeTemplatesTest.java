package com.masters.socratesai.task.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StarterCodeTemplatesTest {
    @Test void blankNativeStartersContainRunnableEntryPointsAndAnUnsolvedBody() {
        assertThat(StarterCodeTemplates.resolve("java", "")).contains("public class Main", "public static void main(String[] args)", "solve(input)", "TODO")
                .doesNotContain("return a + b", "input.nextInt() + input.nextInt()");
        assertThat(StarterCodeTemplates.resolve("python3", null)).contains("def solve():", "if __name__ == \"__main__\":", "TODO");
        assertThat(StarterCodeTemplates.resolve("cpp17", "  ")).contains("int main()", "solve();", "TODO");
    }

    @Test void authoredCodeIsPreservedExactlyAndNoRuntimeIsInventedForAnUnknownLanguage() {
        String authored = "// Custom teaching scaffold\r\nclass Main { }";
        assertThat(StarterCodeTemplates.resolve("java", authored)).isEqualTo(authored);
        assertThat(StarterCodeTemplates.forLanguage("unconfigured")).isEmpty();
    }
}
