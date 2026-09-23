package com.facecook.chat.service;

import com.facecook.chat.dto.ChatMessageResponse;
import com.facecook.chat.entity.Message;
import com.facecook.chat.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 채팅방 메시지 한 페이지를 읽어 응답으로 바꾼다. 참가자 화면({@link ChatService#getHistory})과
 * 슈퍼 계정 열람({@code SuperAccountService#listMessages})이 같은 조회·변환을 쓴다.
 *
 * <p>권한 검사는 여기 두지 않는다 — 참가자는 당사자만, 슈퍼 계정은 모든 방을 볼 수 있어서
 * 두 호출부의 규칙이 다르다. 각 호출부가 검사를 마친 뒤 이 클래스를 부른다.</p>
 */
@Component
@RequiredArgsConstructor
public class ChatMessagePageReader {

    private final MessageRepository messageRepository;

    /**
     * matchId 방의 메시지를 id 역순(최신 먼저)으로 최대 limit개 돌려준다. before가 있으면 그
     * id보다 오래된 것만(이전 페이지 넘기기).
     *
     * <p>전제조건: 호출부가 matchId 접근 권한을 이미 확인했다.</p>
     *
     * <p>부작용: 없음(조회만).</p>
     *
     * <p>예외 없음.</p>
     */
    public List<ChatMessageResponse> read(Long matchId, Long before, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        List<Message> messages = before == null
                ? messageRepository.findByMatchIdOrderByIdDesc(matchId, page)
                : messageRepository.findByMatchIdAndIdLessThanOrderByIdDesc(matchId, before, page);
        return messages.stream().map(ChatMessageResponse::from).toList();
    }
}
