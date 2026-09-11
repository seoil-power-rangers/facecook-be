package com.facecook.chat.websocket;

import com.facecook.auth.entity.User;
import com.facecook.auth.entity.UserStatus;
import com.facecook.auth.repository.UserRepository;
import com.facecook.common.exception.ErrorCode;
import com.facecook.common.session.AuthenticatedUser;
import com.facecook.common.session.SessionCookieService;
import com.facecook.common.session.SessionToken;
import com.facecook.common.session.SessionTokenSigner;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    private final SessionTokenSigner signer;
    private final SessionCookieService cookieService;
    private final UserRepository userRepository;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        Optional<String> rawToken = readCookie(
                servletRequest.getServletRequest(),
                cookieService.cookieName()
        );
        if (rawToken.isEmpty()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        Optional<SessionToken> sessionToken = signer.verify(rawToken.get());
        if (sessionToken.isEmpty()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        Optional<User> user = userRepository.findById(sessionToken.get().userId());
        if (user.isEmpty()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        if (user.get().getStatus() == UserStatus.SUSPENDED) {
            response.setStatusCode(ErrorCode.SUSPENDED.getStatus());
            return false;
        }

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                user.get().getId(),
                user.get().getEmail(),
                user.get().getRole()
        );
        attributes.put(ChatSessionAttributes.SESSION_TOKEN, rawToken.get());
        attributes.put(ChatSessionAttributes.AUTHENTICATED_USER, authenticatedUser);
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        // no-op
    }

    private Optional<String> readCookie(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return Optional.ofNullable(cookie.getValue());
            }
        }
        return Optional.empty();
    }
}
