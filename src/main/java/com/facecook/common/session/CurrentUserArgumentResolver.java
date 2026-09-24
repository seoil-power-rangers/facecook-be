package com.facecook.common.session;

import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 컨트롤러 파라미터의 {@code @CurrentUser AuthenticatedUser}를 채운다.
 * {@code WebConfig#addArgumentResolvers}에서 등록된다.
 *
 * <p>Spring MVC는 컨트롤러 메서드를 부르기 전에 파라미터마다 "이걸 채울 수 있는
 * resolver"를 찾는다. {@link #supportsParameter}가 true를 돌려준 파라미터는
 * {@link #resolveArgument}의 반환값으로 채워진다. 덕분에 컨트롤러는 쿠키나 요청
 * 속성을 직접 읽지 않고 파라미터 하나로 로그인 사용자를 받는다.</p>
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && parameter.getParameterType() == AuthenticatedUser.class;
    }

    /**
     * 세션 인터셉터가 요청 속성에 넣어 둔 {@link AuthenticatedUser}를 꺼낸다.
     *
     * <p>예외: {@code UNAUTHORIZED}(속성이 비어 있음 — 인터셉터가 적용되지 않는
     * 공개 경로에서 {@code @CurrentUser}를 쓴 경우).</p>
     */
    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        Object currentUser = webRequest.getAttribute(
                SessionAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE,
                NativeWebRequest.SCOPE_REQUEST
        );
        if (currentUser == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return currentUser;
    }
}
