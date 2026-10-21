package com.masters.socratesai.task.model;

/** Both policies normalize CRLF; the second also ignores final line endings only. */
public enum OutputComparison {
    EXACT, IGNORE_TRAILING_LINE_ENDINGS
}
