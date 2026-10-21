package com.example.feuerwerkagent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.feuerwerkagent.entity.ImFriendRequest;
import com.example.feuerwerkagent.entity.ImFriendship;
import com.example.feuerwerkagent.entity.ShopUser;
import com.example.feuerwerkagent.mapper.ImFriendRequestMapper;
import com.example.feuerwerkagent.mapper.ImFriendshipMapper;
import com.example.feuerwerkagent.mapper.ShopUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * IM好友服务：搜索用户 / 好友申请 / 同意拒绝 / 好友列表 / 删除好友
 */
@Service
public class ImFriendService {

    @Autowired
    private ShopUserMapper userMapper;

    @Autowired
    private ImFriendshipMapper friendshipMapper;

    @Autowired
    private ImFriendRequestMapper requestMapper;

    @Autowired
    private ImPresenceService presenceService;

    /**
     * 搜索用户（用户名/昵称），返回与我的关系状态
     */
    public List<Map<String, Object>> searchUsers(Long meId, String keyword) {
        if (!StringUtils.hasText(keyword) || keyword.trim().length() < 1) {
            return new ArrayList<>();
        }
        String kw = keyword.trim();
        List<ShopUser> users = userMapper.selectList(new QueryWrapper<ShopUser>()
                .and(w -> w.like("username", kw).or().like("nickname", kw))
                .ne("id", meId)
                .eq("status", 1)
                .last("LIMIT 20"));

        // 我的好友集合
        Set<Long> friendIds = friendIds(meId);
        // 我发出的待处理申请
        Set<Long> pendingOutIds = requestMapper.selectList(new QueryWrapper<ImFriendRequest>()
                        .eq("from_user_id", meId).eq("status", ImFriendRequest.STATUS_PENDING))
                .stream().map(ImFriendRequest::getToUserId).collect(Collectors.toSet());
        // 我收到的待处理申请
        Set<Long> pendingInIds = requestMapper.selectList(new QueryWrapper<ImFriendRequest>()
                        .eq("to_user_id", meId).eq("status", ImFriendRequest.STATUS_PENDING))
                .stream().map(ImFriendRequest::getFromUserId).collect(Collectors.toSet());

        List<Map<String, Object>> result = new ArrayList<>();
        for (ShopUser u : users) {
            Map<String, Object> row = userBrief(u);
            row.put("online", presenceService.isOnline(u.getId()));
            if (friendIds.contains(u.getId())) {
                row.put("relation", "FRIEND");
            } else if (pendingOutIds.contains(u.getId())) {
                row.put("relation", "PENDING_OUT");
            } else if (pendingInIds.contains(u.getId())) {
                row.put("relation", "PENDING_IN");
            } else {
                row.put("relation", "NONE");
            }
            result.add(row);
        }
        return result;
    }

    /**
     * 发送好友申请
     */
    public void sendRequest(Long meId, Long toUserId, String message) {
        if (meId.equals(toUserId)) {
            throw new IllegalArgumentException("不能添加自己为好友");
        }
        ShopUser target = userMapper.selectById(toUserId);
        if (target == null || target.getStatus() == null || target.getStatus() != 1) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (isFriend(meId, toUserId)) {
            throw new IllegalArgumentException("你们已经是好友了");
        }
        Long pending = requestMapper.selectCount(new QueryWrapper<ImFriendRequest>()
                .eq("from_user_id", meId).eq("to_user_id", toUserId)
                .eq("status", ImFriendRequest.STATUS_PENDING));
        if (pending != null && pending > 0) {
            throw new IllegalArgumentException("已发送过申请，等待对方验证");
        }
        // 对方也向我发过申请 -> 直接互为好友
        ImFriendRequest reverse = requestMapper.selectOne(new QueryWrapper<ImFriendRequest>()
                .eq("from_user_id", toUserId).eq("to_user_id", meId)
                .eq("status", ImFriendRequest.STATUS_PENDING)
                .orderByDesc("id").last("LIMIT 1"));
        if (reverse != null) {
            acceptRequest(meId, reverse.getId());
            return;
        }

        ImFriendRequest request = new ImFriendRequest();
        request.setFromUserId(meId);
        request.setToUserId(toUserId);
        request.setMessage(message);
        request.setStatus(ImFriendRequest.STATUS_PENDING);
        request.setCreateTime(LocalDateTime.now());
        requestMapper.insert(request);

        // 在线实时推送好友申请事件
        ShopUser from = userMapper.selectById(meId);
        Map<String, Object> event = new HashMap<>();
        event.put("type", "FRIEND_REQUEST");
        Map<String, Object> payload = new HashMap<>();
        payload.put("requestId", request.getId());
        payload.put("message", message);
        payload.put("from", from == null ? null : userBrief(from));
        event.put("data", payload);
        presenceService.pushToUser(toUserId, event);
    }

