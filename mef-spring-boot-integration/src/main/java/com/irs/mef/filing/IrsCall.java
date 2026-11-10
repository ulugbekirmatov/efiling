package com.irs.mef.filing;

@FunctionalInterface
interface IrsCall<T> {

    T run(Object serviceContext);
}
