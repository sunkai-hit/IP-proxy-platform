package com.ipproxy.platform.resource.v12;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipproxy.platform.common.api.PageResult;
import com.ipproxy.platform.common.audit.OperationAuditRepository;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class V12ResourceService {
    private final V12ResourceMapper db; private final ObjectMapper json; private final OperationAuditRepository audit;
    public V12ResourceService(V12ResourceMapper db,ObjectMapper json,OperationAuditRepository audit){this.db=db;this.json=json;this.audit=audit;}

    public Map<String,Object> overview(){return db.overview();}
    public Map<String,Object> options(){return Map.of("ros",db.optionsRos(),"lines",db.optionsLines(),"customers",db.optionsCustomers(),"suppliers",db.optionsSuppliers());}

    public PageResult<Map<String,Object>> centos(int page,int size,String keyword,String status){page=page(page);size=size(size);String k=n(keyword),s=n(status);return new PageResult<>(page,size,db.countCentos(k,s),db.listCentos(k,s,size,(page-1)*size));}
    public Map<String,Object> centos(Long id){Map<String,Object>x=db.centos(id);if(x==null)throw notFound("CentOS");x=new LinkedHashMap<>(x);x.put("rosList",db.centosRos(id));return x;}

    public PageResult<Map<String,Object>> ros(int page,int size,String keyword,String status,String autoSwitch){page=page(page);size=size(size);String k=n(keyword),s=n(status),a=n(autoSwitch);return new PageResult<>(page,size,db.countRos(k,s,a),db.listRos(k,s,a,size,(page-1)*size));}
    public Map<String,Object> ros(Long id){Map<String,Object>x=db.ros(id);if(x==null)throw notFound("ROS");x=new LinkedHashMap<>(x);x.put("basStatistics",db.rosBas(id));return x;}

    public PageResult<Map<String,Object>> lines(int page,int size,String keyword,String status,Long rosId,String type,String province,String city,String carrier,Long customerId){page=page(page);size=size(size);String k=n(keyword),s=n(status),t=n(type),p=n(province),c=n(city),o=n(carrier);return new PageResult<>(page,size,db.countLines(k,s,rosId,t,p,c,o,customerId),db.listLines(k,s,rosId,t,p,c,o,customerId,size,(page-1)*size));}
    public Map<String,Object> line(Long id){Map<String,Object>x=db.line(id);if(x==null)throw notFound("家宽/线路");return x;}
    public List<Map<String,Object>> lineIpHistory(Long id,int limit){line(id);return db.lineIpHistory(id,Math.min(500,Math.max(1,limit)));}
    public List<Map<String,Object>> lineOperations(Long id,int limit){line(id);return db.lineOperations(id,Math.min(500,Math.max(1,limit)));}

    public PageResult<Map<String,Object>> pools(int page,int size,String keyword,String status){page=page(page);size=size(size);String k=n(keyword),s=n(status);return new PageResult<>(page,size,db.countPools(k,s),db.listPools(k,s,size,(page-1)*size));}
    public Map<String,Object> pool(Long id){Map<String,Object>x=db.pool(id);if(x==null)throw notFound("资源池");return x;}
    public PageResult<Map<String,Object>> poolLines(Long id,int page,int size){pool(id);page=page(page);size=size(size);return new PageResult<>(page,size,db.countPoolLines(id),db.poolLines(id,size,(page-1)*size));}

    @Transactional public Long createPool(PoolInput in,UserPrincipal actor,String sourceIp){required(in.poolCode(),"资源池编码不能为空");required(in.poolName(),"资源池名称不能为空");String type=n(in.poolType()).isBlank()?"MIXED":in.poolType();Long id=db.insertPool(in.poolCode().trim(),in.poolName().trim(),type,n(in.purpose()),j(in.regionCodes()),j(in.carrierCodes()),actor.userId());if(in.lineIds()!=null&&!in.lineIds().isEmpty())db.replacePoolLines(id,in.lineIds(),"创建资源池");audit.success(actor,"RESOURCE","RESOURCE_POOL",id,"CREATE","创建V1.2线路资源池",sourceIp);return id;}
    @Transactional public void updatePool(Long id,PoolInput in,UserPrincipal actor,String sourceIp){pool(id);required(in.poolName(),"资源池名称不能为空");db.updatePool(id,in.poolName().trim(),n(in.poolType()).isBlank()?"MIXED":in.poolType(),n(in.purpose()),j(in.regionCodes()),j(in.carrierCodes()),actor.userId());if(in.lineIds()!=null)db.replacePoolLines(id,in.lineIds(),"维护资源池成员");audit.success(actor,"RESOURCE","RESOURCE_POOL",id,"UPDATE","更新V1.2线路资源池",sourceIp);}
    @Transactional public int replacePoolLines(Long id,List<Long> lineIds,String reason,UserPrincipal actor,String sourceIp){pool(id);int n=db.replacePoolLines(id,lineIds==null?List.of():lineIds,required(reason,"操作原因不能为空"));audit.success(actor,"RESOURCE","RESOURCE_POOL",id,"REPLACE_LINES",reason,sourceIp);return n;}

    @Transactional public Map<String,Object> setRosAutoSwitch(Long id,boolean enabled,String reason,UserPrincipal actor,String sourceIp){ros(id);if(db.updateRosAutoSwitch(id,enabled,actor.userId())==0)throw notFound("ROS");audit.success(actor,"RESOURCE","ROS",id,"AUTO_SWITCH_"+(enabled?"ON":"OFF"),required(reason,"操作原因不能为空"),sourceIp);return Map.of("id",id,"autoSwitch",enabled,"upstreamControl","PENDING_ADAPTER","message","平台配置已更新；真实ROS控制待接入上游控制Adapter");}
    @Transactional public Map<String,Object> setRosHa(Long id,String role,Long replacementId,String switchStatus,String reason,UserPrincipal actor,String sourceIp){ros(id);String r=n(role()).isBlank()?"STANDALONE":role().trim().toUpperCase(Locale.ROOT);if(replacementId!=null)ros(replacementId);db.updateRosHa(id,r,replacementId,n(switchStatus).isBlank()?"NORMAL":switchStatus,actor.userId());audit.success(actor,"RESOURCE","ROS",id,"UPDATE_HA",required(reason,"操作原因不能为空"),sourceIp);return Map.of("id",id,"haRole",r,"replacementRosId",replacementId==null?0:replacementId,"upstreamControl","PENDING_ADAPTER");}

    private String role(){return "";}
    private int page(int v){return Math.max(1,v);} private int size(int v){return Math.min(200,Math.max(1,v));}
    private String n(String v){return v==null?"":v.trim();}
    private String required(String v,String msg){if(n(v).isBlank())throw new BusinessException("V12_REQUIRED",msg);return v.trim();}
    private String j(Object v){try{return json.writeValueAsString(v==null?List.of():v);}catch(Exception e){throw new BusinessException("V12_JSON_ERROR","数据序列化失败");}}
    private BusinessException notFound(String name){return new BusinessException("V12_RESOURCE_NOT_FOUND",name+"不存在");}

    public record PoolInput(String poolCode,String poolName,String poolType,String purpose,List<String> regionCodes,List<String> carrierCodes,List<Long> lineIds){}
}
