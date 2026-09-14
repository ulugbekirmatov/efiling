package com.irs.mef.inspector.capture;

import com.irs.mef.inspector.ring.MimeRequest;
import com.irs.mef.inspector.ring.SoapCapture;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * House reflection: do not import gov.irs.* or compile against the SDK's javax/jakarta
 * SOAPHandler as a Spring MVC concern.
 *
 * Runtime steps:
 *   1. Prefer wrapping Service.setHandlerResolver so the SDK's own invoke-time chain
 *      (XWSS first) still runs; our handler is appended LAST (post-XWSS MIME).
 *   2. If the client already is a BindingProvider (tests), append to getHandlerChain.
 *   3. NEVER call getBindingProvider() on a fresh SendSubmissionsClient — that creates
 *      the port before the SDK installs XWSS.
 *   4. handler.handleMessage: outbound SOAPMessage → MimeMessageReader; inbound → redacted SOAP.
 *      NEVER message.writeTo(whole).
 *
 * If install fails: session keeps SoapCapture.Missing("handler not attached") and still stores Return XML.
 */
@Slf4j
public final class ReflectiveMimeTap {

    interface InspectorSoapHandler {
    }

    private final long maxXmlBytes;
    private volatile CaptureSession session;
    private volatile Object handlerProxy;

    public ReflectiveMimeTap() {
        this(MimeMessageReader.DEFAULT_MAX_XML_BYTES);
    }

    public ReflectiveMimeTap(long maxXmlBytes) {
        this.maxXmlBytes = maxXmlBytes;
    }

    public void install(Object sendSubmissionsClient, CaptureSession captureSession) {
        this.session = captureSession;
        if (sendSubmissionsClient == null) {
            return;
        }
        Object handler = handler();
        boolean hooked = false;
        hooked |= hookServiceResolver(sendSubmissionsClient, handler);
        hooked |= appendIfBindingProvider(sendSubmissionsClient, handler);
        hooked |= appendIfPortFieldPresent(sendSubmissionsClient, handler);
        if (!hooked) {
            log.warn("inspector: handler not attached (no Service resolver hook and no BindingProvider)");
        }
    }

    public MimeRequest takeRequest() {
        CaptureSession current = session;
        if (current == null) {
            return new MimeRequest("", new SoapCapture.Missing("handler not attached"), List.of());
        }
        return current.takeRequest();
    }

    public SoapCapture takeResponse() {
        CaptureSession current = session;
        if (current == null) {
            return new SoapCapture.Missing("no inbound SOAP");
        }
        return current.takeResponse();
    }

    void release(CaptureSession captureSession) {
        if (this.session == captureSession) {
            this.session = null;
        }
    }

