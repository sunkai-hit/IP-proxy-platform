package com.ipproxy.platform.system.bootstrap;

import org.slf4j.*; import org.springframework.beans.factory.annotation.Value; import org.springframework.boot.CommandLineRunner; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements CommandLineRunner {
    private static final Logger log=LoggerFactory.getLogger(AdminBootstrap.class); private final JdbcTemplate jdbc; private final PasswordEncoder encoder;
    @Value("${app.bootstrap.admin-enabled:true}") private boolean enabled; @Value("${app.bootstrap.admin-username:admin}") private String username; @Value("${app.bootstrap.admin-password:admin123}") private String password; @Value("${app.bootstrap.admin-display-name:超级管理员}") private String displayName;
    public AdminBootstrap(JdbcTemplate jdbc,PasswordEncoder encoder){this.jdbc=jdbc;this.encoder=encoder;}
    public void run(String...args){if(!enabled)return;Long roleId=jdbc.query("SELECT id FROM sys_role WHERE role_code='SUPER_ADMIN' AND deleted=FALSE LIMIT 1",rs->rs.next()?rs.getLong(1):null);if(roleId==null)roleId=jdbc.queryForObject("INSERT INTO sys_role(role_code,role_name,data_scope,description) VALUES('SUPER_ADMIN','超级管理员','ALL','M1 bootstrap role') RETURNING id",Long.class);Long userId=jdbc.query("SELECT id FROM sys_user WHERE username=? AND deleted=FALSE LIMIT 1",ps->ps.setString(1,username),rs->rs.next()?rs.getLong(1):null);if(userId==null){userId=jdbc.queryForObject("INSERT INTO sys_user(username,password_hash,display_name,status,password_changed_at) VALUES(?,?,?,'ACTIVE',now()) RETURNING id",Long.class,username,encoder.encode(password),displayName);log.warn("M1 bootstrap administrator created: username={}. Change default password before non-development use.",username);}Integer exists=jdbc.queryForObject("SELECT count(*) FROM sys_user_role WHERE user_id=? AND role_id=?",Integer.class,userId,roleId);if(exists!=null&&exists==0)jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)",userId,roleId);}
}
