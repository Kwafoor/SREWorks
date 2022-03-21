package com.alibaba.tesla.appmanager.server.controller;

import com.alibaba.tesla.appmanager.api.provider.AppMetaProvider;
import com.alibaba.tesla.appmanager.api.provider.DeployConfigProvider;
import com.alibaba.tesla.appmanager.common.constants.DefaultConstant;
import com.alibaba.tesla.appmanager.common.pagination.Pagination;
import com.alibaba.tesla.appmanager.common.util.SchemaUtil;
import com.alibaba.tesla.appmanager.domain.dto.AppMetaDTO;
import com.alibaba.tesla.appmanager.domain.req.AppMetaQueryReq;
import com.alibaba.tesla.appmanager.domain.req.AppMetaUpdateReq;
import com.alibaba.tesla.appmanager.domain.req.deployconfig.DeployConfigApplyTemplateReq;
import com.alibaba.tesla.appmanager.domain.req.deployconfig.DeployConfigDeleteReq;
import com.alibaba.tesla.appmanager.domain.req.deployconfig.DeployConfigGenerateReq;
import com.alibaba.tesla.appmanager.domain.res.appmeta.AppGetVersionRes;
import com.alibaba.tesla.appmanager.domain.res.apppackage.ApplicationConfigurationGenerateRes;
import com.alibaba.tesla.appmanager.domain.res.deployconfig.DeployConfigGenerateRes;
import com.alibaba.tesla.common.base.TeslaBaseResult;
import com.alibaba.tesla.web.controller.BaseController;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 应用元信息 Controller
 *
 * @author qianmo.zm@alibaba-inc.com
 */
@RequestMapping("/apps")
@RestController
@Slf4j
public class AppController extends BaseController {

    @Autowired
    private AppMetaProvider appMetaProvider;

    @Autowired
    private DeployConfigProvider deployConfigProvider;

    /**
     * @api {get} /apps 获取应用列表
     * @apiName GetAppList
     * @apiGroup 应用 API
     * @apiParam (GET Parameters) {Number} page 当前页
     * @apiParam (GET Parameters) {Number} pageSize 每页大小
     */
    @GetMapping
    public TeslaBaseResult list(AppMetaQueryReq request) {
        Pagination<AppMetaDTO> pagination = appMetaProvider.list(request);
        return buildSucceedResult(pagination);
    }

    /**
     * @api {post} /apps 新增应用
     * @apiName PostApp
     * @apiGroup 应用 API
     * @apiParam (JSON Body) {String} appId 应用 ID
     * @apiParam (JSON Body) {Object} options 应用扩展信息
     */
    @PostMapping
    public TeslaBaseResult create(@RequestBody AppMetaUpdateReq request) {
        AppMetaDTO result = appMetaProvider.save(request);
        return buildSucceedResult(result);
    }

    /**
     * @api {get} /apps/:appId 获取指定应用信息
     * @apiName GetApp
     * @apiGroup 应用 API
     * @apiParam (Path Parameters) {String} appId 应用 ID
     */
    @GetMapping(value = "/{appId}")
    public TeslaBaseResult get(@PathVariable String appId) {
        AppMetaDTO result = appMetaProvider.get(appId);
        if (result == null) {
            return buildClientErrorResult(String.format("cannot find app %s", appId));
        }
        return buildSucceedResult(result);
    }

    /**
     * @api {get} /apps/:appId/version 获取指定应用的所属版本 v1 or v2
     * @apiName GetApp
     * @apiGroup 应用 API
     * @apiParam (Path Parameters) {String} appId 应用 ID
     */
    @GetMapping(value = "/{appId}/version")
    public TeslaBaseResult getVersion(@PathVariable String appId) {
        AppMetaDTO result = appMetaProvider.get(appId);
        if (result == null || result.getOptions() == null) {
            // 不存在默认返回 v1
            return buildSucceedResult(AppGetVersionRes.builder().version("v1").build());
        }
        String version = result.getOptions().getString("version");
        if (StringUtils.isEmpty(version)) {
            return buildSucceedResult(AppGetVersionRes.builder().version("v1").build());
        }
        return buildSucceedResult(AppGetVersionRes.builder().version(version).build());
    }

