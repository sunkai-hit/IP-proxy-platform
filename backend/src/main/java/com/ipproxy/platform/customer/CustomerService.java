package com.ipproxy.platform.customer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipproxy.platform.common.api.PageResult;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.customer.mapper.CustomerMapper;
import com.ipproxy.platform.security.UserPrincipal;
import org.slf4j.MDC;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class CustomerService {
    private final CustomerMapper mapper; private final PasswordEncoder encoder; private final ObjectMapper objectMapper;
    public CustomerService(CustomerMapper mapper,PasswordEncoder encoder,ObjectMapper objectMapper){this.mapper=mapper;this.encoder=encoder;this.objectMapper=objectMapper;}

    public PageResult<Map<String,Object>> customers(int page,int size,String keyword,String status,String authStatus,String type){page=page(page);size=size(size);String k=text(keyword),s=text(status),a=text(authStatus),t=text(type);return new PageResult<>(page,size,mapper.countCustomers(k,s,a,t),mapper.listCustomers(k,s,a,t,size,(page-1)*size));}
    public Map<String,Object> options(){Map<String,Object> r=new LinkedHashMap<>();r.put("customerTypes",mapper.dictOptions("customer_type"));r.put("authTypes",mapper.dictOptions("auth_type"));r.put("accountTypes",mapper.dictOptions("account_type"));r.put("owners",mapper.activeOwners());return r;}
    public Map<String,Object> detail(Long id){Map<String,Object> c=requireCustomer(id);Map<String,Object> r=new LinkedHashMap<>();r.put("customer",c);r.put("authRecords",mapper.listAuths(id,"","",500,0));r.put("accounts",mapper.listAccounts(id,"","",500,0));r.put("services",mapper.listServices(id));r.put("credentials",mapper.listCredentials(id));r.put("usage",mapper.usageSummary(id));return r;}

    @Transactional public Long createCustomer(String name,String typeCode,String contactName,String phone,String email,Long ownerUserId,String remark,UserPrincipal actor,String ip){required(name,"客户名称不能为空");validateDict("customer_type",typeCode,"客户类型");long seq=mapper.nextCustomerCodeSeq();String code="C"+LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)+String.format("%06d",seq);mapper.insertCustomer(code,name.trim(),typeCode,blankToNull(contactName),blankToNull(phone),blankToNull(email),ownerUserId,blankToNull(remark),actor.userId());Long id=mapper.findCustomerIdByCode(code);audit(actor,"CUSTOMER",id,"CREATE",ip,"创建客户 "+code);return id;}
    @Transactional public void updateCustomer(Long id,String name,String typeCode,String contactName,String phone,String email,Long ownerUserId,String remark,UserPrincipal actor,String ip){Map<String,Object> old=requireCustomer(id);required(name,"客户名称不能为空");validateDict("customer_type",typeCode,"客户类型");mapper.updateCustomer(id,name.trim(),typeCode,blankToNull(contactName),blankToNull(phone),blankToNull(email),ownerUserId,blankToNull(remark),actor.userId());if(!Objects.equals(String.valueOf(old.get("customer_type_code")),typeCode))mapper.updateCustomerAuthStatus(id,"UNVERIFIED",actor.userId());audit(actor,"CUSTOMER",id,"UPDATE",ip,"修改客户档案");}
    @Transactional public void changeCustomerStatus(Long id,String target,String reason,UserPrincipal actor,String ip){Map<String,Object> c=requireCustomer(id);String current=String.valueOf(c.get("status"));boolean ok=("FROZEN".equals(target)&&"ACTIVE".equals(current))||("ACTIVE".equals(target)&&"FROZEN".equals(current))||("DISABLED".equals(target)&&("ACTIVE".equals(current)||"FROZEN".equals(current)));if(!ok)throw new BusinessException("CUSTOMER_STATUS_INVALID","不允许从 "+current+" 变更为 "+target);mapper.updateCustomerStatus(id,target,actor.userId());audit(actor,"CUSTOMER",id,"STATUS_"+target,ip,requiredReason(reason));}

    public PageResult<Map<String,Object>> auths(int page,int size,Long customerId,String keyword,String status){page=page(page);size=size(size);long cid=customerId==null?0:customerId;String k=text(keyword),s=text(status);return new PageResult<>(page,size,mapper.countAuths(cid,k,s),mapper.listAuths(cid,k,s,size,(page-1)*size));}
    public Map<String,Object> auth(Long id){Map<String,Object> r=mapper.getAuth(id);if(r==null)throw new BusinessException("CUSTOMER_AUTH_NOT_FOUND","认证申请不存在");return r;}
    @Transactional public Long createAuth(Long customerId,String authTypeCode,Map<String,Object> submittedData,List<String> attachmentRefs,UserPrincipal actor,String ip){Map<String,Object> c=requireCustomer(customerId);validateDict("auth_type",authTypeCode,"认证类型");if(!Objects.equals(String.valueOf(c.get("customer_type_code")),authTypeCode))throw new BusinessException("CUSTOMER_AUTH_TYPE_MISMATCH","认证类型应与客户类型一致");if(mapper.openAuthCount(customerId)>0)throw new BusinessException("CUSTOMER_AUTH_OPEN_EXISTS","该客户已有待审核认证申请");long seq=mapper.nextCustomerAuthNoSeq();String authNo="CA"+LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)+String.format("%06d",seq);mapper.insertAuth(authNo,customerId,authTypeCode,json(submittedData==null?Map.of():submittedData),json(attachmentRefs==null?List.of():attachmentRefs),actor.userId());mapper.updateCustomerAuthStatus(customerId,"PENDING",actor.userId());Long id=mapper.findAuthIdByNo(authNo);audit(actor,"CUSTOMER_AUTH",id,"SUBMIT",ip,"提交认证 "+authNo);return id;}
    @Transactional public void reviewAuth(Long id,boolean approve,String opinion,UserPrincipal actor,String ip){Map<String,Object> a=auth(id);String current=String.valueOf(a.get("status"));if(!List.of("PENDING","REVIEWING").contains(current))throw new BusinessException("CUSTOMER_AUTH_REVIEWED","认证申请已完成审核");String status=approve?"APPROVED":"REJECTED";if(mapper.reviewAuth(id,status,actor.userId(),blankToNull(opinion))==0)throw new BusinessException("CUSTOMER_AUTH_REVIEWED","认证状态已变化，请刷新后重试");Long customerId=((Number)a.get("customer_id")).longValue();mapper.updateCustomerAuthStatus(customerId,status,actor.userId());audit(actor,"CUSTOMER_AUTH",id,approve?"APPROVE":"REJECT",ip,opinion==null?"":opinion);}

    public PageResult<Map<String,Object>> accounts(int page,int size,Long customerId,String keyword,String status){page=page(page);size=size(size);long cid=customerId==null?0:customerId;String k=text(keyword),s=text(status);return new PageResult<>(page,size,mapper.countAccounts(cid,k,s),mapper.listAccounts(cid,k,s,size,(page-1)*size));}
    public Map<String,Object> account(Long id){Map<String,Object> r=mapper.getAccount(id);if(r==null)throw new BusinessException("CUSTOMER_ACCOUNT_NOT_FOUND","客户账号不存在");return r;}
    @Transactional public Long createAccount(Long customerId,String username,String password,String accountTypeCode,UserPrincipal actor,String ip){requireCustomer(customerId);required(username,"用户名不能为空");password(password);String type=accountTypeCode==null||accountTypeCode.isBlank()?"DEFAULT":accountTypeCode;validateDict("account_type",type,"账号类型");if(mapper.accountUsernameExists(username)>0)throw new BusinessException("CUSTOMER_ACCOUNT_EXISTS","客户账号用户名已存在");mapper.insertAccount(customerId,username.trim(),encoder.encode(password),type,actor.userId());Long id=mapper.findAccountIdByUsername(username.trim());audit(actor,"CUSTOMER_ACCOUNT",id,"CREATE",ip,"创建客户账号 "+username);return id;}
    @Transactional public void resetAccountPassword(Long id,String password,String reason,UserPrincipal actor,String ip){requireAccount(id);password(password);mapper.resetAccountPassword(id,encoder.encode(password),actor.userId());audit(actor,"CUSTOMER_ACCOUNT",id,"RESET_PASSWORD",ip,requiredReason(reason));}
    @Transactional public void changeAccountStatus(Long id,String target,String reason,UserPrincipal actor,String ip){Map<String,Object> a=requireAccount(id);String current=String.valueOf(a.get("status"));boolean ok=("FROZEN".equals(target)&&"ACTIVE".equals(current))||("ACTIVE".equals(target)&&"FROZEN".equals(current))||("DISABLED".equals(target)&&("ACTIVE".equals(current)||"FROZEN".equals(current)));if(!ok)throw new BusinessException("CUSTOMER_ACCOUNT_STATUS_INVALID","不允许从 "+current+" 变更为 "+target);mapper.updateAccountStatus(id,target,actor.userId());audit(actor,"CUSTOMER_ACCOUNT",id,"STATUS_"+target,ip,requiredReason(reason));}

    public List<Map<String,Object>> services(Long customerId){requireCustomer(customerId);return mapper.listServices(customerId);}
    public List<Map<String,Object>> credentials(Long customerId){requireCustomer(customerId);return mapper.listCredentials(customerId);}
    public Map<String,Object> usage(Long customerId){requireCustomer(customerId);return mapper.usageSummary(customerId);}

    private Map<String,Object> requireCustomer(Long id){Map<String,Object> c=mapper.getCustomer(id);if(c==null)throw new BusinessException("CUSTOMER_NOT_FOUND","客户不存在");return c;}
    private Map<String,Object> requireAccount(Long id){Map<String,Object> a=mapper.getAccount(id);if(a==null)throw new BusinessException("CUSTOMER_ACCOUNT_NOT_FOUND","客户账号不存在");return a;}
    private void validateDict(String dict,String code,String label){if(code==null||code.isBlank()||mapper.activeDictItemCount(dict,code)==0)throw new BusinessException("CUSTOMER_DICT_INVALID",label+"无效");}
    private void audit(UserPrincipal actor,String objectType,Object id,String operation,String ip,String reason){mapper.insertAudit(actor.userId(),actor.displayName(),objectType,String.valueOf(id),operation,reason==null?"":reason,ip==null?"":ip,MDC.get("requestId"));}
    private String json(Object value){try{return objectMapper.writeValueAsString(value);}catch(JsonProcessingException e){throw new BusinessException("CUSTOMER_JSON_INVALID","认证资料格式错误");}}
    private int page(int p){return Math.max(p,1);} private int size(int s){return Math.min(Math.max(s,1),500);} private String text(String s){return s==null?"":s.trim();}
    private String blankToNull(String s){return s==null||s.isBlank()?null:s.trim();}
    private void required(String s,String message){if(s==null||s.isBlank())throw new BusinessException("CUSTOMER_REQUIRED",message);}
    private String requiredReason(String s){required(s,"操作原因不能为空");return s.trim();}
    private void password(String s){if(s==null||s.length()<6)throw new BusinessException("CUSTOMER_PASSWORD_WEAK","密码至少6位");}
}
