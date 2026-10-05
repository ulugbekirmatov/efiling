package com.irs.mef.live;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Enables a {@link LiveIrsTest} class only when {@value #PROPERTY} equals its simple name. */
public final class LiveIrsTestCondition implements ExecutionCondition {

    public static final String PROPERTY = "mef.live.test";

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        String testClass = context.getRequiredTestClass().getSimpleName();
        if (testClass.equals(System.getProperty(PROPERTY))) {
            return ConditionEvaluationResult.enabled("armed by -D" + PROPERTY + "=" + testClass);
        }
        return ConditionEvaluationResult.disabled(
                "logs in to IRS; run alone with -D" + PROPERTY + "=" + testClass);
    }
}
