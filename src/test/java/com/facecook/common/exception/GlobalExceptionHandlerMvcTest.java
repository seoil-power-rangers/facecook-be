package com.facecook.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 요청 형식 오류가 catch-all(500)에 걸리지 않고 원래 HTTP 의미로 응답되는지
 * 실제 MVC 디스패치 경로로 확인한다. 핸들러 메서드를 직접 부르면 어떤 예외가
 * 어느 핸들러로 가는지(catch-all보다 먼저 잡히는지)를 확인할 수 없다.
 */
class GlobalExceptionHandlerMvcTest {

    @RestController
    static class SampleController {

        @GetMapping("/sample")
        String get(@RequestParam int limit) {
            return "ok";
        }

        @PostMapping(value = "/sample", consumes = MediaType.APPLICATION_JSON_VALUE)
        String post(@RequestBody Map<String, Object> body) {
            return "ok";
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SampleController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void typeMismatchIsBadRequest() throws Exception {
        mockMvc.perform(get("/sample").param("limit", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void missingRequiredParameterIsBadRequest() throws Exception {
        mockMvc.perform(get("/sample"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void unsupportedMethodIsMethodNotAllowedWithAllowHeader() throws Exception {
        mockMvc.perform(put("/sample"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(header().string("Allow", containsString("POST")));
    }

    @Test
    void unsupportedMediaTypeIsUnsupportedWithAcceptHeader() throws Exception {
        mockMvc.perform(post("/sample").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string("Accept", containsString("application/json")));
    }

    @Test
    void unexpectedErrorsStillMapToInternalError() throws Exception {
        MockMvc failing = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        failing.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    @RestController
    static class FailingController {

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("boom");
        }
    }
}
