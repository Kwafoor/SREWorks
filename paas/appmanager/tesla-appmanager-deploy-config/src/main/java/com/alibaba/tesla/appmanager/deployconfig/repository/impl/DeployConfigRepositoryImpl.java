package com.alibaba.tesla.appmanager.deployconfig.repository.impl;

import com.alibaba.tesla.appmanager.common.constants.DefaultConstant;
import com.alibaba.tesla.appmanager.common.util.DateUtil;
import com.alibaba.tesla.appmanager.deployconfig.repository.DeployConfigRepository;
import com.alibaba.tesla.appmanager.deployconfig.repository.condition.DeployConfigQueryCondition;
import com.alibaba.tesla.appmanager.deployconfig.repository.domain.DeployConfigDO;
import com.alibaba.tesla.appmanager.deployconfig.repository.domain.DeployConfigDOExample;
import com.alibaba.tesla.appmanager.deployconfig.repository.mapper.DeployConfigDOMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class DeployConfigRepositoryImpl implements DeployConfigRepository {

    private final DeployConfigDOMapper mapper;

    public DeployConfigRepositoryImpl(DeployConfigDOMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public long countByExample(DeployConfigQueryCondition condition) {
        return mapper.countByExample(buildExample(condition));
    }

    @Override
    public int deleteByExample(DeployConfigQueryCondition condition) {
        return mapper.deleteByExample(buildExample(condition));
    }

    @Override
    public int insertSelective(DeployConfigDO record) {
        return mapper.insertSelective(insertDate(record));
    }

    @Override
    public List<DeployConfigDO> selectByExample(DeployConfigQueryCondition condition) {
        return mapper.selectByExample(buildExample(condition));
    }

    @Override
    public int updateByExampleSelective(DeployConfigDO record, DeployConfigQueryCondition condition) {
        return mapper.updateByExampleSelective(updateDate(record), buildExample(condition));
    }

    private DeployConfigDOExample buildExample(DeployConfigQueryCondition condition) {
        DeployConfigDOExample example = new DeployConfigDOExample();
        DeployConfigDOExample.Criteria criteria = example.createCriteria();
        // 允许 appId/envId 为空
        if (condition.getAppId() != null) {
            criteria.andAppIdEqualTo(condition.getAppId());
        }
        if (StringUtils.isNotBlank(condition.getTypeId())) {
            criteria.andTypeIdEqualTo(condition.getTypeId());
        }
        if (condition.getEnvId() != null) {
            criteria.andEnvIdEqualTo(condition.getEnvId());
        }
        if (StringUtils.isNotBlank(condition.getApiVersion())) {
            criteria.andApiVersionEqualTo(condition.getApiVersion());
        }
        if (condition.getEnabled() != null) {
            criteria.andEnabledEqualTo(condition.getEnabled());
        }
        if (condition.getInherit() != null) {
            criteria.andInheritEqualTo(condition.getInherit());
        }
        example.setOrderByClause(DefaultConstant.ORDER_BY_ID_DESC);
        return example;
    }

    private DeployConfigDO insertDate(DeployConfigDO record) {
        Date now = DateUtil.now();
        record.setGmtCreate(now);
        record.setGmtModified(now);
        return record;
    }

    private DeployConfigDO updateDate(DeployConfigDO record) {
        Date now = DateUtil.now();
        record.setGmtModified(now);
        return record;
    }
}
