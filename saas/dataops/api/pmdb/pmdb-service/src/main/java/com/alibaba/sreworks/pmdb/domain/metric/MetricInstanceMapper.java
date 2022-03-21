package com.alibaba.sreworks.pmdb.domain.metric;

import com.alibaba.sreworks.pmdb.domain.metric.MetricInstance;
import com.alibaba.sreworks.pmdb.domain.metric.MetricInstanceExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.RowBounds;

public interface MetricInstanceMapper {
    long countByExample(MetricInstanceExample example);

    int deleteByExample(MetricInstanceExample example);

    int deleteByPrimaryKey(Long id);

    int insert(MetricInstance record);

    int insertSelective(MetricInstance record);

    List<MetricInstance> selectByExampleWithRowbounds(MetricInstanceExample example, RowBounds rowBounds);

    List<MetricInstance> selectByExample(MetricInstanceExample example);

    MetricInstance selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") MetricInstance record, @Param("example") MetricInstanceExample example);

    int updateByExample(@Param("record") MetricInstance record, @Param("example") MetricInstanceExample example);

    int updateByPrimaryKeySelective(MetricInstance record);

    int updateByPrimaryKey(MetricInstance record);

    int batchInsert(@Param("list") List<MetricInstance> list);
}