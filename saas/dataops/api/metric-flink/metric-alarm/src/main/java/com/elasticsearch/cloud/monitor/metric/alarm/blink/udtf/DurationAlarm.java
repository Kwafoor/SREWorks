package com.elasticsearch.cloud.monitor.metric.alarm.blink.udtf;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.elasticsearch.cloud.monitor.commons.checker.duration.DurationConditionChecker;
import com.elasticsearch.cloud.monitor.commons.core.Alarm;
import com.elasticsearch.cloud.monitor.commons.core.MetricAlarm;
import com.elasticsearch.cloud.monitor.commons.datapoint.DataPoint;
import com.elasticsearch.cloud.monitor.commons.datapoint.ImmutableDataPoint;
import com.elasticsearch.cloud.monitor.commons.rule.Rule;
import com.elasticsearch.cloud.monitor.commons.utils.StringUtils;
import com.elasticsearch.cloud.monitor.commons.utils.TagUtils;
import com.elasticsearch.cloud.monitor.commons.utils.TimeUtils;
import com.elasticsearch.cloud.monitor.metric.alarm.blink.constant.AlarmConstants;
import com.elasticsearch.cloud.monitor.metric.alarm.blink.constant.MetricConstants;
import com.elasticsearch.cloud.monitor.metric.alarm.blink.utils.AlarmEvent;
import com.elasticsearch.cloud.monitor.metric.alarm.blink.utils.AlarmEventHelper;
import com.elasticsearch.cloud.monitor.metric.alarm.blink.utils.cache.RuleConditionCache;
import com.elasticsearch.cloud.monitor.metric.alarm.blink.utils.TagsUtils;
import com.elasticsearch.cloud.monitor.metric.alarm.blink.utils.cache.RuleConditionKafkaCache;
import com.elasticsearch.cloud.monitor.metric.common.blink.utils.BlinkLogTracer;
import com.elasticsearch.cloud.monitor.metric.common.client.KafkaConfig;
import com.elasticsearch.cloud.monitor.metric.common.rule.RuleManagerFactory;
import com.elasticsearch.cloud.monitor.metric.common.rule.EmonRulesManager;
import com.elasticsearch.cloud.monitor.metric.common.rule.util.RuleUtil;
import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.opensearch.cobble.monitor.Monitor;
import com.taobao.kmonitor.core.MetricsTags;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.java.tuple.Tuple9;
import org.apache.flink.table.functions.FunctionContext;
import org.apache.flink.table.functions.TableFunction;

/**
 * 特别提示: 同一条线, 必须放到同一个shard上且是保序的, 如果分布到多个shard上会有问题!!!
 *
 * @author xingming.xuxm
 * @Date 2019-12-11
 */

