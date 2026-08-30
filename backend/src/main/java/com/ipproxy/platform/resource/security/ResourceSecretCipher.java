package com.ipproxy.platform.resource.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class ResourceSecretCipher {
    private final SecretKeySpec key; private final SecureRandom random=new SecureRandom();
    public ResourceSecretCipher(@Value("${app.resource.secret-key:${JWT_SECRET:ipproxy-resource-dev-key-change-me}}") String secret){
        try{byte[] k=MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));this.key=new SecretKeySpec(k,"AES");}
        catch(Exception e){throw new IllegalStateException("资源密钥初始化失败",e);}
    }
    public String encrypt(String plain){if(plain==null||plain.isBlank())return null;try{byte[] iv=new byte[12];random.nextBytes(iv);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));byte[] out=c.doFinal(plain.getBytes(StandardCharsets.UTF_8));byte[] all=new byte[iv.length+out.length];System.arraycopy(iv,0,all,0,iv.length);System.arraycopy(out,0,all,iv.length,out.length);return Base64.getEncoder().encodeToString(all);}catch(Exception e){throw new IllegalStateException("资源密钥加密失败",e);}}
    public String decrypt(String cipher){if(cipher==null||cipher.isBlank())return null;try{byte[] all=Base64.getDecoder().decode(cipher);byte[] iv=new byte[12];byte[] body=new byte[all.length-12];System.arraycopy(all,0,iv,0,12);System.arraycopy(all,12,body,0,body.length);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,iv));return new String(c.doFinal(body),StandardCharsets.UTF_8);}catch(Exception e){throw new IllegalStateException("资源密钥解密失败",e);}}
}
