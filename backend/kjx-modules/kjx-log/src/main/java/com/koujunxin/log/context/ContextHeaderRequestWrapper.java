package com.koujunxin.log.context;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

import com.koujunxin.log.api.port.RequestContextPort;
import com.koujunxin.log.envelope.StructuredLogFields;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * Makes generated correlation IDs visible to downstream {@code @RequestHeader} bindings.
 */
final class ContextHeaderRequestWrapper extends HttpServletRequestWrapper {

    private final RequestContextPort.RequestContextSnapshot context;

    ContextHeaderRequestWrapper(
            HttpServletRequest request,
            RequestContextPort.RequestContextSnapshot context) {
        super(request);
        this.context = context;
    }

    @Override
    public String getHeader(String name) {
        if (StructuredLogFields.HEADER_REQUEST_ID.equalsIgnoreCase(name)) {
            return context.requestId();
        }
        if (StructuredLogFields.HEADER_TRACE_ID.equalsIgnoreCase(name)) {
            return context.traceId();
        }
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        String value = getHeader(name);
        if (StructuredLogFields.HEADER_REQUEST_ID.equalsIgnoreCase(name)
                || StructuredLogFields.HEADER_TRACE_ID.equalsIgnoreCase(name)) {
            return value == null
                    ? Collections.emptyEnumeration()
                    : Collections.enumeration(Collections.singleton(value));
        }
        return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Set<String> names = new LinkedHashSet<>();
        Enumeration<String> existing = super.getHeaderNames();
        while (existing != null && existing.hasMoreElements()) {
            names.add(existing.nextElement());
        }
        names.add(StructuredLogFields.HEADER_REQUEST_ID);
        names.add(StructuredLogFields.HEADER_TRACE_ID);
        return Collections.enumeration(names);
    }
}
