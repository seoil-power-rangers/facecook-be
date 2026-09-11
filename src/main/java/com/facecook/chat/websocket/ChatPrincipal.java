package com.facecook.chat.websocket;

import com.facecook.common.session.AuthenticatedUser;

import java.security.Principal;

public record ChatPrincipal(AuthenticatedUser user) implements Principal {

    @Override
    public String getName() {
        return user.userId().toString();
    }
}
