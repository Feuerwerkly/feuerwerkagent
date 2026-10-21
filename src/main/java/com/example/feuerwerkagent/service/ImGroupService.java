package com.example.feuerwerkagent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.feuerwerkagent.entity.ImGroup;
import com.example.feuerwerkagent.entity.ImGroupMember;
import com.example.feuerwerkagent.entity.ShopUser;
import com.example.feuerwerkagent.mapper.ImGroupMapper;
import com.example.feuerwerkagent.mapper.ImGroupMemberMapper;
import com.example.feuerwerkagent.mapper.ShopUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * IM群聊服务：建群 / 成员管理 / 邀请 / 踢人 / 退群 / 解散
 */
@Service
public class ImGroupService {

    @Autowired
    private ImGroupMapper groupMapper;

    @Autowired
    private ImGroupMemberMapper memberMapper;

    @Autowired
    private ShopUserMapper userMapper;

    @Autowired
    private ImPresenceService presenceService;

    @Autowired
    private ImFriendService friendService;

    /**
     * 创建群聊（创建者成为群主，并同时加入初始成员）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(Long ownerId, String name, List<Long> memberIds) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("请填写群名称");
        }
        ImGroup group = new ImGroup();
        group.setName(name.trim());
        group.setOwnerId(ownerId);
        group.setCreateTime(LocalDateTime.now());
        group.setUpdateTime(LocalDateTime.now());
        groupMapper.insert(group);

        // 群主
        insertMember(group.getId(), ownerId, ImGroupMember.ROLE_OWNER);
        // 初始成员（去重、排除群主）
        if (memberIds != null) {
            Set<Long> unique = new LinkedHashSet<>(memberIds);
            unique.remove(ownerId);
            for (Long uid : unique) {
                ShopUser u = userMapper.selectById(uid);
                if (u != null && u.getStatus() != null && u.getStatus() == 1) {
                    insertMember(group.getId(), uid, ImGroupMember.ROLE_MEMBER);
                }
            }
        }

        Map<String, Object> result = groupBrief(group, memberCount(group.getId()));
        // 在线推送群事件给其他成员
        pushGroupEvent("created", group, memberUserIds(group.getId()), ownerId);
        return result;
    }

    /**
     * 我加入的群列表
     */
    public List<Map<String, Object>> myGroups(Long userId) {
        List<ImGroupMember> memberships = memberMapper.selectList(
                new QueryWrapper<ImGroupMember>().eq("user_id", userId).orderByDesc("join_time"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ImGroupMember m : memberships) {
            ImGroup group = groupMapper.selectById(m.getGroupId());
            if (group == null) {
                continue;
            }
            Map<String, Object> row = groupBrief(group, memberCount(group.getId()));
            row.put("myRole", m.getRole());
            result.add(row);
        }
        return result;
    }

    /**
     * 群成员列表（需为群成员才能查看）
     */
    public List<Map<String, Object>> members(Long meId, Long groupId) {
        requireMember(meId, groupId);
        List<ImGroupMember> memberships = memberMapper.selectList(
                new QueryWrapper<ImGroupMember>().eq("group_id", groupId).orderByAsc("join_time"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ImGroupMember m : memberships) {
            ShopUser u = userMapper.selectById(m.getUserId());
            if (u == null) {
                continue;
            }
            Map<String, Object> row = friendService.userBrief(u);
            row.put("role", m.getRole());
            row.put("online", presenceService.isOnline(m.getUserId()));
            result.add(row);
        }
        return result;
    }

    /**
     * 群信息
     */
    public Map<String, Object> groupInfo(Long groupId) {
        ImGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new IllegalArgumentException("群不存在");
        }
        Map<String, Object> row = groupBrief(group, memberCount(groupId));
        row.put("ownerId", group.getOwnerId());
        row.put("notice", group.getNotice());
        return row;
    }

    /**
     * 邀请成员（群成员均可邀请）
     */
    @Transactional(rollbackFor = Exception.class)
    public void invite(Long meId, Long groupId, List<Long> userIds) {
        requireMember(meId, groupId);
        if (userIds == null || userIds.isEmpty()) {
            throw new IllegalArgumentException("请选择要邀请的用户");
        }
        for (Long uid : new LinkedHashSet<>(userIds)) {
            ShopUser u = userMapper.selectById(uid);
            if (u == null || u.getStatus() == null || u.getStatus() != 1) {
                continue;
            }
            Long exists = memberMapper.selectCount(new QueryWrapper<ImGroupMember>()
                    .eq("group_id", groupId).eq("user_id", uid));
            if (exists != null && exists > 0) {
                continue;
            }
            insertMember(groupId, uid, ImGroupMember.ROLE_MEMBER);
        }
        ImGroup group = groupMapper.selectById(groupId);
        if (group != null) {
            group.setUpdateTime(LocalDateTime.now());
            groupMapper.updateById(group);
            pushGroupEvent("invited", group, userIds, null);
        }
    }

    /**
     * 踢人（仅群主）
     */
    public void kick(Long meId, Long groupId, Long userId) {
        requireOwner(meId, groupId);
        if (userId.equals(meId)) {
            throw new IllegalArgumentException("群主不能踢自己，如需解散请使用解散功能");
        }
        memberMapper.delete(new QueryWrapper<ImGroupMember>()
                .eq("group_id", groupId).eq("user_id", userId));
        ImGroup group = groupMapper.selectById(groupId);
        if (group != null) {
            pushGroupEvent("removed", group, List.of(userId), null);
        }
    }

    /**
     * 退群（群主不可退，需解散）
     */
    public void quit(Long meId, Long groupId) {
        requireMember(meId, groupId);
        ImGroup group = groupMapper.selectById(groupId);
        if (group != null && group.getOwnerId().equals(meId)) {
            throw new IllegalArgumentException("群主不能退出群聊，可使用\"解散群聊\"");
        }
        memberMapper.delete(new QueryWrapper<ImGroupMember>()
                .eq("group_id", groupId).eq("user_id", meId));
    }

    /**
     * 解散群（仅群主）
     */
    @Transactional(rollbackFor = Exception.class)
    public void disband(Long meId, Long groupId) {
        requireOwner(meId, groupId);
        ImGroup group = groupMapper.selectById(groupId);
        List<Long> memberIds = memberUserIds(groupId);
        memberMapper.delete(new QueryWrapper<ImGroupMember>().eq("group_id", groupId));
        groupMapper.deleteById(groupId);
        if (group != null) {
            pushGroupEvent("disbanded", group, memberIds, null);
        }
    }

    /**
     * 修改群名称/公告（仅群主）
     */
    public void update(Long meId, Long groupId, String name, String notice) {
        requireOwner(meId, groupId);
        ImGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new IllegalArgumentException("群不存在");
        }
        if (StringUtils.hasText(name)) {
            group.setName(name.trim());
        }
        if (notice != null) {
            group.setNotice(notice);
        }
        group.setUpdateTime(LocalDateTime.now());
        groupMapper.updateById(group);
        pushGroupEvent("updated", group, memberUserIds(groupId), null);
    }

