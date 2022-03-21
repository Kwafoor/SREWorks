package com.alibaba.tesla.appmanager.domain.req.appcomponent;

import com.alibaba.tesla.appmanager.common.BaseRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 应用关联组件查询请求
 *
 * @author yaoxing.gyx@alibaba-inc.com
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AppComponentQueryReq extends BaseRequest {

    /**
     * 应用 ID
     */
    private String appId;
}
