package com.alibaba.tesla.appmanager.server.service.componentwatchcron;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.tesla.appmanager.common.enums.ComponentInstanceStatusEnum;
import com.alibaba.tesla.appmanager.common.enums.DynamicScriptKindEnum;
import com.alibaba.tesla.appmanager.common.pagination.Pagination;
import com.alibaba.tesla.appmanager.domain.req.rtcomponentinstance.RtComponentInstanceGetStatusReq;
import com.alibaba.tesla.appmanager.domain.res.rtcomponentinstance.RtComponentInstanceGetStatusRes;
import com.alibaba.tesla.appmanager.dynamicscript.core.GroovyHandlerFactory;
import com.alibaba.tesla.appmanager.server.dynamicscript.handler.ComponentHandler;
import com.alibaba.tesla.appmanager.server.dynamicscript.handler.ComponentWatchCronHandler;
import com.alibaba.tesla.appmanager.server.repository.condition.RtComponentInstanceQueryCondition;
import com.alibaba.tesla.appmanager.server.repository.domain.RtComponentInstanceDO;
import com.alibaba.tesla.appmanager.server.service.rtcomponentinstance.RtComponentInstanceService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

/**
 * 定时任务管理器 (用于 Component Watch Cron 用途)
 *
 * @author yaoxing.gyx@alibaba-inc.com
 */
@Service
@Slf4j(topic = "status")
public class ComponentWatchCronManager {

    /**
     * 监听固定类型常量
     */
    private static final String WATCH_KIND = "CRON";

    /**
     * 检查次数边界常量
     */
    private static final Long BORDER_TIMES_5S = 60L;
    private static final Long BORDER_TIMES_10S = 120L;
    private static final Long BORDER_TIMES_30S = 180L;
    private static final Long BORDER_TIMES_1M = 240L;
    private static final Long BORDER_TIMES_2M = 300L;
    private static final Long BORDER_TIMES_3M = 360L;
    private static final Long BORDER_TIMES_4M = 420L;
    private static final Long BORDER_TIMES_5M = 1000000000L;

    /**
     * 检查任务线程池配置
     */
    private static final int CORE_POOL_SIZE = 100;
    private static final int MAXIMUM_POOL_SIZE = 150;
    private static final long KEEP_ALIVE_TIME = 60L;
    private static final int QUEUE_SIZE = 100000;

    /**
     * FAILED 状态检查任务线程池配置
     */
    private static final int FAILED_CORE_POOL_SIZE = 20;
    private static final int FAILED_MAXIMUM_POOL_SIZE = 30;
    private static final int FAILED_QUEUE_SIZE = 200000;

    /**
     * 组件获取状态 (运行中) 常量
     */
    private static final List<String> RUNNING_STATUS_LIST = Arrays.asList(
            ComponentInstanceStatusEnum.PENDING.toString(),
            ComponentInstanceStatusEnum.RUNNING.toString(),
            ComponentInstanceStatusEnum.PREPARING_UPDATE.toString(),
            ComponentInstanceStatusEnum.PREPARING_DELETE.toString(),
            ComponentInstanceStatusEnum.UPDATING.toString(),
            ComponentInstanceStatusEnum.WARNING.toString(),
            ComponentInstanceStatusEnum.ERROR.toString(),
            ComponentInstanceStatusEnum.UNKNOWN.toString()
    );

    /**
     * 组件获取状态 (FAILED) 常量
     */
    private static final List<String> FAILED_STATUS_LIST = Arrays.asList(
            ComponentInstanceStatusEnum.FAILED.toString(),
            ComponentInstanceStatusEnum.EXPIRED.toString()
    );

    private ThreadPoolExecutor threadPoolExecutor;
    private ThreadPoolExecutor failedThreadPoolExecutor;

    private final Object threadPoolExecutorLock = new Object();
    private final Object failedThreadPoolExecutorLock = new Object();

    @Autowired
    private RtComponentInstanceService rtComponentInstanceService;

    @Autowired
    private GroovyHandlerFactory groovyHandlerFactory;

    @PostConstruct
    public void init() {
        synchronized (threadPoolExecutorLock) {
            threadPoolExecutor = new ThreadPoolExecutor(
                    CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_TIME, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(QUEUE_SIZE),
                    r -> new Thread(r, "component-watch-cron-manager-" + r.hashCode()),
                    new ThreadPoolExecutor.AbortPolicy()
            );
        }
        synchronized (failedThreadPoolExecutorLock) {
            failedThreadPoolExecutor = new ThreadPoolExecutor(
                    FAILED_CORE_POOL_SIZE, FAILED_MAXIMUM_POOL_SIZE, KEEP_ALIVE_TIME, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(FAILED_QUEUE_SIZE),
                    r -> new Thread(r, "component-watch-cron-manager-failed-" + r.hashCode()),
                    new ThreadPoolExecutor.AbortPolicy()
            );
        }
    }

