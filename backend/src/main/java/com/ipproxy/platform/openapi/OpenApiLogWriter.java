package com.ipproxy.platform.openapi;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Open API 运行日志必须独立于业务事务提交。
 * 即使认证、额度、服务状态或资源校验失败，调用痕迹也不能随业务异常一起回滚。
 */
@Service
public class OpenApiLogWriter {
    private final OpenApiMapper db;

    public OpenApiLogWriter(OpenApiMapper db){this.db=db;}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void api(String requestId,Long customerId,Long accountId,Long serviceId,String method,String path,String queryJson,String clientIp,int status,String result,String errorCode,String errorMessage,long elapsedMs){
        db.insertApiLog(requestId,customerId,accountId,serviceId,method,path,queryJson,clientIp,status,result,errorCode,errorMessage,elapsedMs);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void extract(String requestId,Long customerId,Long accountId,Long serviceId,Long productId,String clientIp,String protocol,String region,String carrier,int requested,int returned,String returnedJson,String resourceJson,String result,String errorCode,String errorMessage,long elapsedMs){
        db.insertExtractLog(requestId,customerId,accountId,serviceId,productId,clientIp,protocol,region,carrier,requested,returned,returnedJson,resourceJson,result,errorCode,errorMessage,elapsedMs);
    }
}
