package com.facecook.superaccount.service;

import com.facecook.auth.entity.User;
import com.facecook.auth.repository.UserRepository;
import com.facecook.chat.dto.ChatMessageResponse;
import com.facecook.chat.entity.Message;
import com.facecook.chat.repository.MessageRepository;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.cook.entity.MatchInfo;
import com.facecook.cook.repository.MatchInfoRepository;
import com.facecook.profile.entity.Profile;
import com.facecook.profile.repository.ProfileRepository;
import com.facecook.superaccount.dto.SuperChatMemberResponse;
import com.facecook.superaccount.dto.SuperChatRoomResponse;
import com.facecook.superaccount.dto.SuperUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SuperAccountService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final MatchInfoRepository matchInfoRepository;
    private final MessageRepository messageRepository;

    @Transactional(readOnly = true)
    public List<SuperUserResponse> listUsers() {
        List<User> users = userRepository.findAll();
        Map<Long, Profile> profiles = profilesByUserId(userIds(users));
        return users.stream()
                .map(user -> SuperUserResponse.from(user, profiles.get(user.getId())))
                .toList();
    }

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

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> listMessages(Long matchId, Long before, int limit) {
        if (!matchInfoRepository.existsById(matchId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "매칭을 찾을 수 없습니다.");
        }
        PageRequest page = PageRequest.of(0, limit);
        List<Message> messages = before == null
                ? messageRepository.findByMatchIdOrderByIdDesc(matchId, page)
                : messageRepository.findByMatchIdAndIdLessThanOrderByIdDesc(matchId, before, page);
        return messages.stream().map(ChatMessageResponse::from).toList();
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
