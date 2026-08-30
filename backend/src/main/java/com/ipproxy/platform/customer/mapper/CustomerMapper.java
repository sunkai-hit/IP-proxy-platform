package com.ipproxy.platform.customer.mapper;

import org.apache.ibatis.annotations.*;
import java.util.*;

public interface CustomerMapper {
    @Select("""
        SELECT count(*) FROM customer c
        WHERE c.deleted=FALSE
          AND (#{keyword}='' OR c.customer_code ILIKE '%'||#{keyword}||'%' OR c.customer_name ILIKE '%'||#{keyword}||'%' OR COALESCE(c.contact_name,'') ILIKE '%'||#{keyword}||'%')
          AND (#{status}='' OR c.status=#{status})
          AND (#{authStatus}='' OR c.auth_status=#{authStatus})
          AND (#{customerTypeCode}='' OR c.customer_type_code=#{customerTypeCode})
        """)
    long countCustomers(@Param("keyword") String keyword,@Param("status") String status,@Param("authStatus") String authStatus,@Param("customerTypeCode") String customerTypeCode);

    @Select("""
        SELECT c.id,c.customer_code,c.customer_name,c.customer_type_code,c.contact_name,c.contact_phone,c.contact_email,
               c.auth_status,c.status,c.owner_user_id,u.display_name owner_name,c.remark,c.created_at,c.updated_at
        FROM customer c LEFT JOIN sys_user u ON u.id=c.owner_user_id
        WHERE c.deleted=FALSE
          AND (#{keyword}='' OR c.customer_code ILIKE '%'||#{keyword}||'%' OR c.customer_name ILIKE '%'||#{keyword}||'%' OR COALESCE(c.contact_name,'') ILIKE '%'||#{keyword}||'%')
          AND (#{status}='' OR c.status=#{status})
          AND (#{authStatus}='' OR c.auth_status=#{authStatus})
          AND (#{customerTypeCode}='' OR c.customer_type_code=#{customerTypeCode})
        ORDER BY c.created_at DESC,c.id DESC LIMIT #{size} OFFSET #{offset}
        """)
    List<Map<String,Object>> listCustomers(@Param("keyword") String keyword,@Param("status") String status,@Param("authStatus") String authStatus,@Param("customerTypeCode") String customerTypeCode,@Param("size") int size,@Param("offset") int offset);

    @Select("""
        SELECT c.id,c.customer_code,c.customer_name,c.customer_type_code,c.contact_name,c.contact_phone,c.contact_email,
               c.auth_status,c.status,c.owner_user_id,u.display_name owner_name,c.remark,c.created_at,c.updated_at,c.version
        FROM customer c LEFT JOIN sys_user u ON u.id=c.owner_user_id
        WHERE c.id=#{id} AND c.deleted=FALSE
        """)
    Map<String,Object> getCustomer(@Param("id") Long id);

    @Select("SELECT nextval('customer_code_seq')") long nextCustomerCodeSeq();
    @Select("SELECT nextval('customer_auth_no_seq')") long nextCustomerAuthNoSeq();
    @Select("SELECT id FROM customer WHERE customer_code=#{code} AND deleted=FALSE") Long findCustomerIdByCode(@Param("code") String code);
    @Select("SELECT id FROM customer_auth WHERE auth_no=#{authNo} AND deleted=FALSE") Long findAuthIdByNo(@Param("authNo") String authNo);
    @Select("SELECT id FROM customer_account WHERE username=#{username} AND deleted=FALSE") Long findAccountIdByUsername(@Param("username") String username);
    @Select("SELECT count(*) FROM customer WHERE id=#{id} AND deleted=FALSE") int customerExists(@Param("id") Long id);
    @Select("SELECT count(*) FROM customer_account WHERE id=#{id} AND deleted=FALSE") int accountExists(@Param("id") Long id);
    @Select("SELECT count(*) FROM customer_account WHERE username=#{username} AND deleted=FALSE") int accountUsernameExists(@Param("username") String username);
    @Select("SELECT count(*) FROM customer_auth WHERE customer_id=#{customerId} AND status IN ('PENDING','REVIEWING') AND deleted=FALSE") int openAuthCount(@Param("customerId") Long customerId);
    @Select("""
        SELECT count(*) FROM sys_dict_item i JOIN sys_dict_type t ON t.id=i.dict_type_id
        WHERE t.dict_code=#{dictCode} AND i.item_code=#{itemCode} AND t.deleted=FALSE AND i.deleted=FALSE AND t.status='ACTIVE' AND i.status='ACTIVE'
        """) int activeDictItemCount(@Param("dictCode") String dictCode,@Param("itemCode") String itemCode);

