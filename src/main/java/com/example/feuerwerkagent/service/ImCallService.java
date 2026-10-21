package com.example.feuerwerkagent.service;

import com.example.feuerwerkagent.entity.ShopUser;
import com.example.feuerwerkagent.mapper.ShopUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * IM视频通话信令转发（WebRTC）：
 * 上行 {type:"CALL", action, peerId, callId, sdp?, candidate?}
 * 下发 {type:"CALL", data:{action, callId, fromUserId, from:{...}, sdp?, candidate?}}
 * 服务端仅做好友校验+点对点转发，媒体流P2P直连不经过服务器
 */
@Slf4j
@Service
public class ImCallService {

    @Autowired
    private ImFriendService friendService;

    @Autowired
    private ImPresenceService presenceService;

    @Autowired
    private ShopUserMapper userMapper;

    /**
     * 处理通话信令：校验好友关系后原样转发给对方
     * action: INVITE邀请 / ACCEPT接听 / REJECT拒接 / CANCEL取消 / HANGUP挂断
     *         OFFER(SDP) / ANSWER(SDP) / ICE(候选)
     */
    public void handleCall(Long meId, Map<String, Object> payload) {
        String action = String.valueOf(payload.get("action"));
        Long peerId = Long.valueOf(String.valueOf(payload.get("peerId")));
        String callId = String.valueOf(payload.get("callId"));

        // 仅好友之间可通话（对方须在线，否则INVITE无意义）
        if (!friendService.isFriend(meId, peerId)) {
            throw new IllegalArgumentException("对方不是你的好友，无法通话");
        }
        if ("INVITE".equals(action) && !presenceService.isOnline(peerId)) {
            throw new IllegalArgumentException("对方当前不在线");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("action", action);
        data.put("callId", callId);
        data.put("fromUserId", meId);
        if (payload.get("sdp") != null) {
            data.put("sdp", payload.get("sdp"));
        }
        if (payload.get("candidate") != null) {
            data.put("candidate", payload.get("candidate"));
        }
        ShopUser me = userMapper.selectById(meId);
        if (me != null) {
            data.put("from", friendService.userBrief(me));
        }

        Map<String, Object> event = new HashMap<>();
        event.put("type", "CALL");
        event.put("data", data);
        presenceService.pushToUser(peerId, event);
        log.debug("通话信令转发: {} -> {} action={} callId={}", meId, peerId, action, callId);
    }
}
