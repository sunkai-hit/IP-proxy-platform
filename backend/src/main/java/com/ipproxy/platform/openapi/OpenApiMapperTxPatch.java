package com.ipproxy.platform.openapi;

import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * M7 运行日志事务边界补丁。
 * Open API 业务方法可能因认证/状态/额度/资源校验抛出异常并回滚，
 * 日志必须使用独立事务持久化，确保失败请求同样可审计和追踪。
 */
@Repository
@Primary
public class OpenApiMapperTxPatch extends OpenApiMapper {
    public OpenApiMapperTxPatch(JdbcTemplate jdbc){super(jdbc);}

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertApiLog(String requestId,Long customerId,Long accountId,Long serviceId,String method,String path,String queryJson,String clientIp,int status,String result,String errorCode,String errorMessage,long elapsedMs){
        super.insertApiLog(requestId,customerId,accountId,serviceId,method,path,queryJson,clientIp,status,result,errorCode,errorMessage,elapsedMs);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertExtractLog(String requestId,Long customerId,Long accountId,Long serviceId,Long productId,String clientIp,String protocol,String region,String carrier,int requested,int returned,String returnedJson,String resourceJson,String result,String errorCode,String errorMessage,long elapsedMs){
        super.insertExtractLog(requestId,customerId,accountId,serviceId,productId,clientIp,protocol,region,carrier,requested,returned,returnedJson,resourceJson,result,errorCode,errorMessage,elapsedMs);
    }
}
