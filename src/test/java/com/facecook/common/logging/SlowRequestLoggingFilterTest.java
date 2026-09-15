package com.facecook.common.logging;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlowRequestLoggingFilterTest {

    private final SlowRequestLoggingFilter filter = new SlowRequestLoggingFilter(500);

    @Test
    void passesRequestThroughToTheChain() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/profiles");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isEqualTo(request);
        assertThat(chain.getResponse()).isEqualTo(response);
    }

    @Test
    void propagatesExceptionsFromTheChain() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/profiles");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                throw new IllegalStateException("boom");
            }
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }
}
