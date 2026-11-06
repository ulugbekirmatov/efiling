package com.irs.mef.live;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test class that calls a third-party sandbox (not IRS). It shares the arming rule of
 * {@link LiveIrsTest}: it runs only when {@code -Dmef.live.test} names this exact class.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(LiveIrsTestCondition.class)
public @interface LiveSandboxTest {
}
