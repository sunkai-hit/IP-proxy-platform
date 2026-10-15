package com.ipproxy.platform.openapi;

import com.ipproxy.platform.common.api.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Internal product-team endpoint. No key configured => unavailable by design. */
@RestController
@RequestMapping("/business/v1/authorizations")
public class ProductAuthorizationController {
    private final ProductAuthorizationService decisions;
    private final String sharedKey;

    public ProductAuthorizationController(ProductAuthorizationService decisions,
            @Value("${app.integration.product-bearer-token:}") String sharedKey) {
        this.decisions=decisions; this.sharedKey=sharedKey==null?"":sharedKey;
    }

    @PostMapping("/check")
    public ApiResponse<ProductAuthorizationService.Decision> check(
            @RequestHeader(value="Authorization",required=false) String authorization,
            @RequestBody ProductAuthorizationService.CheckRequest body) {
        verifyProductCaller(authorization);
        return ApiResponse.success(decisions.check(body));
    }

    private void verifyProductCaller(String authorization) {
        if(sharedKey.length()<32||authorization==null||!authorization.startsWith("Bearer "))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Product integration not authorized");
        byte[] actual=authorization.substring(7).getBytes(StandardCharsets.UTF_8);
        byte[] expected=sharedKey.getBytes(StandardCharsets.UTF_8);
        if(!MessageDigest.isEqual(expected,actual))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Product integration not authorized");
    }
}
