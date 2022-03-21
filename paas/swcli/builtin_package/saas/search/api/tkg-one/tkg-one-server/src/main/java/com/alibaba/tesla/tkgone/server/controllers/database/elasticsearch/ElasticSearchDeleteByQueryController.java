package com.alibaba.tesla.tkgone.server.controllers.database.elasticsearch;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.tesla.common.base.TeslaBaseResult;
import com.alibaba.tesla.web.controller.BaseController;
import com.alibaba.tesla.tkgone.server.services.database.elasticsearch.ElasticSearchDeleteByQueryService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author jialiang.tjl
 * @date 2018/08/24 说明:
 * <p>
 * <p>
 */

@RestController
@RequestMapping(value = "/data/elasticsearch")
public class ElasticSearchDeleteByQueryController extends BaseController {

    @Autowired
    ElasticSearchDeleteByQueryService elasticSearchDeleteByQueryService;

    @RequestMapping(value = "/deleteByKv", method = RequestMethod.DELETE)
    public TeslaBaseResult deleteByKv(@RequestBody JSONObject jsonObject,
                                      @RequestParam String index) throws Exception {
        elasticSearchDeleteByQueryService.deleteByKvFull(index, false, jsonObject);
        return buildSucceedResult("started");
    }

}
