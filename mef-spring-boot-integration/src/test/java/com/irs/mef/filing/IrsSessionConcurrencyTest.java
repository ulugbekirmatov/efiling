package com.irs.mef.filing;

import com.irs.mef.config.MefSdkConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IrsSessionConcurrencyTest {

    @Test
    void twoThreadsShareOneLoginAndNeverOverlap() throws Exception {
        AtomicInteger logins = new AtomicInteger();
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        IrsSession session = session(logins);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = pool.submit(work(session, ready, start, inFlight, maxInFlight));
            Future<?> second = pool.submit(work(session, ready, start, inFlight, maxInFlight));
            assertEquals(true, ready.await(2, TimeUnit.SECONDS));
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, logins.get());
        assertEquals(1, maxInFlight.get());
    }

    @Test
    void aFailedCallDoesNotLogInAgain() {
        AtomicInteger logins = new AtomicInteger();
        IrsSession session = session(logins);

        assertThrows(IllegalStateException.class, () -> session.call(ctx -> {
            throw new IllegalStateException("boom");
        }));
        assertEquals("ok", session.call(ctx -> "ok"));

        assertEquals(1, logins.get());
    }

    @Test
    void aGetRequestDoesNotLogIn() {
        AtomicInteger logins = new AtomicInteger();
        IrsSession session = session(logins);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mef/session");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            IllegalStateException thrown = assertThrows(IllegalStateException.class,
                    () -> session.call(ctx -> "no"));
            assertEquals("A GET request cannot reach IRS", thrown.getMessage());
            assertEquals(0, logins.get());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private static IrsSession session(AtomicInteger logins) {
        SessionLogin login = () -> {
            logins.incrementAndGet();
            return new SessionLogin.Opened(new Object(), "sid");
        };
        return new IrsSession(login, new MefSdkConfig());
    }

    private static Runnable work(IrsSession session, CountDownLatch ready, CountDownLatch start,
                                 AtomicInteger inFlight, AtomicInteger maxInFlight) {
        return () -> {
            ready.countDown();
            try {
                if (!start.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("start timed out");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            session.call(ctx -> {
                int now = inFlight.incrementAndGet();
                maxInFlight.accumulateAndGet(now, Math::max);
                try {
                    Thread.sleep(40);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                } finally {
                    inFlight.decrementAndGet();
                }
                return null;
            });
        };
    }
}