    @Insert("""
        INSERT INTO customer(customer_code,customer_name,customer_type_code,contact_name,contact_phone,contact_email,owner_user_id,remark,created_by,updated_by)
        VALUES(#{code},#{name},#{typeCode},#{contactName},#{contactPhone},#{contactEmail},#{ownerUserId},#{remark},#{actorId},#{actorId})
        """)
    int insertCustomer(@Param("code") String code,@Param("name") String name,@Param("typeCode") String typeCode,@Param("contactName") String contactName,@Param("contactPhone") String contactPhone,@Param("contactEmail") String contactEmail,@Param("ownerUserId") Long ownerUserId,@Param("remark") String remark,@Param("actorId") Long actorId);

    @Update("""
        UPDATE customer SET customer_name=#{name},customer_type_code=#{typeCode},contact_name=#{contactName},contact_phone=#{contactPhone},
            contact_email=#{contactEmail},owner_user_id=#{ownerUserId},remark=#{remark},updated_at=now(),updated_by=#{actorId},version=version+1
        WHERE id=#{id} AND deleted=FALSE
        """) int updateCustomer(@Param("id") Long id,@Param("name") String name,@Param("typeCode") String typeCode,@Param("contactName") String contactName,@Param("contactPhone") String contactPhone,@Param("contactEmail") String contactEmail,@Param("ownerUserId") Long ownerUserId,@Param("remark") String remark,@Param("actorId") Long actorId);
    @Update("UPDATE customer SET auth_status=#{status},updated_at=now(),updated_by=#{actorId},version=version+1 WHERE id=#{id} AND deleted=FALSE") int updateCustomerAuthStatus(@Param("id") Long id,@Param("status") String status,@Param("actorId") Long actorId);
    @Update("UPDATE customer SET status=#{status},updated_at=now(),updated_by=#{actorId},version=version+1 WHERE id=#{id} AND deleted=FALSE") int updateCustomerStatus(@Param("id") Long id,@Param("status") String status,@Param("actorId") Long actorId);

    @Select("""
        SELECT a.id,a.auth_no,a.customer_id,c.customer_code,c.customer_name,a.auth_type_code,a.submitted_data,a.attachment_refs,a.status,
               a.reviewer_id,u.display_name reviewer_name,a.reviewed_at,a.review_opinion,a.submitted_at
        FROM customer_auth a JOIN customer c ON c.id=a.customer_id LEFT JOIN sys_user u ON u.id=a.reviewer_id
        WHERE a.deleted=FALSE AND (#{customerId}=0 OR a.customer_id=#{customerId})
          AND (#{keyword}='' OR a.auth_no ILIKE '%'||#{keyword}||'%' OR c.customer_name ILIKE '%'||#{keyword}||'%' OR c.customer_code ILIKE '%'||#{keyword}||'%')
          AND (#{status}='' OR a.status=#{status})
        ORDER BY a.submitted_at DESC,a.id DESC LIMIT #{size} OFFSET #{offset}
        """) List<Map<String,Object>> listAuths(@Param("customerId") Long customerId,@Param("keyword") String keyword,@Param("status") String status,@Param("size") int size,@Param("offset") int offset);
    @Select("""
        SELECT count(*) FROM customer_auth a JOIN customer c ON c.id=a.customer_id
        WHERE a.deleted=FALSE AND (#{customerId}=0 OR a.customer_id=#{customerId})
          AND (#{keyword}='' OR a.auth_no ILIKE '%'||#{keyword}||'%' OR c.customer_name ILIKE '%'||#{keyword}||'%' OR c.customer_code ILIKE '%'||#{keyword}||'%')
          AND (#{status}='' OR a.status=#{status})
        """) long countAuths(@Param("customerId") Long customerId,@Param("keyword") String keyword,@Param("status") String status);
    @Select("""
        SELECT a.id,a.auth_no,a.customer_id,c.customer_code,c.customer_name,c.customer_type_code,a.auth_type_code,a.submitted_data,a.attachment_refs,a.status,
               a.reviewer_id,u.display_name reviewer_name,a.reviewed_at,a.review_opinion,a.submitted_at
        FROM customer_auth a JOIN customer c ON c.id=a.customer_id LEFT JOIN sys_user u ON u.id=a.reviewer_id
        WHERE a.id=#{id} AND a.deleted=FALSE
        """) Map<String,Object> getAuth(@Param("id") Long id);
    @Insert("""
        INSERT INTO customer_auth(auth_no,customer_id,auth_type_code,submitted_data,attachment_refs,status,created_by,updated_by)
        VALUES(#{authNo},#{customerId},#{authTypeCode},CAST(#{submittedDataJson} AS jsonb),CAST(#{attachmentRefsJson} AS jsonb),'PENDING',#{actorId},#{actorId})
        """) int insertAuth(@Param("authNo") String authNo,@Param("customerId") Long customerId,@Param("authTypeCode") String authTypeCode,@Param("submittedDataJson") String submittedDataJson,@Param("attachmentRefsJson") String attachmentRefsJson,@Param("actorId") Long actorId);
    @Update("""
        UPDATE customer_auth SET status=#{status},reviewer_id=#{reviewerId},reviewed_at=now(),review_opinion=#{opinion},updated_at=now(),updated_by=#{reviewerId},version=version+1
        WHERE id=#{id} AND status IN ('PENDING','REVIEWING') AND deleted=FALSE
        """) int reviewAuth(@Param("id") Long id,@Param("status") String status,@Param("reviewerId") Long reviewerId,@Param("opinion") String opinion);

