package com.alibaba.tesla.appmanager.meta.k8smicroservice.repository.mapper;

import com.alibaba.tesla.appmanager.meta.k8smicroservice.repository.domain.K8sMicroServiceMetaDO;
import com.alibaba.tesla.appmanager.meta.k8smicroservice.repository.domain.K8sMicroServiceMetaDOExample;
import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.RowBounds;

public interface K8sMicroServiceMetaDOMapper {
    long countByExample(K8sMicroServiceMetaDOExample example);

    int deleteByExample(K8sMicroServiceMetaDOExample example);

    int deleteByPrimaryKey(Long id);

    int insert(K8sMicroServiceMetaDO record);

    int insertSelective(K8sMicroServiceMetaDO record);

    List<K8sMicroServiceMetaDO> selectByExampleWithBLOBsWithRowbounds(K8sMicroServiceMetaDOExample example, RowBounds rowBounds);

    List<K8sMicroServiceMetaDO> selectByExampleWithBLOBs(K8sMicroServiceMetaDOExample example);

    List<K8sMicroServiceMetaDO> selectByExampleWithRowbounds(K8sMicroServiceMetaDOExample example, RowBounds rowBounds);

    List<K8sMicroServiceMetaDO> selectByExample(K8sMicroServiceMetaDOExample example);

    K8sMicroServiceMetaDO selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") K8sMicroServiceMetaDO record, @Param("example") K8sMicroServiceMetaDOExample example);

    int updateByExampleWithBLOBs(@Param("record") K8sMicroServiceMetaDO record, @Param("example") K8sMicroServiceMetaDOExample example);

    int updateByExample(@Param("record") K8sMicroServiceMetaDO record, @Param("example") K8sMicroServiceMetaDOExample example);

    int updateByPrimaryKeySelective(K8sMicroServiceMetaDO record);

    int updateByPrimaryKeyWithBLOBs(K8sMicroServiceMetaDO record);

    int updateByPrimaryKey(K8sMicroServiceMetaDO record);
}