    /**
     * 刷新指定组件实例的状态
     *
     * @param componentInstances 组件实例列表
     */
    private void refresh(
            ComponentWatchTypeEnum type, String tag, List<RtComponentInstanceDO> componentInstances)
            throws InterruptedException {
        if (ComponentWatchTypeEnum.NORMAL.equals(type)) {
            synchronized (threadPoolExecutorLock) {
                if (threadPoolExecutor == null) {
                    return;
                }
            }
        } else {
            synchronized (failedThreadPoolExecutorLock) {
                if (failedThreadPoolExecutor == null) {
                    return;
                }
            }
        }

        // 提交组件实例查询请求
        List<Future<UpdateTaskResult>> futures = new ArrayList<>();
        for (RtComponentInstanceDO componentInstance : componentInstances) {
            UpdateTaskResult result = new UpdateTaskResult();
            result.setComponentInstance(componentInstance);
            Future<UpdateTaskResult> future;
            try {
                if (ComponentWatchTypeEnum.NORMAL.equals(type)) {
                    future = threadPoolExecutor.submit(new UpdateTask(result), result);
                } else {
                    future = failedThreadPoolExecutor.submit(new UpdateTask(result), result);
                }
            } catch (RejectedExecutionException e) {
                log.warn("cannot submit component watch cron task to thread pool, rejected|tag={}|" +
                                "componentInstanceId={}appInstanceId={}|appId={}|componentType={}|componentName={}",
                        tag, componentInstance.getComponentInstanceId(), componentInstance.getAppInstanceId(),
                        componentInstance.getAppId(), componentInstance.getComponentType(),
                        componentInstance.getComponentName());
                continue;
            }
            futures.add(future);
        }

        // 等待本轮全部结束
        while (true) {
            int notReadyCount = 0;
            for (Future<UpdateTaskResult> future : futures) {
                if (future.isCancelled()) {
                    continue;
                }
                if (!future.isDone()) {
                    notReadyCount++;
                }
            }
            if (notReadyCount > 0) {
                log.info("current count for not ready watch cron task is {}|size={}|tag={}",
                        notReadyCount, futures.size(), tag);
                Thread.sleep(1000);
                continue;
            }
            for (Future<UpdateTaskResult> future : futures) {
                try {
                    UpdateTaskResult fr = future.get();
                    RtComponentInstanceDO ci = fr.getComponentInstance();
                    String logSuffix = String.format("tag=%s|appInstanceId=%s|componentInstanceId=%s|appId=%s|" +
                                    "clusterId=%s|namespaceId=%s|stageId=%s|componentType=%s|componentName=%s|" +
                                    "version=%s|message=%s", tag,
                            ci.getAppInstanceId(), ci.getComponentInstanceId(), ci.getAppId(), ci.getClusterId(),
                            ci.getNamespaceId(), ci.getStageId(), ci.getComponentType(), ci.getComponentName(),
                            ci.getVersion(), fr.getMessage());
                    if (!fr.isSuccess()) {
                        log.warn("cannot refresh component instance status|{}", logSuffix);
                    } else {
                        log.info("refresh component instance success|{}|result={}", logSuffix,
                                JSONObject.toJSONString(fr.getResult()));
                    }
                } catch (InterruptedException | ExecutionException e) {
                    log.warn("failed to refresh component instance status|exception={}", ExceptionUtils.getStackTrace(e));
                }
            }
            return;
        }
    }

    /**
     * 5s 定时工作
     */
    @Scheduled(cron = "${appmanager.cron-job.cron-job-manager-refresh-5s:0/5 * * * * *}")
    @SchedulerLock(name = "cronJobManagerFactoryRefresh5s")
    public void refresh5s() throws InterruptedException {
        Pagination<RtComponentInstanceDO> componentInstances = rtComponentInstanceService
                .list(RtComponentInstanceQueryCondition.builder()
                        .statusList(RUNNING_STATUS_LIST)
                        .watchKind(WATCH_KIND)
                        .timesGreaterThan(0L)
                        .timesLessThan(BORDER_TIMES_5S)
                        .build());
        refresh(ComponentWatchTypeEnum.NORMAL, "5s", componentInstances.getItems());
    }