    /**
     * @api {put} /apps/:appId 更新指定应用信息
     * @apiName PutApp
     * @apiGroup 应用 API
     * @apiParam (Path Parameters) {String} appId 应用 ID
     * @apiParam (JSON Body) {Object} options 应用扩展信息
     */
    @PutMapping(value = "/{appId}")
    public TeslaBaseResult update(@PathVariable String appId, @RequestBody AppMetaUpdateReq request) {
        request.setAppId(appId);
        AppMetaDTO result = appMetaProvider.save(request);
        return buildSucceedResult(result);
    }

    /**
     * @api {put} /apps 更新指定应用信息
     * @apiName PutApp
     * @apiGroup 应用 API
     * @apiParam (Path Parameters) {String} appId 应用 ID
     * @apiParam (JSON Body) {Object} options 应用扩展信息
     */
    @PutMapping
    public TeslaBaseResult updateCompatible(@RequestBody AppMetaUpdateReq request) {
        AppMetaDTO result = appMetaProvider.save(request);
        return buildSucceedResult(result);
    }

    /**
     * @api {delete} /apps/:appId 删除指定应用信息
     * @apiName DeleteApp
     * @apiGroup 应用 API
     * @apiParam (Path Parameters) {String} appId 应用 ID
     */
    @DeleteMapping(value = "/{appId}")
    public TeslaBaseResult delete(@PathVariable String appId) {
        if (StringUtils.isEmpty(appId)) {
            return buildSucceedResult(Boolean.TRUE);
        }

        boolean result = appMetaProvider.delete(appId);
        return buildSucceedResult(result);
    }

    /**
     * @api {put} /apps/:appId/application-configurations 更新指定应用的部署信息
     * @apiName PutAppApplicationConfigurations
     * @apiGroup 应用 API
     * @apiParam (Path Parameters) {String} appId 应用 ID
     */
    @PutMapping(value = "/{appId}/application-configurations")
    public TeslaBaseResult updateApplicationConfigurations(
            @PathVariable String appId, @RequestBody DeployConfigApplyTemplateReq request) {
        request.setAppId(appId);
        return buildSucceedResult(deployConfigProvider.applyTemplate(request));
    }

    /**
     * @api {get} /apps/:appId/application-configurations 获取指定应用的部署信息
     * @apiName GetAppApplicationConfigurations
     * @apiGroup 应用 API
     * @apiParam (Path Parameters) {String} appId 应用 ID
     */
    @GetMapping(value = "/{appId}/application-configurations")
    public TeslaBaseResult getApplicationConfigurations(
            @PathVariable String appId, @ModelAttribute DeployConfigGenerateReq request) {
        if (StringUtils.isEmpty(request.getApiVersion())) {
            request.setApiVersion(DefaultConstant.API_VERSION_V1_ALPHA2);
        }
        request.setAppId(appId);
        request.setAppPackageId(0L);
        DeployConfigGenerateRes result = deployConfigProvider.generate(request);
        return buildSucceedResult(ApplicationConfigurationGenerateRes.builder()
                .yaml(SchemaUtil.toYamlMapStr(result.getSchema()))
                .build());
    }


    /**
     * @api {delete} /apps/:appId/application-configurations 删除指定应用的部署信息
     * @apiName DeleteApplicationConfigurations
     * @apiGroup Application Configuration API
     */
    @DeleteMapping(value = "/{appId}/application-configurations")
    public TeslaBaseResult deleteApplicationConfigurations(
            @PathVariable String appId, @ModelAttribute DeployConfigDeleteReq request) {
        if (StringUtils.isEmpty(request.getApiVersion())) {
            request.setApiVersion(DefaultConstant.API_VERSION_V1_ALPHA2);
        }
        request.setAppId(appId);
        deployConfigProvider.delete(request);
        return buildSucceedResult(DefaultConstant.EMPTY_OBJ);
    }
}