    // ==================== 校验与工具 ====================

    public boolean isMember(Long userId, Long groupId) {
        Long count = memberMapper.selectCount(new QueryWrapper<ImGroupMember>()
                .eq("group_id", groupId).eq("user_id", userId));
        return count != null && count > 0;
    }

    public List<Long> memberUserIds(Long groupId) {
        return memberMapper.selectList(new QueryWrapper<ImGroupMember>()
                        .eq("group_id", groupId))
                .stream().map(ImGroupMember::getUserId).toList();
    }

    /**
     * 校验用户是群成员（群必须存在），否则抛出业务异常
     */
    public void requireMember(Long userId, Long groupId) {
        ImGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new IllegalArgumentException("群不存在或已解散");
        }
        if (!isMember(userId, groupId)) {
            throw new IllegalArgumentException("你不是该群成员");
        }
    }

    private void requireOwner(Long userId, Long groupId) {
        requireMember(userId, groupId);
        ImGroup group = groupMapper.selectById(groupId);
        if (!group.getOwnerId().equals(userId)) {
            throw new IllegalArgumentException("仅群主可执行该操作");
        }
    }

    private void insertMember(Long groupId, Long userId, String role) {
        ImGroupMember m = new ImGroupMember();
        m.setGroupId(groupId);
        m.setUserId(userId);
        m.setRole(role);
        m.setJoinTime(LocalDateTime.now());
        memberMapper.insert(m);
    }

    private int memberCount(Long groupId) {
        Long count = memberMapper.selectCount(new QueryWrapper<ImGroupMember>().eq("group_id", groupId));
        return count == null ? 0 : count.intValue();
    }

    private Map<String, Object> groupBrief(ImGroup group, int count) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", group.getId());
        map.put("name", group.getName());
        map.put("notice", group.getNotice());
        map.put("ownerId", group.getOwnerId());
        map.put("memberCount", count);
        map.put("createTime", group.getCreateTime());
        return map;
    }

    /**
     * 群事件实时推送（created/invited/removed/disbanded/updated）
     */
    private void pushGroupEvent(String action, ImGroup group, List<Long> targetUserIds, Long excludeUserId) {
        if (targetUserIds == null || targetUserIds.isEmpty()) {
            return;
        }
        Map<String, Object> event = new HashMap<>();
        event.put("type", "GROUP_EVENT");
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", action);
        payload.put("group", groupBrief(group, memberCount(group.getId())));
        event.put("data", payload);
        presenceService.pushToUsers(targetUserIds, event, excludeUserId);
    }
}
