package com.irs.mef.filing;

/**
 * One SDK call. The argument is the current {@code ServiceContext}; the caller must not keep it.
 */
@FunctionalInterface
public interface IrsCall<T> {

    T run(Object serviceContext);
}
