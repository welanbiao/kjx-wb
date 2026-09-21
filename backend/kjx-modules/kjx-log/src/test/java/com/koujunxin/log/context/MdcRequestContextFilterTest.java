package com.koujunxin.log.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.koujunxin.log.api.port.RequestContextPort;
import com.koujunxin.log.envelope.StructuredLogFields;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

class MdcRequestContextFilterTest {

    private final MdcRequestContextPort contextPort = new MdcRequestContextPort();
    private final HeaderTracePropagation propagation = new HeaderTracePropagation(contextPort);
    private final MdcRequestContextFilter filter = new MdcRequestContextFilter(contextPort, propagation);

    @AfterEach
    void clear() {
        contextPort.clear();
        MDC.clear();
    }

    @Test
    void sequentialRequestsDoNotLeakMdc() throws Exception {
        MockHttpServletRequest first = new MockHttpServletRequest();
        first.addHeader(StructuredLogFields.HEADER_REQUEST_ID, "req-a");
        first.addHeader(StructuredLogFields.HEADER_TRACE_ID, "trace-a");
        MockHttpServletResponse respA = new MockHttpServletResponse();
        CapturingChain chainA = new CapturingChain();
        filter.doFilter(first, respA, chainA);
        assertThat(chainA.requestId).isEqualTo("req-a");
        assertThat(chainA.traceId).isEqualTo("trace-a");
        assertThat(MDC.get(StructuredLogFields.REQUEST_ID)).isNull();
        assertThat(MDC.get(StructuredLogFields.TRACE_ID)).isNull();
        assertThat(contextPort.current().requestId()).isNull();

        MockHttpServletRequest second = new MockHttpServletRequest();
        second.addHeader(StructuredLogFields.HEADER_REQUEST_ID, "req-b");
        second.addHeader(StructuredLogFields.HEADER_TRACE_ID, "trace-b");
        CapturingChain chainB = new CapturingChain();
        filter.doFilter(second, new MockHttpServletResponse(), chainB);
        assertThat(chainB.requestId).isEqualTo("req-b");
        assertThat(chainB.traceId).isEqualTo("trace-b");
        assertThat(chainB.requestId).isNotEqualTo(chainA.requestId);
        assertThat(MDC.getCopyOfContextMap() == null || MDC.getCopyOfContextMap().isEmpty()).isTrue();
    }

    @Test
    void concurrentRequestsKeepIsolatedMdc() throws Exception {
        int n = 8;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        Map<String, String> seen = new ConcurrentHashMap<>();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String id = "req-" + i;
            Thread thread = new Thread(() -> {
                try {
                    start.await(5, TimeUnit.SECONDS);
                    MockHttpServletRequest request = new MockHttpServletRequest();
                    request.addHeader(StructuredLogFields.HEADER_REQUEST_ID, id);
                    request.addHeader(StructuredLogFields.HEADER_TRACE_ID, "trace-" + id);
                    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain() {
                        @Override
                        public void doFilter(ServletRequest request, ServletResponse response) {
                            seen.put(id, MDC.get(StructuredLogFields.REQUEST_ID));
                            assertThat(MDC.get(StructuredLogFields.TRACE_ID)).isEqualTo("trace-" + id);
                        }
                    });
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                } finally {
                    done.countDown();
                }
            });
            threads.add(thread);
            thread.start();
        }
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        for (Thread thread : threads) {
            thread.join(1000);
        }
        assertThat(seen).hasSize(n);
        seen.forEach((key, value) -> assertThat(value).isEqualTo(key));
        assertThat(MDC.get(StructuredLogFields.REQUEST_ID)).isNull();
    }

    @Test
    void missingHeadersAreGeneratedAndPropagatedOnResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();
        filter.doFilter(request, response, chain);
        assertThat(response.getHeader(StructuredLogFields.HEADER_REQUEST_ID)).isNotBlank();
        assertThat(response.getHeader(StructuredLogFields.HEADER_TRACE_ID)).isNotBlank();
        assertThat(chain.headerRequestId)
                .isEqualTo(response.getHeader(StructuredLogFields.HEADER_REQUEST_ID));
        assertThat(chain.headerTraceId)
                .isEqualTo(response.getHeader(StructuredLogFields.HEADER_TRACE_ID));
    }

    @Test
    void unsafeCorrelationHeadersAreReplacedBeforeEnteringMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(StructuredLogFields.HEADER_REQUEST_ID, "forged\nrequest");
        request.addHeader(StructuredLogFields.HEADER_TRACE_ID, "forged trace");
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.requestId).doesNotContain("\n").isNotEqualTo("forged\nrequest");
        assertThat(chain.traceId).doesNotContain(" ").isNotEqualTo("forged trace");
    }

    private static final class CapturingChain extends MockFilterChain {
        private String requestId;
        private String traceId;
        private String headerRequestId;
        private String headerTraceId;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            requestId = MDC.get(StructuredLogFields.REQUEST_ID);
            traceId = MDC.get(StructuredLogFields.TRACE_ID);
            HttpServletRequest http = (HttpServletRequest) request;
            headerRequestId = http.getHeader(StructuredLogFields.HEADER_REQUEST_ID);
            headerTraceId = http.getHeader(StructuredLogFields.HEADER_TRACE_ID);
        }
    }
}
