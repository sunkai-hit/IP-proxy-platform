package com.ipproxy.platform.system.user.mapper;

import com.ipproxy.platform.system.user.model.SystemUser; import org.apache.ibatis.annotations.*; import java.util.List;

public interface SystemUserMapper {
    @Select("SELECT id,username,password_hash,display_name,department,status FROM sys_user WHERE username=#{username} AND deleted=FALSE LIMIT 1") SystemUser findByUsername(@Param("username")String username);
    @Select("SELECT r.role_code FROM sys_role r JOIN sys_user_role ur ON ur.role_id=r.id WHERE ur.user_id=#{userId} AND r.deleted=FALSE AND r.status='ACTIVE' ORDER BY r.role_code") List<String> findRoleCodesByUserId(@Param("userId")Long userId);
    @Select("SELECT DISTINCT p.permission_code FROM sys_permission p JOIN sys_role_permission rp ON rp.permission_id=p.id JOIN sys_user_role ur ON ur.role_id=rp.role_id JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=#{userId} AND p.deleted=FALSE AND p.status='ACTIVE' AND r.deleted=FALSE AND r.status='ACTIVE' ORDER BY p.permission_code") List<String> findPermissionCodesByUserId(@Param("userId")Long userId);
    @Update("UPDATE sys_user SET last_login_at=now(),last_login_ip=CAST(#{ip} AS inet),updated_at=now() WHERE id=#{userId}") int updateLastLogin(@Param("userId")Long userId,@Param("ip")String ip);
    @Insert("INSERT INTO sys_login_log(user_id,username,login_ip,user_agent,result,failure_reason,request_id) VALUES(#{userId},#{username},CAST(NULLIF(#{ip},'') AS inet),#{userAgent},#{result},#{reason},#{requestId})") int insertLoginLog(@Param("userId")Long userId,@Param("username")String username,@Param("ip")String ip,@Param("userAgent")String userAgent,@Param("result")String result,@Param("reason")String reason,@Param("requestId")String requestId);
}
