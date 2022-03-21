package com.elasticsearch.cloud.monitor.metric.alarm.blink.udaf;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.elasticsearch.cloud.monitor.commons.checker.nodata.NoDataConditionChecker;
import com.elasticsearch.cloud.monitor.commons.core.Alarm;
import com.elasticsearch.cloud.monitor.commons.core.MetricAlarm;
import com.elasticsearch.cloud.monitor.commons.rule.Rule;
import com.elasticsearch.cloud.monitor.commons.rule.RuleManagerFactory;
import com.elasticsearch.cloud.monitor.commons.rule.RulesManager;
import com.elasticsearch.cloud.monitor.commons.rule.filter.TagVFilter;
import com.elasticsearch.cloud.monitor.metric.alarm.blink.constant.AlarmConstants;
import com.elasticsearch.cloud.monitor.metric.alarm.blink.constant.MetricConstants;
import com.elasticsearch.cloud.monitor.metric.alarm.blink.utils.AlarmEvent;
import com.elasticsearch.cloud.monitor.metric.alarm.blink.utils.AlarmEventHelper;
import com.elasticsearch.cloud.monitor.metric.alarm.blink.utils.TagsUtils;
import com.elasticsearch.cloud.monitor.metric.common.blink.utils.BlinkLogTracer;
import com.elasticsearch.cloud.monitor.metric.common.blink.utils.BlinkTagsUtil;
import com.elasticsearch.cloud.monitor.metric.common.monitor.kmon.KmonCreatorForBlink;
import com.elasticsearch.cloud.monitor.metric.common.rule.util.RuleUtil;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.opensearch.cobble.monitor.Monitor;
import com.taobao.kmonitor.core.MetricsTags;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.flink.table.functions.AggregateFunction;
import org.apache.flink.table.functions.FunctionContext;

/**
 * @author xingming.xuxm
 * @Date 2019-11-26
 */
@Slf4j
public class NoDataAlarm extends AggregateFunction<List<AlarmEvent>, NoDataAlarm.NoDataAlarmAccumulator> {
    private transient Cache<String, NoDataConditionChecker> checkerCache;
    private transient RuleManagerFactory ruleManagerFactory;
    private transient MetricsTags tags;
    private transient Monitor monitor;
    private transient BlinkLogTracer tracer;
    private transient Map<String, Map<Long, Map<Long, Map<String, Set<String>>>>> windowRuleSingleTagValueMap;
    private transient Map<String, Map<Long, Map<Long, Set<Map<String, String>>>>> windowRuleMultiTagValueMap;
    private transient MultiTagNoDataInfoSync noDataInfoSync;

    /**
     * 配置了no data, 但是实际上来数据了的rule id
     */
    private transient Map<String, Map<Long, Set<Long>>> tenantTimestampRuleIds;

    @SuppressWarnings("Duplicates")
    @Override
    public void open(FunctionContext context) throws Exception {
        long timeout = Long.parseLong(context.getJobParameter(AlarmConstants.CHECKER_CACHE_TIMEOUT_HOUR, "1"));
        checkerCache = CacheBuilder.newBuilder()
            .expireAfterAccess(timeout, TimeUnit.HOURS).build();
        ruleManagerFactory = RuleUtil.createRuleManagerFactory(context);

        monitor = KmonCreatorForBlink.getMonitor(context, this.getClass().getSimpleName());
        monitor.registerGauge(MetricConstants.ALARM_DATA_DELAY);
        monitor.registerQPS(MetricConstants.ALARM_ERROR_QPS);
        monitor.registerCounter(MetricConstants.ALARM_TRIGGER_COUNT);
        windowRuleSingleTagValueMap = Maps.newConcurrentMap();
        windowRuleMultiTagValueMap = Maps.newConcurrentMap();
        tenantTimestampRuleIds = Maps.newConcurrentMap();

        if (Boolean.valueOf(context.getJobParameter(AlarmConstants.NODATA_OSS_ENABLE, "false"))) {
            noDataInfoSync = new MultiTagNoDataInfoSync(context.getJobParameter(AlarmConstants.NODATA_OSS_ENDPOINT, ""),
                context.getJobParameter(AlarmConstants.NODATA_OSS_ACCESS_KEY, ""),
                context.getJobParameter(AlarmConstants.NODATA_OSS_ACCESS_SECRET, ""),
                context.getJobParameter(AlarmConstants.NODATA_OSS_BUCKET, ""),
                context.getJobParameter(AlarmConstants.NODATA_OSS_FILE, ""));
        }

        tags = BlinkTagsUtil.getTags(context, this.getClass().getSimpleName());
        tracer = new BlinkLogTracer(context);
        tracer.trace("NoDataAlarm init========");
    }

