package io.xstefank.wildfly.bot.model;

public class RuntimeConstants {

    public static final String CONFIG_FILE_NAME = "wildfly-bot.yml";

    public static final String DEFAULT_COMMIT_MESSAGE = "None of the commit messages satisfy the following regex pattern: [%s]";

    public static final String DEFAULT_TITLE_MESSAGE = "Wrong content of the title. It does not satisfy the following regex pattern: [%s]";

    public static final String DEFAULT_PROJECT_KEY = "WFLY";

    public static final String LABEL_NEEDS_REBASE = "rebase-this";

    public static final String DEPENDABOT = "dependabot[bot]";

    public static final String PROJECT_PATTERN_REGEX = "%s-\\d+";
}
