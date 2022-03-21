package com.alibaba.sreworks.job.master.jobtrigger.quartz;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.alibaba.tesla.common.base.TeslaBaseResult;
import com.alibaba.tesla.web.controller.BaseController;

import lombok.extern.slf4j.Slf4j;
import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.TriggerBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Service
public class QuartzService {

    public void checkCron(String cron) throws Exception {
        if (!CronExpression.isValidExpression(cron)) {
            throw new Exception(cron + "is valid expression");
        }
    }

    public List<Long> getNextTriggerTime(String cron, int size) throws Exception {

        checkCron(cron);
        List<Long> ret = new ArrayList<>();
        CronTrigger trigger = TriggerBuilder.newTrigger()
            .withSchedule(CronScheduleBuilder.cronSchedule(cron)).build();
        Date time = new Date();
        for (int i = 0; i < size; i++) {
            time = trigger.getFireTimeAfter(time);
            ret.add(time.getTime());
        }
        return ret;

    }

}