    private Object handler() {
        Object existing = handlerProxy;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (handlerProxy == null) {
                handlerProxy = newHandlerProxy();
            }
            return handlerProxy;
        }
    }

    private Object newHandlerProxy() {
        Class<?> soapHandler = firstLoad(
                "jakarta.xml.ws.handler.soap.SOAPHandler",
                "javax.xml.ws.handler.soap.SOAPHandler");
        Class<?> handler = firstLoad(
                "jakarta.xml.ws.handler.Handler",
                "javax.xml.ws.handler.Handler");
        List<Class<?>> interfaces = new ArrayList<>();
        interfaces.add(InspectorSoapHandler.class);
        if (soapHandler != null) {
            interfaces.add(soapHandler);
        } else if (handler != null) {
            interfaces.add(handler);
        }
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = getClass().getClassLoader();
        }
        return Proxy.newProxyInstance(loader, interfaces.toArray(Class<?>[]::new), new HandlerDispatch());
    }

    private boolean hookServiceResolver(Object client, Object handler) {
        try {
            Object service = invokeNoArg(client, "getService");
            if (service == null) {
                service = findServiceField(client);
            }
            if (service == null) {
                return false;
            }
            if (service instanceof InspectorServiceHook) {
                return true;
            }
            Object hooked = wrapService(service, handler);
            if (hooked == service) {
                return false;
            }
            return replaceServiceField(client, service, hooked);
        } catch (Exception e) {
            log.warn("inspector: cannot wrap Service.setHandlerResolver: {}", e.toString());
            return false;
        }
    }

    private Object wrapService(Object service, Object handler) {
        try {
            ProxyFactory factory = new ProxyFactory();
            factory.setTarget(service);
            factory.setProxyTargetClass(true);
            factory.setOpaque(true);
            factory.addInterface(InspectorServiceHook.class);
            factory.addAdvice((MethodInterceptor) invocation -> {
                Method method = invocation.getMethod();
                Object[] args = invocation.getArguments();
                if ("setHandlerResolver".equals(method.getName()) && args != null && args.length == 1) {
                    args[0] = wrapResolver(args[0], handler);
                }
                return invocation.proceed();
            });
            return factory.getProxy(service.getClass().getClassLoader());
        } catch (Exception e) {
            log.warn("inspector: CGLIB Service wrap failed: {}", e.toString());
            return service;
        }
    }

    private Object wrapResolver(Object resolver, Object handler) {
        Class<?> resolverType = firstLoad(
                "jakarta.xml.ws.handler.HandlerResolver",
                "javax.xml.ws.handler.HandlerResolver");
        if (resolverType == null) {
            return resolver;
        }
        ClassLoader loader = resolverType.getClassLoader();
        if (loader == null) {
            loader = getClass().getClassLoader();
        }
        return Proxy.newProxyInstance(loader, new Class<?>[]{resolverType}, (proxy, method, args) -> {
            if ("getHandlerChain".equals(method.getName())) {
                List<Object> chain = new ArrayList<>();
                if (resolver != null) {
                    Object inner = method.invoke(resolver, args);
                    if (inner instanceof Collection<?> collection) {
                        chain.addAll(collection);
                    }
                }
                appendOnce(chain, handler);
                return chain;
            }
            if (resolver != null) {
                return method.invoke(resolver, args);
            }
            if (method.getReturnType() == boolean.class) {
                return false;
            }
            if (method.getReturnType() == int.class) {
                return 0;
            }
            return null;
        });
    }

    private boolean appendIfBindingProvider(Object client, Object handler) {
        if (!isBindingProvider(client)) {
            return false;
        }
        return appendToChain(client, handler);
    }

    private boolean appendIfPortFieldPresent(Object client, Object handler) {
        try {
            Object port = readFieldNamed(client, "myPort");
            if (port == null || port == client) {
                return false;
            }
            if (!isBindingProvider(port)) {
                return false;
            }
            return appendToChain(port, handler);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean appendToChain(Object bindingProvider, Object handler) {
        try {
            Object binding = invokeNoArg(bindingProvider, "getBinding");
            if (binding == null) {
                return false;
            }
            Object existing = invokeNoArg(binding, "getHandlerChain");
            List<Object> chain = new ArrayList<>();
            if (existing instanceof Collection<?> collection) {
                chain.addAll(collection);
            }
            if (containsOurs(chain)) {
                return true;
            }
            chain.add(handler);
            Method set = findMethod(binding.getClass(), "setHandlerChain", List.class);
            if (set == null) {
                return false;
            }
            set.setAccessible(true);
            set.invoke(binding, chain);
            return true;
        } catch (Exception e) {
            log.warn("inspector: setHandlerChain failed: {}", e.toString());
            return false;
        }
    }

    private void appendOnce(List<Object> chain, Object handler) {
        if (!containsOurs(chain)) {
            chain.add(handler);
        }
    }

    private static boolean containsOurs(List<?> chain) {
        for (Object item : chain) {
            if (item instanceof InspectorSoapHandler) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBindingProvider(Object object) {
        return object != null && (instanceOf(object, "jakarta.xml.ws.BindingProvider")
                || instanceOf(object, "javax.xml.ws.BindingProvider"));
    }

    private static boolean instanceOf(Object object, String className) {
        try {
            return Class.forName(className).isInstance(object);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static Class<?> firstLoad(String... names) {
        for (String name : names) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException ignored) {
                // try next
            }
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String name) throws Exception {
        Method method = findMethod(target.getClass(), name);
        if (method == null) {
            return null;
        }
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, params);
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Object findServiceField(Object client) {
        for (Class<?> type = client.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(client);
                    if (value != null && (instanceOf(value, "jakarta.xml.ws.Service")
                            || instanceOf(value, "javax.xml.ws.Service"))) {
                        return value;
                    }
                } catch (Exception ignored) {
                    // next field
                }
            }
        }
        return null;
    }

    private static boolean replaceServiceField(Object client, Object original, Object replacement) {
        for (Class<?> type = client.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    if (field.get(client) == original) {
                        field.set(client, replacement);
                        return true;
                    }
                } catch (Exception ignored) {
                    // next
                }
            }
        }
        return false;
    }

    private static Object readFieldNamed(Object client, String name) {
        for (Class<?> type = client.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(client);
            } catch (Exception ignored) {
                // next
            }
        }
        return null;
    }

    private final class HandlerDispatch implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("equals".equals(name)) {
                return proxy == args[0];
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("toString".equals(name)) {
                return "InspectorSendSubmissionsHandler";
            }
            if ("getHeaders".equals(name)) {
                return Set.of();
            }
            if ("close".equals(name)) {
                return null;
            }
            if ("handleMessage".equals(name) || "handleFault".equals(name)) {
                try {
                    handle(args != null && args.length > 0 ? args[0] : null, "handleFault".equals(name));
                } catch (Exception e) {
                    log.warn("inspector: SOAP handler swallowed: {}", e.toString());
                }
                return Boolean.TRUE;
            }
            Class<?> returns = method.getReturnType();
            if (returns == boolean.class) {
                return Boolean.TRUE;
            }
            if (returns == void.class) {
                return null;
            }
            return null;
        }

        private void handle(Object context, boolean fault) {
            CaptureSession current = session;
            if (current == null || context == null) {
                return;
            }
            boolean outbound = isOutbound(context);
            Object message = soapMessage(context);
            if (message == null) {
                return;
            }
            if (outbound && !fault) {
                current.recordOutbound(MimeMessageReader.read(message, maxXmlBytes));
                return;
            }
            current.recordInbound(responseFrom(message));
        }

        private SoapCapture responseFrom(Object message) {
            MimeRequest request = MimeMessageReader.read(message, maxXmlBytes);
            return request.soapCapture();
        }

        private boolean isOutbound(Object context) {
            Object outbound = mapGet(context, "jakarta.xml.ws.handler.message.outbound");
            if (outbound == null) {
                outbound = mapGet(context, "javax.xml.ws.handler.message.outbound");
            }
            if (outbound == null) {
                outbound = mapGet(context, "jakarta.xml.ws.handler.MessageContext.MESSAGE_OUTBOUND_PROPERTY");
            }
            return Boolean.TRUE.equals(outbound);
        }

        private Object soapMessage(Object context) {
            try {
                Object message = invokeNoArg(context, "getMessage");
                if (message != null) {
                    return message;
                }
            } catch (Exception ignored) {
                // fall through
            }
            return mapGet(context, "jakarta.xml.ws.handler.MessageContext.MESSAGE");
        }

        private Object mapGet(Object context, String key) {
            try {
                Method get = findMethod(context.getClass(), "get", Object.class);
                if (get == null) {
                    return null;
                }
                get.setAccessible(true);
                return get.invoke(context, key);
            } catch (Exception e) {
                return null;
            }
        }
    }

    /** Marker on the CGLIB Service wrapper so attach is idempotent. */
    public interface InspectorServiceHook {
    }
}
