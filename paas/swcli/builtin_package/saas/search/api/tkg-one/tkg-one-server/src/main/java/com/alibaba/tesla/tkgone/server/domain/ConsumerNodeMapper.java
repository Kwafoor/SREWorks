package com.alibaba.tesla.tkgone.server.domain;

import com.alibaba.tesla.tkgone.server.domain.ConsumerNode;
import com.alibaba.tesla.tkgone.server.domain.ConsumerNodeExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.RowBounds;

public interface ConsumerNodeMapper {
    long countByExample(ConsumerNodeExample example);

    int deleteByExample(ConsumerNodeExample example);

    int deleteByPrimaryKey(Long id);

    int insert(ConsumerNode record);

    int insertSelective(ConsumerNode record);

    List<ConsumerNode> selectByExampleWithRowbounds(ConsumerNodeExample example, RowBounds rowBounds);

    List<ConsumerNode> selectByExample(ConsumerNodeExample example);

    ConsumerNode selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") ConsumerNode record, @Param("example") ConsumerNodeExample example);

    int updateByExample(@Param("record") ConsumerNode record, @Param("example") ConsumerNodeExample example);

    int updateByPrimaryKeySelective(ConsumerNode record);

    int updateByPrimaryKey(ConsumerNode record);
}