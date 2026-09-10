package com.facecook.common.session;

import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && parameter.getParameterType() == AuthenticatedUser.class;
    }

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
