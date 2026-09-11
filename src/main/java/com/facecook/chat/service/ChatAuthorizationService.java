package com.facecook.chat.service;

import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.cook.entity.MatchInfo;
import com.facecook.cook.repository.MatchInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatAuthorizationService {

    private final MatchInfoRepository matchInfoRepository;

    @Transactional(readOnly = true)
    public MatchInfo requireParticipant(Long matchId, Long userId) {
        MatchInfo matchInfo = matchInfoRepository.findById(matchId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "매칭을 찾을 수 없습니다."));
        if (!matchInfo.includes(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return matchInfo;
    }
}