    /**
     * 10s 定时工作
     */
    @Scheduled(cron = "${appmanager.cron-job.cron-job-manager-refresh-10s:0/10 * * * * *}")
    @SchedulerLock(name = "cronJobManagerFactoryRefresh10s")
    public void refresh10s() throws InterruptedException {
        Pagination<RtComponentInstanceDO> componentInstances = rtComponentInstanceService
                .list(RtComponentInstanceQueryCondition.builder()
                        .statusList(RUNNING_STATUS_LIST)
                        .watchKind(WATCH_KIND)
                        .timesGreaterThan(BORDER_TIMES_5S + 1)
                        .timesLessThan(BORDER_TIMES_10S)
                        .build());
        refresh(ComponentWatchTypeEnum.NORMAL, "10s", componentInstances.getItems());
    }

    /**
     * 30s 定时工作
     */
    @Scheduled(cron = "${appmanager.cron-job.cron-job-manager-refresh-30s:0/30 * * * * *}")
    @SchedulerLock(name = "cronJobManagerFactoryRefresh30s")
    public void refresh30s() throws InterruptedException {
        Pagination<RtComponentInstanceDO> componentInstances = rtComponentInstanceService
                .list(RtComponentInstanceQueryCondition.builder()
                        .statusList(RUNNING_STATUS_LIST)
                        .watchKind(WATCH_KIND)
                        .timesGreaterThan(BORDER_TIMES_10S + 1)
                        .timesLessThan(BORDER_TIMES_30S)
                        .build());
        refresh(ComponentWatchTypeEnum.NORMAL, "30s", componentInstances.getItems());
    }

    /**
     * 1m 定时工作
     */
    @Scheduled(cron = "${appmanager.cron-job.cron-job-manager-refresh-1m:0 0/1 * * * *}")
    @SchedulerLock(name = "cronJobManagerFactoryRefresh1m")
    public void refresh1m() throws InterruptedException {
        Pagination<RtComponentInstanceDO> componentInstances = rtComponentInstanceService
                .list(RtComponentInstanceQueryCondition.builder()
                        .statusList(RUNNING_STATUS_LIST)
                        .watchKind(WATCH_KIND)
                        .timesGreaterThan(BORDER_TIMES_30S + 1)
                        .timesLessThan(BORDER_TIMES_1M)
                        .build());
        refresh(ComponentWatchTypeEnum.NORMAL, "1m", componentInstances.getItems());
    }

    /**
     * 2m 定时工作
     */
    @Scheduled(cron = "${appmanager.cron-job.cron-job-manager-refresh-2m:0 0/2 * * * *}")
    @SchedulerLock(name = "cronJobManagerFactoryRefresh2m")
    public void refresh2m() throws InterruptedException {
        Pagination<RtComponentInstanceDO> componentInstances = rtComponentInstanceService
                .list(RtComponentInstanceQueryCondition.builder()
                        .statusList(RUNNING_STATUS_LIST)
                        .watchKind(WATCH_KIND)
                        .timesGreaterThan(BORDER_TIMES_1M + 1)
                        .timesLessThan(BORDER_TIMES_2M)
                        .build());
        refresh(ComponentWatchTypeEnum.NORMAL, "2m", componentInstances.getItems());
    }

    /**
     * 3m 定时工作
     */
    @Scheduled(cron = "${appmanager.cron-job.cron-job-manager-refresh-3m:0 0/3 * * * *}")
    @SchedulerLock(name = "cronJobManagerFactoryRefresh3m")
    public void refresh3m() throws InterruptedException {
        Pagination<RtComponentInstanceDO> componentInstances = rtComponentInstanceService
                .list(RtComponentInstanceQueryCondition.builder()
                        .statusList(RUNNING_STATUS_LIST)
                        .watchKind(WATCH_KIND)
                        .timesGreaterThan(BORDER_TIMES_2M + 1)
                        .timesLessThan(BORDER_TIMES_3M)
                        .build());
        refresh(ComponentWatchTypeEnum.NORMAL, "3m", componentInstances.getItems());
    }

    /**
     * 4m 定时工作
     */
    @Scheduled(cron = "${appmanager.cron-job.cron-job-manager-refresh-4m:0 0/4 * * * *}")
    @SchedulerLock(name = "cronJobManagerFactoryRefresh4m")
    public void refresh4m() throws InterruptedException {
        Pagination<RtComponentInstanceDO> componentInstances = rtComponentInstanceService
                .list(RtComponentInstanceQueryCondition.builder()
                        .statusList(RUNNING_STATUS_LIST)
                        .watchKind(WATCH_KIND)
                        .timesGreaterThan(BORDER_TIMES_3M + 1)
                        .timesLessThan(BORDER_TIMES_4M)
                        .build());
        refresh(ComponentWatchTypeEnum.NORMAL, "4m", componentInstances.getItems());
    }

