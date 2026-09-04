package com.facecook.auth.mail;

import com.facecook.auth.dto.VerificationPurpose;

public interface AuthMailService {

    void sendVerificationCode(String email, String code, VerificationPurpose purpose);
}
