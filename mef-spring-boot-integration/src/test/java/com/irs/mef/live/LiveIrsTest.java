package com.irs.mef.live;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test class that logs in to IRS. It runs only when {@code -Dmef.live.test} names this
 * exact class, so one flag can never arm two live tests in the same run.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(LiveIrsTestCondition.class)
public @interface LiveIrsTest {
}
