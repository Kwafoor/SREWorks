package com.alibaba.sreworks.pmdb.domain.metric;

import com.alibaba.sreworks.pmdb.domain.metric.Metric;
import com.alibaba.sreworks.pmdb.domain.metric.MetricExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.RowBounds;

public interface MetricMapper {
    long countByExample(MetricExample example);

    int deleteByExample(MetricExample example);

    int deleteByPrimaryKey(Integer id);

    int insert(Metric record);

    int insertSelective(Metric record);

    List<Metric> selectByExampleWithRowbounds(MetricExample example, RowBounds rowBounds);

    List<Metric> selectByExample(MetricExample example);

    Metric selectByPrimaryKey(Integer id);

    int updateByExampleSelective(@Param("record") Metric record, @Param("example") MetricExample example);

    int updateByExample(@Param("record") Metric record, @Param("example") MetricExample example);

    int updateByPrimaryKeySelective(Metric record);

    int updateByPrimaryKey(Metric record);
}