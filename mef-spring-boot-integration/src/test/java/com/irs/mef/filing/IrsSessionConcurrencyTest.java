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
import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void callGivesUpBeforeTouchingTheContext() throws Exception {
        AtomicInteger logins = new AtomicInteger();
        AtomicInteger bodies = new AtomicInteger();
        FilingProperties properties = new FilingProperties();
        properties.setSessionWaitSeconds(1);
        IrsSession session = session(logins, properties);
        CountDownLatch inside = new CountDownLatch(1);
        Thread holder = new Thread(() -> session.call(ctx -> {
            inside.countDown();
            try {
                Thread.sleep(3_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
        holder.start();
        assertEquals(true, inside.await(2, TimeUnit.SECONDS));
        long started = System.nanoTime();
        assertThrows(FilingBusyException.class, () -> session.call(ctx -> {
            bodies.incrementAndGet();
            return null;
        }));
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        assertEquals(0, bodies.get());
        assertEquals(1, logins.get());
        assertTrue(elapsedMs < 2_500, "waited " + elapsedMs + "ms");
        holder.interrupt();
        holder.join(2_000);
    }

    private static IrsSession session(AtomicInteger logins) {
        return session(logins, new FilingProperties());
    }

    private static IrsSession session(AtomicInteger logins, FilingProperties properties) {
        SessionLogin login = () -> {
            logins.incrementAndGet();
            return new SessionLogin.Opened(new Object(), "sid");
        };
        return new IrsSession(login, new MefSdkConfig(),
                new IrsWire(Clock.systemUTC(), NoOpSendSubmissionsWireTap.INSTANCE),
                properties);
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
