package com.ipproxy.platform.common.audit;

import com.ipproxy.platform.security.UserPrincipal;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OperationAuditRepository {
    private final JdbcTemplate jdbc;
    public OperationAuditRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public void success(UserPrincipal actor,String module,String objectType,Object objectId,String operation,String reason,String sourceIp){
        Long uid=actor==null?null:actor.userId();
        String name=actor==null?"system":actor.username();
        jdbc.update("INSERT INTO sys_operation_log(operator_id,operator_name,module_code,object_type,object_id,operation,reason,source_ip,request_id,result) VALUES(?,?,?,?,?,?,?,CAST(NULLIF(?,'') AS inet),?,'SUCCESS')",
            uid,name,module,objectType,objectId==null?null:String.valueOf(objectId),operation,reason,sourceIp,MDC.get("requestId"));
    }

    public void failure(UserPrincipal actor,String module,String objectType,Object objectId,String operation,String reason,String sourceIp,String error){
        Long uid=actor==null?null:actor.userId();
        String name=actor==null?"system":actor.username();
        jdbc.update("INSERT INTO sys_operation_log(operator_id,operator_name,module_code,object_type,object_id,operation,reason,source_ip,request_id,result,error_message) VALUES(?,?,?,?,?,?,?,CAST(NULLIF(?,'') AS inet),?,'FAILURE',?)",
            uid,name,module,objectType,objectId==null?null:String.valueOf(objectId),operation,reason,sourceIp,MDC.get("requestId"),error);
    }
}
