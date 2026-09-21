package com.facecook.cook.controller;

import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.common.session.AuthenticatedUser;
import com.facecook.common.session.CurrentUser;
import com.facecook.cook.config.CookProperties;
import com.facecook.cook.dto.CookListResponse;
import com.facecook.cook.dto.SendCookRequest;
import com.facecook.cook.dto.SendCookResponse;
import com.facecook.cook.service.CookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cooks")
public class CookController {

    /** 새 FE가 거절 요청에 붙이는 헤더. 이 헤더가 없는 요청은 이전 화면이 보낸 것으로 본다. */
    public static final String REJECT_CONTRACT_HEADER = "X-Cook-Reject-Contract";
    public static final String REJECT_CONTRACT_VERSION = "1";

    private final CookService cookService;
    private final CookProperties cookProperties;

    @PostMapping
    public ResponseEntity<SendCookResponse> send(
            @CurrentUser AuthenticatedUser currentUser,
            @Valid @RequestBody SendCookRequest request
    ) {
        return ResponseEntity.ok(cookService.send(currentUser.userId(), request));
    }

    @GetMapping
    public ResponseEntity<CookListResponse> getCooks(@CurrentUser AuthenticatedUser currentUser) {
        return ResponseEntity.ok(cookService.getCooks(currentUser.userId()));
    }

    @DeleteMapping("/{cookId}")
    public ResponseEntity<Void> cancel(
            @CurrentUser AuthenticatedUser currentUser,
            @PathVariable Long cookId
    ) {
        cookService.cancel(currentUser.userId(), cookId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 받은 콕을 거절한다.
     *
     * <p>거절 API가 꺼져 있거나 {@value #REJECT_CONTRACT_HEADER} 헤더가 없으면 콕 상태나 호출자와 무관하게
     * 없는 경로처럼 {@code NOT_FOUND}로 응답하고 아무것도 기록하지 않는다. 배포 전부터 열려 있던 이전 화면은
     * 서버에 거절이 기록된다는 안내 없이 같은 요청을 보내므로, 새 화면의 요청과 구분하기 위해서다.
     * 인증은 그 앞의 인터셉터가 처리한다.</p>
     */
    @PostMapping("/{cookId}/reject")
    public ResponseEntity<Void> reject(
            @CurrentUser AuthenticatedUser currentUser,
            @PathVariable Long cookId,
            @RequestHeader(name = REJECT_CONTRACT_HEADER, required = false) String rejectContract
    ) {
        if (!cookProperties.rejectEnabled() || !REJECT_CONTRACT_VERSION.equals(rejectContract)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        cookService.reject(currentUser.userId(), cookId);
        return ResponseEntity.noContent().build();
    }
}