    @Select("""
        SELECT a.id,a.customer_id,c.customer_code,c.customer_name,a.username,a.account_type_code,a.status,a.password_changed_at,a.last_login_at,
               host(a.last_login_ip) last_login_ip,a.created_at
        FROM customer_account a JOIN customer c ON c.id=a.customer_id
        WHERE a.deleted=FALSE AND (#{customerId}=0 OR a.customer_id=#{customerId})
          AND (#{keyword}='' OR a.username ILIKE '%'||#{keyword}||'%' OR c.customer_name ILIKE '%'||#{keyword}||'%' OR c.customer_code ILIKE '%'||#{keyword}||'%')
          AND (#{status}='' OR a.status=#{status})
        ORDER BY a.created_at DESC,a.id DESC LIMIT #{size} OFFSET #{offset}
        """) List<Map<String,Object>> listAccounts(@Param("customerId") Long customerId,@Param("keyword") String keyword,@Param("status") String status,@Param("size") int size,@Param("offset") int offset);
    @Select("""
        SELECT count(*) FROM customer_account a JOIN customer c ON c.id=a.customer_id
        WHERE a.deleted=FALSE AND (#{customerId}=0 OR a.customer_id=#{customerId})
          AND (#{keyword}='' OR a.username ILIKE '%'||#{keyword}||'%' OR c.customer_name ILIKE '%'||#{keyword}||'%' OR c.customer_code ILIKE '%'||#{keyword}||'%')
          AND (#{status}='' OR a.status=#{status})
        """) long countAccounts(@Param("customerId") Long customerId,@Param("keyword") String keyword,@Param("status") String status);
    @Select("""
        SELECT a.id,a.customer_id,c.customer_code,c.customer_name,a.username,a.account_type_code,a.status,a.password_changed_at,a.last_login_at,host(a.last_login_ip) last_login_ip,a.created_at
        FROM customer_account a JOIN customer c ON c.id=a.customer_id WHERE a.id=#{id} AND a.deleted=FALSE
        """) Map<String,Object> getAccount(@Param("id") Long id);
    @Insert("""
        INSERT INTO customer_account(customer_id,username,password_hash,account_type_code,status,password_changed_at,created_by,updated_by)
        VALUES(#{customerId},#{username},#{passwordHash},#{accountTypeCode},'ACTIVE',now(),#{actorId},#{actorId})
        """) int insertAccount(@Param("customerId") Long customerId,@Param("username") String username,@Param("passwordHash") String passwordHash,@Param("accountTypeCode") String accountTypeCode,@Param("actorId") Long actorId);
    @Update("UPDATE customer_account SET password_hash=#{hash},password_changed_at=now(),updated_at=now(),updated_by=#{actorId},version=version+1 WHERE id=#{id} AND deleted=FALSE") int resetAccountPassword(@Param("id") Long id,@Param("hash") String hash,@Param("actorId") Long actorId);
    @Update("UPDATE customer_account SET status=#{status},updated_at=now(),updated_by=#{actorId},version=version+1 WHERE id=#{id} AND deleted=FALSE") int updateAccountStatus(@Param("id") Long id,@Param("status") String status,@Param("actorId") Long actorId);