    /**
     * 我收到的好友申请（待处理）
     */
    public List<Map<String, Object>> myRequests(Long meId) {
        List<ImFriendRequest> requests = requestMapper.selectList(new QueryWrapper<ImFriendRequest>()
                .eq("to_user_id", meId).orderByDesc("id").last("LIMIT 100"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ImFriendRequest r : requests) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", r.getId());
            row.put("message", r.getMessage());
            row.put("status", r.getStatus());
            row.put("createTime", r.getCreateTime());
            ShopUser from = userMapper.selectById(r.getFromUserId());
            row.put("from", from == null ? null : userBrief(from));
            result.add(row);
        }
        return result;
    }

    /**
     * 同意好友申请：建立双向好友关系
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> acceptRequest(Long meId, Long requestId) {
        ImFriendRequest request = requestMapper.selectById(requestId);
        if (request == null || !request.getToUserId().equals(meId)) {
            throw new IllegalArgumentException("申请不存在");
        }
        if (request.getStatus() != ImFriendRequest.STATUS_PENDING) {
            throw new IllegalArgumentException("该申请已处理过");
        }

        request.setStatus(ImFriendRequest.STATUS_ACCEPTED);
        request.setHandleTime(LocalDateTime.now());
        requestMapper.updateById(request);

        LocalDateTime now = LocalDateTime.now();
        if (!isFriend(meId, request.getFromUserId())) {
            ImFriendship a = new ImFriendship();
            a.setUserId(meId);
            a.setFriendId(request.getFromUserId());
            a.setCreateTime(now);
            friendshipMapper.insert(a);
            ImFriendship b = new ImFriendship();
            b.setUserId(request.getFromUserId());
            b.setFriendId(meId);
            b.setCreateTime(now);
            friendshipMapper.insert(b);
        }

        // 向申请发起方推送"对方已通过"
        ShopUser me = userMapper.selectById(meId);
        Map<String, Object> event = new HashMap<>();
        event.put("type", "FRIEND_ACCEPTED");
        Map<String, Object> payload = new HashMap<>();
        payload.put("friend", me == null ? null : userBrief(me));
        event.put("data", payload);
        presenceService.pushToUser(request.getFromUserId(), event);

        ShopUser from = userMapper.selectById(request.getFromUserId());
        return from == null ? new HashMap<>() : userBrief(from);
    }

    /**
     * 拒绝好友申请
     */
    public void rejectRequest(Long meId, Long requestId) {
        ImFriendRequest request = requestMapper.selectById(requestId);
        if (request == null || !request.getToUserId().equals(meId)) {
            throw new IllegalArgumentException("申请不存在");
        }
        if (request.getStatus() != ImFriendRequest.STATUS_PENDING) {
            throw new IllegalArgumentException("该申请已处理过");
        }
        request.setStatus(ImFriendRequest.STATUS_REJECTED);
        request.setHandleTime(LocalDateTime.now());
        requestMapper.updateById(request);
    }

    /**
     * 好友列表（含在线状态）
     */
    public List<Map<String, Object>> myFriends(Long meId) {
        List<ImFriendship> friendships = friendshipMapper.selectList(
                new QueryWrapper<ImFriendship>().eq("user_id", meId).orderByAsc("friend_id"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ImFriendship f : friendships) {
            ShopUser u = userMapper.selectById(f.getFriendId());
            if (u == null) {
                continue;
            }
            Map<String, Object> row = userBrief(u);
            row.put("remark", f.getRemark());
            row.put("online", presenceService.isOnline(u.getId()));
            result.add(row);
        }
        return result;
    }

    /**
     * 删除好友（双向）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteFriend(Long meId, Long friendId) {
        friendshipMapper.delete(new QueryWrapper<ImFriendship>()
                .eq("user_id", meId).eq("friend_id", friendId));
        friendshipMapper.delete(new QueryWrapper<ImFriendship>()
                .eq("user_id", friendId).eq("friend_id", meId));
    }

    /**
     * 是否好友（任一方向行存在即视为好友）
     */
    public boolean isFriend(Long a, Long b) {
        Long count = friendshipMapper.selectCount(new QueryWrapper<ImFriendship>()
                .eq("user_id", a).eq("friend_id", b));
        return count != null && count > 0;
    }

    /**
     * 我的好友ID集合
     */
    public Set<Long> friendIds(Long meId) {
        return friendshipMapper.selectList(new QueryWrapper<ImFriendship>()
                        .eq("user_id", meId))
                .stream().map(ImFriendship::getFriendId).collect(Collectors.toSet());
    }

    /**
     * 用户简要信息（不含敏感字段）
     */
    public Map<String, Object> userBrief(ShopUser u) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", u.getId());
        map.put("username", u.getUsername());
        map.put("nickname", StringUtils.hasText(u.getNickname()) ? u.getNickname() : u.getUsername());
        map.put("avatar", u.getAvatar());
        return map;
    }
}
