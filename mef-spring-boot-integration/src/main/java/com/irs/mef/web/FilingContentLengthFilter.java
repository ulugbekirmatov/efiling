package com.irs.mef.web;

import com.irs.mef.filing.ClientFiling;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * The yml form and multipart caps do not apply to a JSON body. A declared Content-Length
 * over the client-body cap is refused before the body is read. A request with no
 * Content-Length (chunked) is left to Jackson's string cap.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
class FilingContentLengthFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = pathWithinApplication(request);
        return !"/mef/filings".equals(path) && !path.startsWith("/mef/filings/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.CONTENT_LENGTH);
        if (header != null && !header.isBlank()) {
            long length = parseLength(header);
            if (length > ClientFiling.MAX_CLIENT_BODY_CHARS) {
                response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                response.getWriter().write(
                        "{\"status\":413,\"errorCode\":\"NEWSEND_BODY_TOO_LARGE\",\"message\":\"Content-Length exceeds "
                                + ClientFiling.MAX_CLIENT_BODY_CHARS + " bytes\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** A non-numeric Content-Length is the container's problem. This filter only caps a number. */
    private static long parseLength(String header) {
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "";
        }
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            return uri.substring(context.length());
        }
        return uri;
    }
}
