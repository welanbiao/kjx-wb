package com.koujunxin.log.context;

import java.io.IOException;

import org.slf4j.MDC;

import com.koujunxin.log.api.port.RequestContextPort;
import com.koujunxin.log.api.port.TracePropagationPort;
import com.koujunxin.log.envelope.StructuredLogFields;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 每个 HTTP 请求绑定 MDC，结束时清空，保证请求间不串上下文。
 */
public final class MdcRequestContextFilter extends OncePerRequestFilter {

    private final RequestContextPort requestContextPort;
    private final TracePropagationPort tracePropagationPort;

    public MdcRequestContextFilter(RequestContextPort requestContextPort, TracePropagationPort tracePropagationPort) {
        this.requestContextPort = requestContextPort;
        this.tracePropagationPort = tracePropagationPort;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        RequestContextPort.RequestContextSnapshot snapshot = tracePropagationPort.extract(request::getHeader);
        requestContextPort.bind(snapshot);
        try {
            response.setHeader(StructuredLogFields.HEADER_REQUEST_ID, snapshot.requestId());
            response.setHeader(StructuredLogFields.HEADER_TRACE_ID, snapshot.traceId());
            filterChain.doFilter(new ContextHeaderRequestWrapper(request, snapshot), response);
        } finally {
            requestContextPort.clear();
            MDC.clear();
        }
    }
}
