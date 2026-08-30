package com.ipproxy.platform.openapi;

import com.ipproxy.platform.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/open/v1")
public class OpenApiController {
    private final OpenApiService service;
    public OpenApiController(OpenApiService service){this.service=service;}

    @PostMapping("/auth/token")
    public ApiResponse<?> token(@RequestBody TokenRequest body,HttpServletRequest request){return ApiResponse.success(service.issueToken(body.username(),body.password(),Boolean.TRUE.equals(body.changeToken()),ip(request)));}

    @GetMapping("/proxy/extract")
    public ApiResponse<?> extract(@RequestParam int amount,@RequestParam(defaultValue="HTTP") String protocol,@RequestParam(defaultValue="") String regionCode,@RequestParam(defaultValue="") String carrierCode,@RequestParam(required=false) String token,HttpServletRequest request){return ApiResponse.success(service.extract(token(request,token),amount,protocol,regionCode,carrierCode,ip(request)));}

    @GetMapping("/whitelist")
    public ApiResponse<?> whitelist(@RequestParam(required=false) String token,HttpServletRequest request){return ApiResponse.success(service.whitelist(token(request,token),ip(request)));}

    @PostMapping("/whitelist")
    public ApiResponse<?> addWhitelist(@RequestBody WhitelistRequest body,@RequestParam(required=false) String token,HttpServletRequest request){return ApiResponse.success(service.addWhitelist(token(request,token),body.ip(),ip(request)));}

    @DeleteMapping("/whitelist/{ip}")
    public ApiResponse<?> deleteWhitelist(@PathVariable String ip,@RequestParam(required=false) String token,HttpServletRequest request){service.deleteWhitelist(token(request,token),ip,ip(request));return ApiResponse.success();}

    private String token(HttpServletRequest r,String queryToken){String auth=r.getHeader("Authorization");if(auth!=null&&auth.regionMatches(true,0,"Bearer ",0,7))return auth.substring(7).trim();return queryToken;}
    private String ip(HttpServletRequest r){String forwarded=r.getHeader("X-Forwarded-For");if(forwarded!=null&&!forwarded.isBlank())return forwarded.split(",")[0].trim();return r.getRemoteAddr()==null?"":r.getRemoteAddr();}

    public record TokenRequest(String username,String password,Boolean changeToken){}
    public record WhitelistRequest(String ip){}
}
