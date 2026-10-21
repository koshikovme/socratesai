package com.masters.socratesai.execution.domain;

import com.masters.socratesai.task.model.OutputComparison;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

class OutputComparatorTest {
    private final OutputComparator comparator = new OutputComparator();

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            EXACT | 'a  b' | 'a b' | false
            EXACT | '42' | '42' | true
            IGNORE_TRAILING_LINE_ENDINGS | 'x ' | 'x' | false
            IGNORE_TRAILING_LINE_ENDINGS | 'привет' | 'привет' | true
            """)
    void preservesMeaningfulCharacters(OutputComparison policy, String expected, String actual, boolean matches) {
        assertThat(comparator.matches(expected, actual, policy)).isEqualTo(matches);
    }

    @Test
    void normalizesCrLfButExactRetainsFinalNewlines() {
        assertThat(comparator.matches("a\nb\n", "a\r\nb\r\n", OutputComparison.EXACT)).isTrue();
        assertThat(comparator.matches("a", "a\n", OutputComparison.EXACT)).isFalse();
        assertThat(comparator.matches("", "", OutputComparison.EXACT)).isTrue();
    }

    @Test
    void ignoresOnlyTrailingLineEndings() {
        var policy = OutputComparison.IGNORE_TRAILING_LINE_ENDINGS;
        assertThat(comparator.matches("a\n", "a\r\n\r\n", policy)).isTrue();
        assertThat(comparator.matches("", "\n\r\n", policy)).isTrue();
        assertThat(comparator.matches("a\nb", "a\n\nb", policy)).isFalse();
        assertThat(comparator.matches("a", "a\r", policy)).isFalse();
        assertThat(comparator.matches("a", "a\t\n", policy)).isFalse();
        assertThat(comparator.matches("a ", "a \n", policy)).isTrue();
    }

    @Test
    void rejectsMissingValues() {
        assertThatNullPointerException().isThrownBy(() -> comparator.matches(null, "", OutputComparison.EXACT));
        assertThatNullPointerException().isThrownBy(() -> comparator.matches("", null, OutputComparison.EXACT));
        assertThatNullPointerException().isThrownBy(() -> comparator.matches("", "", null));
    }
}
