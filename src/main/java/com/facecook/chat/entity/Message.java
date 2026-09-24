package com.facecook.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 채팅 메시지 한 건({@code message} 테이블). 저장만 하고 고치거나 지우는 메서드는 없다.
 *
 * <p>{@code clientMessageId}는 전역 unique다. 네트워크 문제로 같은 전송이 두 번 와도 두 번째는 저장에 실패하고,
 * 서비스가 첫 번째 것을 찾아 돌려준다. {@code @JdbcTypeCode(SqlTypes.CHAR)}: UUID를 DB에 36자 문자열로 저장한다
 * (스키마가 {@code CHAR(36)}).</p>
 */
@Getter
@Entity
@Table(name = "message")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long id;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(nullable = false, length = 1000)
    private String content;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "client_message_id", nullable = false, unique = true, length = 36)
    private UUID clientMessageId;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    private Message(
            Long matchId,
            Long senderId,
            String content,
            UUID clientMessageId,
            LocalDateTime sentAt
    ) {
        this.matchId = matchId;
        this.senderId = senderId;
        this.content = content;
        this.clientMessageId = clientMessageId;
        this.sentAt = sentAt;
    }

    public static Message create(
            Long matchId,
            Long senderId,
            String content,
            UUID clientMessageId,
            LocalDateTime sentAt
    ) {
        return new Message(matchId, senderId, content, clientMessageId, sentAt);
    }
}