    @Override
    public void close() throws Exception {
        tracer.trace("NoDataAlarm close========");
        if (ruleManagerFactory != null) {
            ruleManagerFactory.close();
        }
        if (noDataInfoSync != null) {
            noDataInfoSync.close();
        }
    }

    @Override
    public NoDataAlarmAccumulator createAccumulator() {
        return new NoDataAlarmAccumulator();
    }

    @Override
    public List<AlarmEvent> getValue(NoDataAlarmAccumulator alarmAcc) {
        if (monitor != null) {
            monitor.reportLatency(MetricConstants.ALARM_DATA_DELAY, alarmAcc.getTimestamp(), tags);
        }

        Set<Long> comeRuleIds = Sets.newConcurrentHashSet();
        if (tenantTimestampRuleIds.containsKey(alarmAcc.getTenant()) && tenantTimestampRuleIds.get(
            alarmAcc.getTenant()).containsKey(alarmAcc.getTimestamp())) {
            comeRuleIds = tenantTimestampRuleIds.get(alarmAcc.getTenant()).get(alarmAcc.getTimestamp());
        }
        List<AlarmEvent> out = new ArrayList<>();
        RulesManager rulesManager = ruleManagerFactory.getRuleManager(alarmAcc.getTenant());
        for (Rule rule : rulesManager.getAllRules()) {
            if (rule.getNoDataCondition() == null) {
                continue;
            }
            rewriteRule(rule);
            Map<String, Set<String>> occurTags = null;
            Set<Map<String, String>> occurMultiTags = null;
            long window = alarmAcc.getTimestamp();
            if (windowRuleSingleTagValueMap.containsKey(alarmAcc.getTenant()) && windowRuleSingleTagValueMap.get(
                alarmAcc.getTenant()).containsKey(window) && windowRuleSingleTagValueMap.get(alarmAcc.getTenant()).get(
                window).containsKey(rule.getId())) {
                occurTags = windowRuleSingleTagValueMap.get(alarmAcc.getTenant()).get(window).get(rule.getId());
            }

            if (windowRuleMultiTagValueMap.containsKey(alarmAcc.getTenant()) && windowRuleMultiTagValueMap.get(
                alarmAcc.getTenant()).containsKey(window) && windowRuleMultiTagValueMap.get(alarmAcc.getTenant()).get(
                window).containsKey(rule.getId())) {
                occurMultiTags = windowRuleMultiTagValueMap.get(alarmAcc.getTenant()).get(window).get(rule.getId());
            }

            NoDataConditionChecker checker = getNoDataConditionChecker(rule);
            List<Alarm> alarms = null;

            try {
                alarms = checker.check(comeRuleIds, rule, occurTags, occurMultiTags);
            } catch (Throwable e) {
                log.error("nodata check failed", e);
                if (monitor != null) {
                    monitor.increment(MetricConstants.ALARM_ERROR_QPS, 1, tags);
                }
            }
            if (alarms != null) {
                for (Alarm alarm : alarms) {
                    MetricAlarm metricAlarm = new MetricAlarm();
                    metricAlarm.setAlarm(alarm);
                    metricAlarm.setRuleId(rule.getId());
                    metricAlarm.setTimestamp(alarmAcc.getTimestamp() / (60 * 1000) * (60 * 1000));
                    metricAlarm.setError(false);
                    AlarmEvent event = AlarmEventHelper.buildEvent(rule, metricAlarm, this.getClass().getSimpleName());
                    out.add(event);
                }
            }
        }
        if (windowRuleSingleTagValueMap.containsKey(alarmAcc.getTenant())) {
            windowRuleSingleTagValueMap.get(alarmAcc.getTenant()).remove(alarmAcc.getTimestamp());
        }
        if (windowRuleMultiTagValueMap.containsKey(alarmAcc.getTenant())) {
            windowRuleMultiTagValueMap.get(alarmAcc.getTenant()).remove(alarmAcc.getTimestamp());
        }
        if (tenantTimestampRuleIds.containsKey(alarmAcc.getTenant())) {
            tenantTimestampRuleIds.remove(alarmAcc.getTimestamp());
        }

        if (monitor != null) {
            monitor.increment(MetricConstants.ALARM_TRIGGER_COUNT, out.size(), tags);
        }
        return out;
    }

    private NoDataConditionChecker getNoDataConditionChecker(Rule rule) {
        String key = rule.getNoDataConditionId();
        NoDataConditionChecker checker = checkerCache.getIfPresent(key);
        if (checker == null) {
            Map<String, TagVFilter> tagVFilterMap = null;
            if (rule.getMetricCompose() != null && rule.getMetricCompose().getMetrics() != null
                && rule.getMetricCompose().getMetrics().size() > 0) {
                tagVFilterMap = rule.getMetricCompose().getMetrics().get(0).getFilterMap();
            }
            if (tagVFilterMap == null) {
                tagVFilterMap = rule.getFilterMap();
            }
            checker = rule.getNoDataCondition().getChecker(tagVFilterMap);
            checkerCache.put(key, checker);
        }
        return checker;
    }

