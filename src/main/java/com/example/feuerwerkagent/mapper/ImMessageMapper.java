package com.example.feuerwerkagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.feuerwerkagent.entity.ImMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ImMessageMapper extends BaseMapper<ImMessage> {

    /**
     * 单聊会话内最大消息ID（无消息返回0）
     */
    @Select("SELECT IFNULL(MAX(id), 0) FROM im_message WHERE peer_type = 'SINGLE' " +
            "AND ((from_user_id = #{a} AND to_user_id = #{b}) OR (from_user_id = #{b} AND to_user_id = #{a}))")
    long maxIdOfSingle(@Param("a") Long a, @Param("b") Long b);

    /**
     * 群聊会话内最大消息ID（无消息返回0）
     */
    @Select("SELECT IFNULL(MAX(id), 0) FROM im_message WHERE peer_type = 'GROUP' AND group_id = #{groupId}")
    long maxIdOfGroup(@Param("groupId") Long groupId);
}
