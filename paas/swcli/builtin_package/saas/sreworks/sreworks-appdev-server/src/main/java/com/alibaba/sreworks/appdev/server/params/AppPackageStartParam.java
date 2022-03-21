package com.alibaba.sreworks.appdev.server.params;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppPackageStartParam {

    private Long teamRepoId;

    private Long teamRegistryId;

    private String version;

}