    public void accumulate(NoDataAlarmAccumulator acc, long ruleId, long windowStart, String tenant, String tagString) {
        RulesManager rulesManager = ruleManagerFactory.getRuleManager(tenant);
        if (StringUtils.isEmpty(tenant)) {
            tenant = "default";
        }
        Rule rule = rulesManager.getRule(ruleId);
        if (rule != null && rule.getNoDataCondition() != null) {
            //tracer.trace("nodata rule: {},{},{}", ruleId, windowStart, granularity);
            //TODO 如果报警数据 同时支持多种精度, 需要考虑granularity
            rewriteRule(rule);
            // acc.addRuleId(ruleId);
            Map<Long, Set<Long>> tenantRuleIds = tenantTimestampRuleIds.get(tenant);
            if (tenantRuleIds == null) {
                tenantRuleIds = Maps.newConcurrentMap();
                tenantTimestampRuleIds.put(tenant, tenantRuleIds);
            }
            Set<Long> ruleIds = tenantRuleIds.get(windowStart);
            if (ruleIds == null) {
                ruleIds = Sets.newConcurrentHashSet();
                tenantRuleIds.put(windowStart, ruleIds);
            }
            ruleIds.add(ruleId);

            Set<String> multiTagNoDataKeys = rule.getNoDataCondition().getNoDataLineTagKeys();
            if (multiTagNoDataKeys != null && multiTagNoDataKeys.size() > 0) {

                Map<String, String> tags = TagsUtils.toTagsMap(tagString);
                Map<String, String> lineTag = Maps.newConcurrentMap();
                for (String key : multiTagNoDataKeys) {
                    lineTag.put(key, tags.getOrDefault(key, ""));
                }

                Map<Long, Map<Long, Set<Map<String, String>>>> map = windowRuleMultiTagValueMap.get(tenant);
                if (map == null) {
                    map = Maps.newConcurrentMap();
                    windowRuleMultiTagValueMap.put(tenant, map);
                }

                Map<Long, Set<Map<String, String>>> ruleTagValueMap = map.get(windowStart);
                if (ruleTagValueMap == null) {
                    ruleTagValueMap = Maps.newConcurrentMap();
                    map.put(windowStart, ruleTagValueMap);
                }

                Set<Map<String, String>> tagValues = ruleTagValueMap.get(ruleId);
                if (tagValues == null) {
                    tagValues = Sets.newConcurrentHashSet();
                    ruleTagValueMap.put(ruleId, tagValues);
                }
                tagValues.add(lineTag);

            } else {
                if (rule.getNoDataCondition().getIsSeriesGroupBy()) {

                    Map<String, String> tags = TagsUtils.toTagsMap(tagString);
                    Map<Long, Map<Long, Map<String, Set<String>>>> map = windowRuleSingleTagValueMap.get(tenant);
                    if (map == null) {
                        map = Maps.newConcurrentMap();
                        windowRuleSingleTagValueMap.put(tenant, map);
                    }

                    Map<Long, Map<String, Set<String>>> ruleTagValueMap = map.get(windowStart);
                    if (ruleTagValueMap == null) {
                        ruleTagValueMap = Maps.newConcurrentMap();
                        map.put(windowStart, ruleTagValueMap);
                    }

                    Map<String, Set<String>> tagValues = ruleTagValueMap.get(ruleId);
                    if (tagValues == null) {
                        tagValues = Maps.newConcurrentMap();
                        ruleTagValueMap.put(ruleId, tagValues);
                    }
                    if (tags != null) {
                        for (Entry<String, String> entry : tags.entrySet()) {
                            Set<String> values = tagValues.get(entry.getKey());
                            if (values == null) {
                                values = Sets.newConcurrentHashSet();
                                tagValues.put(entry.getKey(), values);
                            }
                            values.add(entry.getValue());
                        }
                    }
                }
            }

        }
        acc.setTenant(tenant);
        acc.setTimestamp(windowStart);
    }

    private void rewriteRule(Rule rule) {
        try {
            if (noDataInfoSync != null) {
                noDataInfoSync.rewriteRule(rule);
            }
        } catch (Throwable t) {
            log.error("rewrite rule error %s", t.getMessage(), t);
        }

    }

    @Data
    public static class NoDataAlarmAccumulator {
        /**
         * ms
         */
        private long timestamp = 0;

        private String tenant;

    }
}
