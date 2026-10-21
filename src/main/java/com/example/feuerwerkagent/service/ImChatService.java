package com.example.feuerwerkagent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.feuerwerkagent.entity.ImConversation;
import com.example.feuerwerkagent.entity.ImGroup;
import com.example.feuerwerkagent.entity.ImMessage;
import com.example.feuerwerkagent.entity.ShopUser;
import com.example.feuerwerkagent.mapper.ImConversationMapper;
import com.example.feuerwerkagent.mapper.ImGroupMapper;
import com.example.feuerwerkagent.mapper.ImMessageMapper;
import com.example.feuerwerkagent.mapper.ShopUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IM消息服务：发送（校验+落库+推送）/ 会话列表 / 历史 / 已读
 */
@Service
public class ImChatService {

    private static final int HISTORY_PAGE_SIZE = 50;

    @Autowired
    private ImMessageMapper messageMapper;

    @Autowired
    private ImConversationMapper conversationMapper;

    @Autowired
    private ShopUserMapper userMapper;

    @Autowired
    private ImGroupMapper groupMapper;

    @Autowired
    private ImFriendService friendService;

    @Autowired
    private ImGroupService groupService;

    @Autowired
    private ImPresenceService presenceService;

    /**
     * 发送消息：单聊校验好友关系，群聊校验成员身份
     *
     * @return 推送给发送方的ACK载荷（含完整消息与clientId）
     */
    public Map<String, Object> send(Long fromUserId, String peerType, Long peerId,
                                    String contentType, String content, String clientId) {
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        if (content.length() > 4000) {
            throw new IllegalArgumentException("消息过长（最多4000字符）");
        }

        boolean isGroup = ImMessage.TYPE_GROUP.equals(peerType);
        // 校验
        if (isGroup) {
            if (!groupService.isMember(fromUserId, peerId)) {
                throw new IllegalArgumentException("你不是该群成员，无法发送消息");
            }
        } else {
            if (!friendService.isFriend(fromUserId, peerId)) {
                throw new IllegalArgumentException("对方不是你的好友，无法发送消息");
            }
        }

        ImMessage message = new ImMessage();
        message.setPeerType(peerType);
        message.setFromUserId(fromUserId);
        message.setToUserId(isGroup ? null : peerId);
        message.setGroupId(isGroup ? peerId : null);
        message.setContentType(StringUtils.hasText(contentType) ? contentType : ImMessage.CONTENT_TEXT);
        message.setContent(content.trim());
        message.setCreateTime(LocalDateTime.now());
        messageMapper.insert(message);

        ShopUser sender = userMapper.selectById(fromUserId);
        Map<String, Object> payload = messageMap(message, sender);

        // 推送：接收方 MESSAGE / 发送方 ACK（多端同步）
        Map<String, Object> messageEvent = new HashMap<>();
        messageEvent.put("type", "MESSAGE");
        messageEvent.put("data", payload);
        if (isGroup) {
            presenceService.pushToUsers(groupService.memberUserIds(peerId), messageEvent, fromUserId);
        } else {
            presenceService.pushToUser(peerId, messageEvent);
        }
        Map<String, Object> ack = new HashMap<>();
        ack.put("type", "ACK");
        Map<String, Object> ackData = new HashMap<>(payload);
        if (clientId != null) {
            ackData.put("clientId", clientId);
        }
        ack.put("data", ackData);
        presenceService.pushToUser(fromUserId, ack);
        return ack;
    }

