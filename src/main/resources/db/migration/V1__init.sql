CREATE TABLE users (
    user_id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    email             VARCHAR(255) NOT NULL UNIQUE,
    role              VARCHAR(20)  NOT NULL DEFAULT 'participant',
    status            VARCHAR(20)  NOT NULL DEFAULT 'active',
    agreed_privacy_at DATETIME     NULL,
    agreed_terms_at   DATETIME     NULL,
    last_active_at    DATETIME     NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE profile (
    user_id      BIGINT PRIMARY KEY,
    nickname     VARCHAR(50)  NOT NULL,
    gender       VARCHAR(10)  NOT NULL,
    age          INT          NOT NULL,
    mbti         VARCHAR(4)   NOT NULL,
    hobby        VARCHAR(255) NOT NULL,
    blood_type   VARCHAR(5)   NOT NULL,
    department   VARCHAR(100) NULL,
    grade        VARCHAR(20)  NULL,
    bio          VARCHAR(500) NULL,
    photo_url    VARCHAR(500) NULL,
    ideal_type   VARCHAR(255) NULL,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);

CREATE TABLE match_info (
    match_id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_a_id           BIGINT NOT NULL,
    user_b_id           BIGINT NOT NULL,
    matched_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    current_step        INT NOT NULL DEFAULT 1,
    step1_completed_at  DATETIME NULL,
    step1_completed_by  BIGINT NULL,
    step2_completed_at  DATETIME NULL,
    step2_completed_by  BIGINT NULL,
    step3_completed_at  DATETIME NULL,
    step3_completed_by  BIGINT NULL,
    CONSTRAINT fk_match_user_a      FOREIGN KEY (user_a_id)           REFERENCES users (user_id),
    CONSTRAINT fk_match_user_b      FOREIGN KEY (user_b_id)           REFERENCES users (user_id),
    CONSTRAINT fk_match_step1_admin FOREIGN KEY (step1_completed_by)  REFERENCES users (user_id),
    CONSTRAINT fk_match_step2_admin FOREIGN KEY (step2_completed_by)  REFERENCES users (user_id),
    CONSTRAINT fk_match_step3_admin FOREIGN KEY (step3_completed_by)  REFERENCES users (user_id)
);

CREATE TABLE cook (
    cook_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    sender_id   BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    match_id    BIGINT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'pending',
    sent_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_cook_sender_receiver UNIQUE (sender_id, receiver_id),
    CONSTRAINT fk_cook_sender   FOREIGN KEY (sender_id)   REFERENCES users (user_id),
    CONSTRAINT fk_cook_receiver FOREIGN KEY (receiver_id) REFERENCES users (user_id),
    CONSTRAINT fk_cook_match    FOREIGN KEY (match_id)    REFERENCES match_info (match_id)
);

CREATE TABLE message (
    message_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    match_id            BIGINT NOT NULL,
    sender_id           BIGINT NOT NULL,
    content             VARCHAR(1000) NOT NULL,
    client_message_id   CHAR(36) NOT NULL,
    sent_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_message_client_id UNIQUE (client_message_id),
    CONSTRAINT fk_message_match  FOREIGN KEY (match_id)  REFERENCES match_info (match_id),
    CONSTRAINT fk_message_sender FOREIGN KEY (sender_id) REFERENCES users (user_id)
);
CREATE INDEX idx_message_match_sent ON message (match_id, sent_at);

CREATE TABLE report (
    report_id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    reporter_id         BIGINT NOT NULL,
    reported_user_id    BIGINT NOT NULL,
    reason              VARCHAR(500) NOT NULL,
    detail              VARCHAR(1000) NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'pending',
    reviewed_by         BIGINT NULL,
    reviewed_at         DATETIME NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id)      REFERENCES users (user_id),
    CONSTRAINT fk_report_reported FOREIGN KEY (reported_user_id) REFERENCES users (user_id),
    CONSTRAINT fk_report_reviewer FOREIGN KEY (reviewed_by)      REFERENCES users (user_id)
);

CREATE TABLE push_subscription (
    subscription_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    endpoint        VARCHAR(500) NOT NULL,
    p256dh          VARCHAR(255) NOT NULL,
    auth            VARCHAR(255) NOT NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_push_user_endpoint UNIQUE (user_id, endpoint),
    CONSTRAINT fk_push_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);
