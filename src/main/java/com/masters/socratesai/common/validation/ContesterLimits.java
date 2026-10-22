package com.masters.socratesai.common.validation;

import lombok.experimental.UtilityClass;

/** Contract ceilings; operator/runtime limits may be stricter. */
@UtilityClass
public class ContesterLimits {
    public static final int SOURCE_CHARACTERS = 20000;
    public static final int SOURCE_BYTES = 4 * SOURCE_CHARACTERS;
    public static final int STATEMENT_CHARACTERS = 50000;
    public static final int TEST_DATA_CHARACTERS = 65536;
    public static final int TEST_CASES = 100;
    public static final int LANGUAGES = 10;
    public static final int COLLECTION_PROBLEMS = 200;
    public static final int TIME_LIMIT_MS = 30000;
    public static final int WALL_TIME_LIMIT_MS = 60000;
    public static final int MEMORY_LIMIT_KB = 1048576;
    public static final int OUTPUT_LIMIT_BYTES = 65536;
    public static final String LANGUAGE_KEY = "[a-z][a-z0-9_+\\-]{0,29}";
}