    /**
     * 会话列表：好友单聊 + 我加入的群聊，含最后一条消息与未读数，按最后消息时间倒序
     */
    public List<Map<String, Object>> conversations(Long meId) {
        List<Map<String, Object>> result = new ArrayList<>();

        // 单聊会话（来自好友列表）
        for (Map<String, Object> friend : friendService.myFriends(meId)) {
            Long friendId = (Long) friend.get("id");
            Map<String, Object> row = new HashMap<>();
            row.put("peerType", ImMessage.TYPE_SINGLE);
            row.put("peerId", friendId);
            row.put("name", friend.get("nickname"));
            row.put("avatar", friend.get("avatar"));
            row.put("online", friend.get("online"));
            row.put("lastMessage", lastMessageMap(lastMessageOfSingle(meId, friendId)));
            row.put("unread", unreadOfSingle(meId, friendId));
            row.put("sortTime", sortTime(row));
            result.add(row);
        }

        // 群聊会话
        for (Map<String, Object> group : groupService.myGroups(meId)) {
            Long groupId = (Long) group.get("id");
            Map<String, Object> row = new HashMap<>();
            row.put("peerType", ImMessage.TYPE_GROUP);
            row.put("peerId", groupId);
            row.put("name", group.get("name"));
            row.put("avatar", null);
            row.put("memberCount", group.get("memberCount"));
            ImMessage last = lastMessageOfGroup(groupId);
            Map<String, Object> lastMap = lastMessageMap(last);
            if (last != null) {
                ShopUser sender = userMapper.selectById(last.getFromUserId());
                lastMap.put("fromNickname", sender == null ? "未知用户"
                        : (StringUtils.hasText(sender.getNickname()) ? sender.getNickname() : sender.getUsername()));
            }
            row.put("lastMessage", lastMap);
            row.put("unread", unreadOfGroup(meId, groupId));
            row.put("sortTime", sortTime(row));
            result.add(row);
        }

        result.sort(Comparator.comparing(m -> (LocalDateTime) ((Map<String, Object>) m).get("sortTime"),
                Comparator.reverseOrder()));
        result.forEach(m -> m.remove("sortTime"));
        return result;
    }