    @Select("""
        SELECT s.id,s.service_no,s.product_type,p.product_name,pk.package_name,s.status,s.effective_at,s.expire_at,s.last_used_at,
               (SELECT count(*) FROM svc_credential cr WHERE cr.service_id=s.id AND cr.deleted=FALSE) credential_count,
               (SELECT count(*) FROM svc_whitelist w WHERE w.service_id=s.id AND w.deleted=FALSE AND w.status='ACTIVE') whitelist_count
        FROM svc_instance s JOIN prd_product p ON p.id=s.product_id LEFT JOIN prd_package pk ON pk.id=s.package_id
        WHERE s.customer_id=#{customerId} AND s.deleted=FALSE ORDER BY s.created_at DESC
        """) List<Map<String,Object>> listServices(@Param("customerId") Long customerId);
    @Select("""
        SELECT cr.id,cr.service_id,s.service_no,p.product_name,cr.credential_type,cr.account_name,cr.secret_mask,cr.status,cr.issued_at,cr.expire_at,cr.last_used_at,cr.rotated_at
        FROM svc_credential cr JOIN svc_instance s ON s.id=cr.service_id JOIN prd_product p ON p.id=s.product_id
        WHERE s.customer_id=#{customerId} AND s.deleted=FALSE AND cr.deleted=FALSE ORDER BY cr.issued_at DESC
        """) List<Map<String,Object>> listCredentials(@Param("customerId") Long customerId);
    @Select("""
        SELECT
          (SELECT count(*) FROM svc_instance s WHERE s.customer_id=#{customerId} AND s.deleted=FALSE) service_total,
          (SELECT count(*) FROM svc_instance s WHERE s.customer_id=#{customerId} AND s.deleted=FALSE AND s.status='ACTIVE') active_service_count,
          COALESCE((SELECT count(*) FROM log_ip_extract l WHERE l.customer_id=#{customerId}),0) extract_call_count,
          COALESCE((SELECT sum(l.returned_count) FROM log_ip_extract l WHERE l.customer_id=#{customerId}),0) extracted_ip_count,
          COALESCE((SELECT count(*) FROM log_usage l WHERE l.customer_id=#{customerId}),0) usage_request_count,
          COALESCE((SELECT sum(l.upload_bytes+l.download_bytes) FROM log_usage l WHERE l.customer_id=#{customerId}),0) traffic_bytes
        """) Map<String,Object> usageSummary(@Param("customerId") Long customerId);

    @Select("""
        SELECT i.item_code,i.item_name,i.item_value,i.sort_order FROM sys_dict_item i JOIN sys_dict_type t ON t.id=i.dict_type_id
        WHERE t.dict_code=#{dictCode} AND t.deleted=FALSE AND i.deleted=FALSE AND t.status='ACTIVE' AND i.status='ACTIVE' ORDER BY i.sort_order,i.id
        """) List<Map<String,Object>> dictOptions(@Param("dictCode") String dictCode);
    @Select("SELECT id,username,display_name FROM sys_user WHERE deleted=FALSE AND status='ACTIVE' ORDER BY display_name,username") List<Map<String,Object>> activeOwners();

    @Insert("""
        INSERT INTO sys_operation_log(operator_id,operator_name,module_code,object_type,object_id,operation,reason,source_ip,request_id,result)
        VALUES(#{operatorId},#{operatorName},'CUSTOMER',#{objectType},#{objectId},#{operation},#{reason},CAST(NULLIF(#{ip},'') AS inet),#{requestId},'SUCCESS')
        """) int insertAudit(@Param("operatorId") Long operatorId,@Param("operatorName") String operatorName,@Param("objectType") String objectType,@Param("objectId") String objectId,@Param("operation") String operation,@Param("reason") String reason,@Param("ip") String ip,@Param("requestId") String requestId);
}
