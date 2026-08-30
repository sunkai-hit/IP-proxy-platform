package com.ipproxy.platform.system.user.model;
public class SystemUser {
    private Long id; private String username; private String passwordHash; private String displayName; private String department; private String status;
    public Long getId(){return id;} public void setId(Long id){this.id=id;} public String getUsername(){return username;} public void setUsername(String username){this.username=username;} public String getPasswordHash(){return passwordHash;} public void setPasswordHash(String passwordHash){this.passwordHash=passwordHash;} public String getDisplayName(){return displayName;} public void setDisplayName(String displayName){this.displayName=displayName;} public String getDepartment(){return department;} public void setDepartment(String department){this.department=department;} public String getStatus(){return status;} public void setStatus(String status){this.status=status;}
}
