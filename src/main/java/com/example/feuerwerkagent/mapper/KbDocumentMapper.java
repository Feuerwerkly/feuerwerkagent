package com.example.feuerwerkagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.feuerwerkagent.entity.KbDocument;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface KbDocumentMapper extends BaseMapper<KbDocument> {

    /**
     * 查询所有文档
     */
    List<KbDocument> selectAllDocuments();

    /**
     * 插入文档
     */
    int insert(KbDocument document);

    /**
     * 根据ID更新文档
     */
    int updateById(KbDocument document);

    /**
     * 根据ID查询文档
     */
    KbDocument selectById(Long id);

    /**
     * 根据ID删除文档
     */
    int deleteById(Long id);
}