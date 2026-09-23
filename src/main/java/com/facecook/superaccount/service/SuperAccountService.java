package com.facecook.superaccount.service;

import com.facecook.auth.entity.User;
import com.facecook.auth.repository.UserRepository;
import com.facecook.chat.dto.ChatMessageResponse;
import com.facecook.chat.entity.Message;
import com.facecook.chat.repository.MessageRepository;
import com.facecook.chat.service.ChatMessagePageReader;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.match.entity.MatchInfo;
import com.facecook.match.repository.MatchInfoRepository;
import com.facecook.profile.entity.Profile;
import com.facecook.profile.repository.ProfileRepository;
import com.facecook.superaccount.dto.SuperChatMemberResponse;
import com.facecook.superaccount.dto.SuperChatRoomResponse;
import com.facecook.superaccount.dto.SuperUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 총학생회 슈퍼 계정용 — 전체 유저·매칭·채팅 열람.
 *
 * <p>이 클래스는 SUPER 권한 검사를 하지 않는다 — 호출자가 이미
 * {@code SuperAuthorization.requireSuper()}를 통과했다고 전제한다(권한
 * 검사는 {@code SuperAccountController}에서 끝내고 들어옴). 세 메서드
 * 전부 읽기 전용이고 쓰기는 하나도 없다.</p>
 *
 * <p>{@link #listUsers}·{@link #listChats}는 페이지네이션 없이 전체를
 * 한 번에 조회한다 — 참가자 규모가 작은 축제 행사용 도구라 지금은 문제
 * 없지만, 규모가 커지면 이 부분부터 페이지네이션이 필요해진다.</p>
 */
@Service
@RequiredArgsConstructor
public class SuperAccountService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final MatchInfoRepository matchInfoRepository;
    private final MessageRepository messageRepository;
    private final ChatMessagePageReader messagePageReader;

    /**
     * 전체 유저 목록을 프로필과 함께 반환한다(정렬 순서 보장 없음 —
     * {@code findAll()} 그대로).
     *
     * <p>전제조건: 없음(호출자 SUPER 권한은 컨트롤러가 이미 검증).</p>
     *
     * <p>부작용: 없음. 프로필 없는 유저(관리자·슈퍼 계정 등)는
     * {@code profile}이 null로 온다.</p>
     *
     * <p>예외 없음.</p>
     *
     * @see #listChats()
     */
    @Transactional(readOnly = true)
    public List<SuperUserResponse> listUsers() {
        List<User> users = userRepository.findAll();
        Map<Long, Profile> profiles = profilesByUserId(userIds(users));
        return users.stream()
                .map(user -> SuperUserResponse.from(user, profiles.get(user.getId())))
                .toList();
    }

    /**
     * 전체 매칭방 목록을 성사 시각 최신순으로, 양쪽 멤버 정보와 최근
     * 메시지 미리보기(내용·시각)까지 함께 반환한다.
     *
     * <p>전제조건: 없음.</p>
     *
     * <p>부작용: 없음. 메시지가 하나도 없는 매칭방은 최근 메시지가
     * null로 온다.</p>
     *
     * <p>예외 없음.</p>
     *
     * @see #listMessages(Long, Long, int)
     */
    @Transactional(readOnly = true)
    public List<SuperChatRoomResponse> listChats() {
        List<MatchInfo> matches = matchInfoRepository.findAllByOrderByMatchedAtDesc();
        Set<Long> memberIds = new HashSet<>();
        for (MatchInfo match : matches) {
            memberIds.add(match.getUserAId());
            memberIds.add(match.getUserBId());
        }
        Map<Long, User> users = userRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Profile> profiles = profilesByUserId(memberIds);
        Map<Long, Message> latest = new HashMap<>();
        for (Message message : messageRepository.findLatestPerMatch()) {
            latest.put(message.getMatchId(), message);
        }

        return matches.stream()
                .map(match -> {
                    Message last = latest.get(match.getId());
                    return new SuperChatRoomResponse(
                            match.getId(),
                            match.getMatchedAt(),
                            member(match.getUserAId(), users, profiles),
                            member(match.getUserBId(), users, profiles),
                            last == null ? null : last.getContent(),
                            last == null ? null : last.getSentAt()
                    );
                })
                .toList();
    }

    /**
     * matchId 채팅방의 실제 메시지 내용을 id 역순(최신 먼저)으로 최대
     * limit개 조회한다. before가 있으면 그 메시지 id보다 오래된 것만.
     *
     * <p>전제조건: matchId 존재.</p>
     *
     * <p>부작용: 없음. 매칭 당사자인지는 확인하지 않는다 — 슈퍼 계정은
     * 모든 채팅방을 볼 수 있는 게 의도된 동작이다(당사자만 볼 수 있는
     * {@link com.facecook.chat.service.ChatService#getHistory}와의
     * 차이점).</p>
     *
     * <p>예외: {@code NOT_FOUND}(매칭 없음).</p>
     *
     * @see #listChats()
     * @see com.facecook.chat.service.ChatService#getHistory(Long, Long, Long, int)
     */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> listMessages(Long matchId, Long before, int limit) {
        if (!matchInfoRepository.existsById(matchId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "매칭을 찾을 수 없습니다.");
        }
        return messagePageReader.read(matchId, before, limit);
    }

    private SuperChatMemberResponse member(
            Long userId,
            Map<Long, User> users,
            Map<Long, Profile> profiles
    ) {
        User user = users.get(userId);
        if (user == null) {
            return new SuperChatMemberResponse(userId, null, null, null, null);
        }
        return SuperChatMemberResponse.from(user, profiles.get(userId));
    }

    private Map<Long, Profile> profilesByUserId(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return profileRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(Profile::getUserId, Function.identity()));
    }

    private Set<Long> userIds(List<User> users) {
        return users.stream().map(User::getId).collect(Collectors.toSet());
    }
}
