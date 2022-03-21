package com.alibaba.sreworks.job.worker.services;

import java.net.http.HttpResponse;

import javax.annotation.PostConstruct;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.sreworks.job.utils.HostUtil;
import com.alibaba.sreworks.job.utils.JsonUtil;
import com.alibaba.sreworks.job.utils.Requests;
import com.alibaba.sreworks.job.worker.configs.SreworksJobProperties;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HeartBeatService {

    @Autowired
    Environment environment;

    @Autowired
    SreworksJobProperties sreworksJobProperties;

    @Autowired
    TaskHandlerService taskHandlerService;

    @Autowired
    TaskInstanceService taskInstanceService;

    @Value("${server.port}")
    private Long serverPort;

    @PostConstruct
    public void init() throws Exception {
        report();
    }

    @Scheduled(fixedRate = 10000)
    public void report() throws Exception {

        String address = String.format("http://%s:%s", HostUtil.LOCAL_HOST, serverPort);
        HttpResponse<String> response = Requests.post(
            sreworksJobProperties.getMasterEndpoint() + "/listen/worker",
            null,
            null,
            JsonUtil.map(
                "address", address,
                "execTypeList", taskHandlerService.taskHandlerMap.keySet()
            ).toJSONString()
        );
        Requests.checkResponseStatus(response);
        String body = response.body();
        int poolSize = JSONObject.parseObject(body).getJSONObject("data").getIntValue("poolSize");
        taskInstanceService.runningExecutor.setCorePoolSize(poolSize);
        taskInstanceService.runningExecutor.setMaxPoolSize(poolSize);
    }

}