    /**
     * 历史消息（升序返回）。单聊需为好友，群聊需为成员
     */
    public List<Map<String, Object>> history(Long meId, String peerType, Long peerId, Long beforeId, Integer size) {
        int limit = size == null || size < 1 || size > 100 ? HISTORY_PAGE_SIZE : size;
        boolean isGroup = ImMessage.TYPE_GROUP.equals(peerType);
        if (isGroup) {
            groupService.requireMember(meId, peerId);
        } else {
            if (!friendService.isFriend(meId, peerId)) {
                throw new IllegalArgumentException("对方不是你的好友");
            }
        }

        QueryWrapper<ImMessage> wrapper = new QueryWrapper<>();
        wrapper.eq("peer_type", peerType);
        if (isGroup) {
            wrapper.eq("group_id", peerId);
        } else {
            wrapper.and(w -> w
                    .apply("(from_user_id = {0} AND to_user_id = {1})", meId, peerId)
                    .or()
                    .apply("(from_user_id = {0} AND to_user_id = {1})", peerId, meId));
        }
        if (beforeId != null && beforeId > 0) {
            wrapper.lt("id", beforeId);
        }
        wrapper.orderByDesc("id").last("LIMIT " + limit);
        List<ImMessage> messages = messageMapper.selectList(wrapper);

        // 反转为升序
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0; i--) {
            ShopUser sender = userMapper.selectById(messages.get(i).getFromUserId());
            result.add(messageMap(messages.get(i), sender));
        }
        return result;
    }

    /**
     * 标记会话已读（读位=会话内当前最大消息ID）
     */
    public void markRead(Long meId, String peerType, Long peerId) {
        boolean isGroup = ImMessage.TYPE_GROUP.equals(peerType);
        long maxId = isGroup ? messageMapper.maxIdOfGroup(peerId)
                : messageMapper.maxIdOfSingle(meId, peerId);

        ImConversation exist = conversationMapper.selectOne(new QueryWrapper<ImConversation>()
                .eq("user_id", meId).eq("peer_type", peerType).eq("peer_id", peerId));
        if (exist == null) {
            ImConversation c = new ImConversation();
            c.setUserId(meId);
            c.setPeerType(peerType);
            c.setPeerId(peerId);
            c.setLastReadMessageId(maxId);
            c.setUpdateTime(LocalDateTime.now());
            conversationMapper.insert(c);
        } else if (exist.getLastReadMessageId() == null || exist.getLastReadMessageId() < maxId) {
            exist.setLastReadMessageId(maxId);
            exist.setUpdateTime(LocalDateTime.now());
            conversationMapper.updateById(exist);
        }
    }

    /**
     * 群名（用于推送展示）
     */
    public String groupName(Long groupId) {
        ImGroup group = groupMapper.selectById(groupId);
        return group == null ? "群聊" : group.getName();
    }

    // ==================== 私有工具 ====================

    private ImMessage lastMessageOfSingle(Long me, Long peer) {
        return messageMapper.selectOne(new QueryWrapper<ImMessage>()
                .eq("peer_type", ImMessage.TYPE_SINGLE)
                .and(w -> w.apply("(from_user_id = {0} AND to_user_id = {1})", me, peer)
                        .or()
                        .apply("(from_user_id = {0} AND to_user_id = {1})", peer, me))
                .orderByDesc("id").last("LIMIT 1"));
    }

    private ImMessage lastMessageOfGroup(Long groupId) {
        return messageMapper.selectOne(new QueryWrapper<ImMessage>()
                .eq("peer_type", ImMessage.TYPE_GROUP)
                .eq("group_id", groupId)
                .orderByDesc("id").last("LIMIT 1"));
    }

    private long lastReadId(Long meId, String peerType, Long peerId) {
        ImConversation c = conversationMapper.selectOne(new QueryWrapper<ImConversation>()
                .eq("user_id", meId).eq("peer_type", peerType).eq("peer_id", peerId));
        return c == null || c.getLastReadMessageId() == null ? 0 : c.getLastReadMessageId();
    }

    private long unreadOfSingle(Long meId, Long peerId) {
        Long count = messageMapper.selectCount(new QueryWrapper<ImMessage>()
                .eq("peer_type", ImMessage.TYPE_SINGLE)
                .eq("from_user_id", peerId).eq("to_user_id", meId)
                .gt("id", lastReadId(meId, ImMessage.TYPE_SINGLE, peerId)));
        return count == null ? 0 : count;
    }

    private long unreadOfGroup(Long meId, Long groupId) {
        Long count = messageMapper.selectCount(new QueryWrapper<ImMessage>()
                .eq("peer_type", ImMessage.TYPE_GROUP)
                .eq("group_id", groupId)
                .ne("from_user_id", meId)
                .gt("id", lastReadId(meId, ImMessage.TYPE_GROUP, groupId)));
        return count == null ? 0 : count;
    }

    /**
     * 消息统一载荷（含发送者信息）
     */
    private Map<String, Object> messageMap(ImMessage message, ShopUser sender) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", message.getId());
        map.put("peerType", message.getPeerType());
        map.put("fromUserId", message.getFromUserId());
        map.put("contentType", message.getContentType());
        map.put("content", message.getContent());
        map.put("createTime", message.getCreateTime());
        boolean isGroup = ImMessage.TYPE_GROUP.equals(message.getPeerType());
        map.put("peerId", isGroup ? message.getGroupId() : message.getToUserId());
        if (sender != null) {
            map.put("fromNickname", StringUtils.hasText(sender.getNickname()) ? sender.getNickname() : sender.getUsername());
            map.put("fromAvatar", sender.getAvatar());
        }
        return map;
    }

    private Map<String, Object> lastMessageMap(ImMessage last) {
        Map<String, Object> map = new HashMap<>();
        if (last == null) {
            return map;
        }
        map.put("content", ImMessage.CONTENT_IMAGE.equals(last.getContentType()) ? "[图片]" : last.getContent());
        map.put("contentType", last.getContentType());
        map.put("fromUserId", last.getFromUserId());
        map.put("createTime", last.getCreateTime());
        return map;
    }

    private LocalDateTime sortTime(Map<String, Object> conversation) {
        Map<String, Object> last = (Map<String, Object>) conversation.get("lastMessage");
        Object time = last == null ? null : last.get("createTime");
        return time instanceof LocalDateTime ? (LocalDateTime) time : LocalDateTime.MIN;
    }
}