    /**
     * 5m 定时工作
     */
    @Scheduled(cron = "${appmanager.cron-job.cron-job-manager-refresh-5m:0 0/5 * * * *}")
    @SchedulerLock(name = "cronJobManagerFactoryRefresh5m")
    public void refresh5m() throws InterruptedException {
        Pagination<RtComponentInstanceDO> componentInstances = rtComponentInstanceService
                .list(RtComponentInstanceQueryCondition.builder()
                        .statusList(RUNNING_STATUS_LIST)
                        .watchKind(WATCH_KIND)
                        .timesGreaterThan(BORDER_TIMES_4M + 1)
                        .timesLessThan(BORDER_TIMES_5M)
                        .build());
        refresh(ComponentWatchTypeEnum.NORMAL, "5m", componentInstances.getItems());
    }

    /**
     * 1h 定时工作 (FAILED)
     */
    @Scheduled(cron = "${appmanager.cron-job.cron-job-manager-refresh-5m:0 0 0/1 * * *}")
    @SchedulerLock(name = "cronJobManagerFactoryRefresh1h")
    public void refresh1h() throws InterruptedException {
        Pagination<RtComponentInstanceDO> componentInstances = rtComponentInstanceService
                .list(RtComponentInstanceQueryCondition.builder()
                        .statusList(FAILED_STATUS_LIST)
                        .watchKind(WATCH_KIND)
                        .timesGreaterThan(0L)
                        .timesLessThan(BORDER_TIMES_5M)
                        .build());
        refresh(ComponentWatchTypeEnum.FAILED, "1h", componentInstances.getItems());
    }

    /**
     * 更新状态任务
     */
    class UpdateTask implements Runnable {

        private final UpdateTaskResult result;

        UpdateTask(UpdateTaskResult result) {
            this.result = result;
        }

        @Override
        public void run() {
            try {
                RtComponentInstanceDO componentInstance = result.getComponentInstance();
                String componentType = componentInstance.getComponentType();
                String watchKind = componentInstance.getWatchKind();

                // 获取组件 Handler 对象，确认 watch kind 类型，并得知当前需要请求的 groovy script 定位名称
                ComponentHandler componentHandler = groovyHandlerFactory
                        .get(ComponentHandler.class, DynamicScriptKindEnum.COMPONENT.toString(), componentType);
                if (!WATCH_KIND.equals(watchKind)) {
                    String errorMessage = String.format("component instance watch kind is not equal to current" +
                            " component handler settings|componentType=%s|watchKind=%s", componentType, watchKind);
                    log.warn(errorMessage);
                    result.setSuccess(false);
                    result.setMessage(errorMessage);
                    return;
                }

                // 根据 ComponentHandler 中提供的 groovy script 定位名称获取 handler 对象，并查询状态值
                ComponentWatchCronHandler componentWatchCronHandler = groovyHandlerFactory.get(
                        ComponentWatchCronHandler.class,
                        DynamicScriptKindEnum.COMPONENT_WATCH_CRON.toString(),
                        componentHandler.watchScriptName());
                RtComponentInstanceGetStatusRes res = componentWatchCronHandler
                        .get(RtComponentInstanceGetStatusReq.builder()
                                .clusterId(componentInstance.getClusterId())
                                .namespaceId(componentInstance.getNamespaceId())
                                .stageId(componentInstance.getStageId())
                                .appId(componentInstance.getAppId())
                                .componentType(componentType)
                                .componentName(componentInstance.getComponentName())
                                .version(componentInstance.getVersion())
                                .build());
                String status = res.getStatus();
                JSONArray conditions = res.getConditions();

                // 上报数据
                componentInstance.setStatus(status);
                componentInstance.setConditions(JSONArray.toJSONString(conditions));
                if (componentInstance.getTimes() != null) {
                    componentInstance.setTimes(componentInstance.getTimes() + 1);
                } else {
                    componentInstance.setTimes(0L);
                }
                rtComponentInstanceService.reportRaw(componentInstance);
                result.setSuccess(true);
                result.setMessage("");
            } catch (Throwable e) {
                result.setSuccess(false);
                result.setMessage(ExceptionUtils.getStackTrace(e));
            }
        }
    }

    /**
     * 用于主线程和子线程交互结果的对象
     */
    @Data
    static class UpdateTaskResult {

        private RtComponentInstanceGetStatusRes result;

        private RtComponentInstanceDO componentInstance;

        private boolean success;

        private String message;
    }
}
