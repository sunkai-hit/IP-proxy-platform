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
    public Map<String,Object> options(){return Map.of("ros",db.optionsRos(),"lines",db.optionsLines(),"customers",db.optionsCustomers(),"suppliers",db.optionsSuppliers(),"lineTypes",List.of(Map.of("code","SHARED","name","共享"),Map.of("code","LONG","name","长效")));}

    public PageResult<Map<String,Object>> centos(int page,int size,String keyword,String status){page=page(page);size=size(size);String k=n(keyword),s=n(status);return new PageResult<>(page,size,db.countCentos(k,s),db.listCentos(k,s,size,(page-1)*size));}
    public Map<String,Object> centos(Long id){Map<String,Object>x=db.centos(id);if(x==null)throw notFound("CentOS");x=new LinkedHashMap<>(x);x.put("rosList",db.centosRos(id));return x;}

    public PageResult<Map<String,Object>> ros(int page,int size,String keyword,String status,String autoSwitch){page=page(page);size=size(size);String k=n(keyword),s=n(status),a=n(autoSwitch);return new PageResult<>(page,size,db.countRos(k,s,a),db.listRos(k,s,a,size,(page-1)*size));}
    public Map<String,Object> ros(Long id){Map<String,Object>x=db.ros(id);if(x==null)throw notFound("ROS");x=new LinkedHashMap<>(x);x.put("basStatistics",db.rosBas(id));return x;}

    public PageResult<Map<String,Object>> lines(int page,int size,String keyword,String status,Long rosId,String type,String province,String city,String carrier,Long customerId){page=page(page);size=size(size);String k=n(keyword),s=n(status),t=lineTypeFilter(type),p=n(province),c=n(city),o=n(carrier);return new PageResult<>(page,size,db.countLines(k,s,rosId,t,p,c,o,customerId),db.listLines(k,s,rosId,t,p,c,o,customerId,size,(page-1)*size));}
    public Map<String,Object> line(Long id){Map<String,Object>x=db.line(id);if(x==null)throw notFound("家宽/线路");return x;}
    public List<Map<String,Object>> lineIpHistory(Long id,int limit){line(id);return db.lineIpHistory(id,Math.min(500,Math.max(1,limit)));}
    public List<Map<String,Object>> lineOperations(Long id,int limit){line(id);return db.lineOperations(id,Math.min(500,Math.max(1,limit)));}
    @Transactional public Map<String,Object> setLineType(Long id,String type,String reason,UserPrincipal actor,String sourceIp){line(id);String t=lineType(type);if(db.updateLineType(id,t,actor.userId())==0)throw notFound("家宽/线路");audit.success(actor,"RESOURCE","LINE",id,"SET_LINE_TYPE",required(reason,"操作原因不能为空"),sourceIp);return Map.of("id",id,"lineType",t);}

    public PageResult<Map<String,Object>> pools(int page,int size,String keyword,String status,String province,String city,String carrier){page=page(page);size=size(size);String k=n(keyword),s=n(status),p=n(province),c=n(city),o=n(carrier);return new PageResult<>(page,size,db.countPools(k,s,p,c,o),db.listPools(k,s,p,c,o,size,(page-1)*size));}
    public Map<String,Object> pool(Long id){Map<String,Object>x=db.pool(id);if(x==null)throw notFound("资源池");x=new LinkedHashMap<>(x);x.put("usage",db.poolUsage(id));return x;}
    public PageResult<Map<String,Object>> poolLines(Long id,int page,int size,String keyword,String status,String type){pool(id);page=page(page);size=size(size);String k=n(keyword),s=n(status),t=lineTypeFilter(type);return new PageResult<>(page,size,db.countPoolLines(id,k,s,t),db.poolLines(id,k,s,t,size,(page-1)*size));}
    public PageResult<Map<String,Object>> candidatePoolLines(Long id,int page,int size,String keyword,String type){pool(id);page=page(page);size=size(size);String k=n(keyword),t=lineTypeFilter(type);return new PageResult<>(page,size,db.countCandidatePoolLines(id,k,t),db.candidatePoolLines(id,k,t,size,(page-1)*size));}
    public List<Map<String,Object>> poolOperations(Long id,int limit){pool(id);return db.poolOperations(id,Math.min(500,Math.max(1,limit)));}

    @Transactional public Long createPool(PoolInput in,UserPrincipal actor,String sourceIp){
        required(in.poolCode(),"资源池编码不能为空");required(in.poolName(),"资源池名称不能为空");
        String province=n(in.provinceCode()),city=n(in.cityCode()),carrier=n(in.carrierCode());
        Long id=db.insertPool(in.poolCode().trim(),in.poolName().trim(),n(in.purpose()),province,city,carrier,j(province.isBlank()?List.of():List.of(province)),j(carrier.isBlank()?List.of():List.of(carrier)),actor.userId());
        if(in.lineIds()!=null&&!in.lineIds().isEmpty()){validatePoolLines(id,in.lineIds());db.addPoolLines(id,distinct(in.lineIds()),"创建资源池");}
        audit.success(actor,"RESOURCE","RESOURCE_POOL",id,"CREATE","创建V1.2来源属性资源池",sourceIp);return id;
    }
    @Transactional public void updatePool(Long id,PoolInput in,UserPrincipal actor,String sourceIp){
        Map<String,Object> old=pool(id);required(in.poolName(),"资源池名称不能为空");String province=n(in.provinceCode()),city=n(in.cityCode()),carrier=n(in.carrierCode());
        boolean sourceChanged=!Objects.equals(obj(old.get("province_code")),province)||!Objects.equals(obj(old.get("city_code")),city)||!Objects.equals(obj(old.get("carrier_code")),carrier);
        if(sourceChanged&&db.countPoolLines(id,"","","")>0)throw new BusinessException("V12_POOL_SOURCE_LOCKED","资源池已有线路成员，请先移出成员后再修改省/市/运营商来源属性");
        db.updatePool(id,in.poolName().trim(),n(in.purpose()),province,city,carrier,j(province.isBlank()?List.of():List.of(province)),j(carrier.isBlank()?List.of():List.of(carrier)),actor.userId());
        if(in.lineIds()!=null){validatePoolLines(id,in.lineIds());db.replacePoolLines(id,distinct(in.lineIds()),"维护资源池成员");}
        audit.success(actor,"RESOURCE","RESOURCE_POOL",id,"UPDATE","更新V1.2来源属性资源池",sourceIp);
    }
    @Transactional public int addPoolLines(Long id,List<Long> lineIds,String reason,UserPrincipal actor,String sourceIp){pool(id);List<Long> ids=distinct(lineIds);if(ids.isEmpty())throw new BusinessException("V12_POOL_LINE_REQUIRED","请选择要加入资源池的线路");validatePoolLines(id,ids);String r=required(reason,"操作原因不能为空");int count=db.addPoolLines(id,ids,r);audit.success(actor,"RESOURCE","RESOURCE_POOL",id,"ADD_LINES",r,sourceIp);return count;}
    @Transactional public int removePoolLines(Long id,List<Long> lineIds,String reason,UserPrincipal actor,String sourceIp){pool(id);List<Long> ids=distinct(lineIds);if(ids.isEmpty())throw new BusinessException("V12_POOL_LINE_REQUIRED","请选择要移出资源池的线路");String r=required(reason,"操作原因不能为空");int count=db.removePoolLines(id,ids,r);audit.success(actor,"RESOURCE","RESOURCE_POOL",id,"REMOVE_LINES",r,sourceIp);return count;}
    @Transactional public int replacePoolLines(Long id,List<Long> lineIds,String reason,UserPrincipal actor,String sourceIp){pool(id);List<Long> ids=distinct(lineIds);validatePoolLines(id,ids);String r=required(reason,"操作原因不能为空");int count=db.replacePoolLines(id,ids,r);audit.success(actor,"RESOURCE","RESOURCE_POOL",id,"REPLACE_LINES",r,sourceIp);return count;}

    @Transactional public Map<String,Object> setRosAutoSwitch(Long id,boolean enabled,String reason,UserPrincipal actor,String sourceIp){ros(id);if(db.updateRosAutoSwitch(id,enabled,actor.userId())==0)throw notFound("ROS");audit.success(actor,"RESOURCE","ROS",id,"AUTO_SWITCH_"+(enabled?"ON":"OFF"),required(reason,"操作原因不能为空"),sourceIp);return Map.of("id",id,"autoSwitch",enabled,"upstreamControl","PENDING_ADAPTER","message","平台配置已更新；真实ROS控制待接入上游控制Adapter");}
    @Transactional public Map<String,Object> setRosHa(Long id,String role,Long replacementId,String switchStatus,String reason,UserPrincipal actor,String sourceIp){
        ros(id);String r=n(role).isBlank()?"STANDALONE":role.trim().toUpperCase(Locale.ROOT);
        if(!Set.of("STANDALONE","PRIMARY","BACKUP").contains(r))throw new BusinessException("V12_ROS_HA_ROLE_INVALID","主备类型仅支持 STANDALONE/PRIMARY/BACKUP");
        if(replacementId!=null){if(Objects.equals(id,replacementId))throw new BusinessException("V12_ROS_HA_SELF_REFERENCE","ROS不能将自己设置为替换对象");ros(replacementId);}
        String sw=n(switchStatus).isBlank()?"NORMAL":switchStatus.trim().toUpperCase(Locale.ROOT);db.updateRosHa(id,r,replacementId,sw,actor.userId());
        audit.success(actor,"RESOURCE","ROS",id,"UPDATE_HA",required(reason,"操作原因不能为空"),sourceIp);Map<String,Object> out=new LinkedHashMap<>();out.put("id",id);out.put("haRole",r);out.put("replacementRosId",replacementId);out.put("switchStatus",sw);out.put("upstreamControl","PENDING_ADAPTER");return out;
    }

    private void validatePoolLines(Long poolId,List<Long> ids){for(Long lineId:distinct(ids)){if(lineId==null||!db.lineEligibleForPool(poolId,lineId))throw new BusinessException("V12_POOL_LINE_INELIGIBLE","线路"+lineId+"不是共享/长效线路，或其省/市/运营商与资源池来源属性不一致");}}
    private List<Long> distinct(List<Long> ids){if(ids==null)return List.of();return ids.stream().filter(Objects::nonNull).distinct().toList();}
    private int page(int v){return Math.max(1,v);} private int size(int v){return Math.min(200,Math.max(1,v));}
    private String n(String v){return v==null?"":v.trim();}
    private String obj(Object v){return v==null?"":String.valueOf(v).trim();}
    private String required(String v,String msg){if(n(v).isBlank())throw new BusinessException("V12_REQUIRED",msg);return v.trim();}
    private String lineType(String v){String t=n(v).toUpperCase(Locale.ROOT);if(!Set.of("SHARED","LONG").contains(t))throw new BusinessException("V12_LINE_TYPE_INVALID","线路类型仅支持 SHARED（共享）或 LONG（长效）");return t;}
    private String lineTypeFilter(String v){return n(v).isBlank()?"":lineType(v);}
    private String j(Object v){try{return json.writeValueAsString(v==null?List.of():v);}catch(Exception e){throw new BusinessException("V12_JSON_ERROR","数据序列化失败");}}
    private BusinessException notFound(String name){return new BusinessException("V12_RESOURCE_NOT_FOUND",name+"不存在");}

    /** poolType/regionCodes/carrierCodes 仅兼容V1.2早期客户端请求，资源池业务逻辑不再使用这些字段。 */
    public record PoolInput(String poolCode,String poolName,String purpose,String provinceCode,String cityCode,String carrierCode,List<Long> lineIds,String poolType,List<String> regionCodes,List<String> carrierCodes){}
}
