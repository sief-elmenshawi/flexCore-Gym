package com.flexcore.ptsession.service;

import com.flexcore.ptsession.dto.request.BookPTSessionRequest;
import com.flexcore.ptsession.dto.response.PTSessionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PTSessionService {

    PTSessionResponse book(BookPTSessionRequest request, Long memberId);

    void cancel(Long sessionId, Long requestingUserId);

    void deletePermanently(Long sessionId, Long requestingUserId);

    Page<PTSessionResponse> getMySessions(Long userId, String roleName, Pageable pageable);
}