@SuppressWarnings("Duplicates")
@Slf4j
public class DurationAlarm
    extends TableFunction<Tuple9<String, String, String, String, String, String, Long, String, String>> {
    private transient RuleManagerFactory ruleManagerFactory;
    private transient Cache<String, RuleConditionCache> ruleConditionCaches;

    /**
     * flink暂时不上报作业的监控指标, 需要提供新的监控数据上报通道
     */
    private Monitor monitor = null;
    private MetricsTags globalTags = null;
    private BlinkLogTracer tracer;
    private KafkaConfig kafkaConfig = null;
    private boolean enableRecoverCache = false;

    /**
     * cache延迟10min, 否则追数据很慢
     */
    private long cacheDelayMs = 10 * 60 * 1000;

    @Override
    public void open(FunctionContext context) throws Exception {
        ruleManagerFactory = RuleUtil.createRuleManagerFactoryForFlink(context);
        long timeout = Long.parseLong(context.getJobParameter(AlarmConstants.CHECKER_CACHE_TIMEOUT_HOUR, "1"));
        ruleConditionCaches = CacheBuilder.newBuilder().expireAfterAccess(timeout, TimeUnit.HOURS).build();

        cacheDelayMs = Long.parseLong(context.getJobParameter(AlarmConstants.CACHE_DELAY_MS, "600000"));

        if (enableRecoverCache) {
            // TODO Kafka恢复数据
            kafkaConfig = new KafkaConfig();
        }

        tracer = new BlinkLogTracer(context);
    }

    public void eval(Long ruleId, String metricName, Long timestamp, Double metricValue, String tagsStr, String granularity) {
        EmonRulesManager rulesManager = ruleManagerFactory.getRuleManager();
        if (rulesManager == null) {
            return;
        }

        Rule rule = rulesManager.getRule(ruleId);
        if (rule == null) {
            return;
        }

        Map<String, String> tags = TagsUtils.toTagsMap(tagsStr);
        long interval = TimeUtils.parseDuration(granularity);
        if (monitor != null) {
            monitor.reportLatency(MetricConstants.ALARM_DATA_DELAY, timestamp, globalTags);
        }

        //这个如果并发情况下 可能会有问题 TODO
        com.elasticsearch.cloud.monitor.commons.core.Constants.CHECK_INTERVAL = interval;

        DataPoint dp = new ImmutableDataPoint(metricName, timestamp, metricValue, tags, granularity);
        RuleConditionCache ruleConditionCache = getRuleConditionCache(rule, interval, dp);
        if (ruleConditionCache == null) {
            log.error(String.format("ruleId %s getRuleConditionCache is null", ruleId));
            return;
        }
        DurationConditionChecker checker = ruleConditionCache.getConditionChecker(dp.getTags());
        ruleConditionCache.put(dp);
        try {
            Alarm alarm = checker.check(dp, ruleConditionCache);
            if (alarm != null && !alarm.isOk()) {
                MetricAlarm metricAlarm = new MetricAlarm();
                metricAlarm.setAlarm(alarm);
                metricAlarm.setRuleId(rule.getId());
                metricAlarm.setTags(dp.getTags());
                metricAlarm.setTimestamp(TimeUtils.toMillisecond(dp.getTimestamp()));
                metricAlarm.setError(false);
                if (rule.getMetric() != null && rule.getMetric().contains("*")) {
                    metricAlarm.setMetric(dp.getName());
                }

                AlarmEvent event = AlarmEventHelper.buildEvent(rule, metricAlarm, this.getClass().getSimpleName());
                collect(
                        Tuple9.of(event.getService(), event.getSource(), StringUtils.join(event.getTags(), ","),
                        event.getText(), event.getTitle(), event.getType(), event.getTime(), event.getGroup(),
                        event.getUid())
                );
                if (monitor != null) {
                    monitor.increment(MetricConstants.ALARM_TRIGGER_COUNT, 1, globalTags);
                }
            }
        } catch (Exception e) {
            if (monitor != null) {
                monitor.increment(MetricConstants.ALARM_ERROR_QPS, 1, globalTags);
            }
            log.error("check failed. ruleid=" + rule.getId() + " " + dp.getTimestamp() + TagUtils.getTag(dp.getTags()),
                e);
        }

        ruleConditionCache.compact(dp.getTimestamp());
    }

    /**
     * 获取当前rule规则 缓存的数据流历史数据，不同的流通过数据流tags区分
     *
     * @param rule 数据关联规则
     * @param interval 流入的数据粒度
     * @param dataPoint 流入的数据点
     * @return
     */
    private RuleConditionCache getRuleConditionCache(Rule rule, long interval, DataPoint dataPoint) {
        String key = getCacheKey(rule);
        RuleConditionCache ruleConditionCache = ruleConditionCaches.getIfPresent(key);
        if (ruleConditionCache == null) {
            ruleConditionCache = new RuleConditionKafkaCache(rule, interval, kafkaConfig);
            ruleConditionCache.setMonitorClient(monitor, globalTags);
            ruleConditionCaches.put(key, ruleConditionCache);
        }
        try {
            // 规则需要的数据时间长度大于缓存数据时间长度, 通常第一次作业启动需要cache数据
            long crossSpan = rule.getDurationCondition().getCrossSpan() - interval;
            if (enableRecoverCache && crossSpan > cacheDelayMs) {
                ruleConditionCache.recovery(dataPoint);
            }
        } catch (Exception ex) {
            //可能是ruleConditionCache.recovery出现异常了
            log.error(String
                .format("fetch cache error. ruleid: %s,error %s ", rule.getId(), Throwables.getStackTraceAsString(ex)));
        }
        return ruleConditionCache;
    }

    /**
     * rule维度的cache, ruleid, tags, 判断条件, 变化都不再是同一个rule
     * 这里没有考虑metric name, 因为metric name 变化了, 基本上tags肯定会变
     *
     * @param rule
     * @return
     */
    private String getCacheKey(Rule rule) {
        return String.format("%d-%d-%s-cached", rule.getId(), rule.getFilterMap().hashCode(),
            rule.getDurationConditionId());
    }

    @Override
    public void close() throws Exception {
        tracer.trace("DurationAlarm close========");

        if (ruleManagerFactory != null) {
            ruleManagerFactory.close();
        }
    }
}
