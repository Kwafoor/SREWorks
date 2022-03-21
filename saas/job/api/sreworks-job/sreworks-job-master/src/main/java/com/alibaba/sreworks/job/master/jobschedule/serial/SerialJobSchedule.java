package com.alibaba.sreworks.job.master.jobschedule.serial;

import java.util.stream.Collectors;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.sreworks.job.master.domain.DO.SreworksJobTask;
import com.alibaba.sreworks.job.master.domain.DTO.SreworksJobTaskDTO;
import com.alibaba.sreworks.job.master.domain.repository.SreworksJobTaskRepository;
import com.alibaba.sreworks.job.master.jobschedule.AbstractJobSchedule;
import com.alibaba.sreworks.job.master.jobschedule.dag.DagContentService;
import com.alibaba.sreworks.job.utils.JsonUtil;
import com.alibaba.tesla.dag.model.domain.TcDag;
import com.alibaba.tesla.dag.model.domain.TcDagInst;
import com.alibaba.tesla.dag.model.repository.TcDagRepository;
import com.alibaba.tesla.dag.services.DagInstService;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@EqualsAndHashCode(callSuper = true)
@Data
@Service
@Slf4j
public class SerialJobSchedule extends AbstractJobSchedule<SerialJobScheduleConf> {

    public String type = "serial";

    private String name(Long id) {
        return "job" + id;
    }

    @Autowired
    TcDagRepository dagRepository;

    @Autowired
    DagContentService dagContentService;

    @Autowired
    DagInstService dagInstService;

    @Autowired
    SreworksJobTaskRepository taskRepository;

    @Override
    public Class<SerialJobScheduleConf> getConfClass() {
        return SerialJobScheduleConf.class;
    }

    @Override
    public JSONObject getConf(Long id) throws Exception {
        TcDag dag = dagRepository.findFirstByAppIdAndName("tesla", name(id));
        SerialJobScheduleConf conf = dagContentService.serialJobScheduleConf(dag.contentJson());
        return JsonUtil.map(
            "taskIdList", conf.getTaskIdList().stream()
                .map(taskId -> new SreworksJobTaskDTO(taskRepository.findFirstById(taskId)))
                .collect(Collectors.toList())
        );
    }

    @Override
    public void create(Long id, SerialJobScheduleConf conf) throws Exception {
        TcDag dag = TcDag.builder()
            .gmtCreate(System.currentTimeMillis() / 1000)
            .gmtModified(System.currentTimeMillis() / 1000)
            .appId("tesla")
            .name(name(id))
            .content(dagContentService.dagContent(conf))
            .lastUpdateBy("")
            .build();
        dagRepository.saveAndFlush(dag);
    }

    @Override
    public void delete(Long id) {
        dagRepository.deleteByAppIdAndName("tesla", name(id));
    }

    @Override
    public void modify(Long id, SerialJobScheduleConf conf) throws Exception {
        TcDag dag = dagRepository.findFirstByAppIdAndName("tesla", name(id));
        dag.setContent(dagContentService.dagContent(conf));
        dagRepository.saveAndFlush(dag);
    }

    @Override
    public Long start(Long id, JSONObject varConf) throws Exception {
        return dagInstService.start(name(id), varConf, true);
    }

    @Override
    public void stop(Long dagInstId) {
        dagInstService.stop(dagInstId);
    }

    @Override
    public TcDagInst get(Long dagInstId) {
        return dagInstService.get(dagInstId);
    }
}
