package com.alibaba.tesla.productops.repository;

import java.util.List;

import javax.transaction.Transactional;

import com.alibaba.tesla.productops.DO.ProductopsNode;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;

/**
 * @author jinghua.yjh
 */
public interface ProductopsNodeRepository
    extends JpaRepository<ProductopsNode, Long>, JpaSpecificationExecutor<ProductopsNode> {

    List<ProductopsNode> findAllByStageId(String stageId);

    List<ProductopsNode> findAllByNodeTypePathLikeAndStageId(String s, String stageId);

    ProductopsNode findFirstByNodeTypePathAndStageId(String nodeTypePath, String stageId);

    List<ProductopsNode> findAllByParentNodeTypePathAndStageId(String parentNodeTypePath, String stageId);

    @Modifying
    @Transactional(rollbackOn = Exception.class)
    int deleteByNodeTypePathAndStageId(String nodeTypePath, String stageId);

    @Modifying
    @Transactional(rollbackOn = Exception.class)
    void deleteByNodeTypePathLikeAndStageId(String s, String stageId);

}
