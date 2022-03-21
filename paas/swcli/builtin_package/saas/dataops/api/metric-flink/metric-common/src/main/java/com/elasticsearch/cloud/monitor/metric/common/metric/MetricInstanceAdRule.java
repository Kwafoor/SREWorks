package com.elasticsearch.cloud.monitor.metric.common.metric;

import lombok.Data;

import java.io.Serializable;

/**
 * 指标实例对象
 *
 * @author: fangzong.ly
 * @date: 2021/09/05 11:47
 */

@Data
public class MetricInstanceAdRule implements Serializable {

    private String metricId;

    private String metricName;

    private String metricInstanceId;

    private String indexPath;

    private String indexTags;

    private String adTitle;

    private Long ruleId;

    private Boolean enable;

    private String teamId;

    private String appId;
}
