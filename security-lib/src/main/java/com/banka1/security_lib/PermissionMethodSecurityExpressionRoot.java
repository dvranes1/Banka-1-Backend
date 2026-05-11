package com.banka1.security_lib;

import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

/**
 * Method-security root that resolves permissions from the authenticated JWT claim.
 */
public class PermissionMethodSecurityExpressionRoot extends SecurityExpressionRoot
        implements MethodSecurityExpressionOperations {

    private final SecurityProperties securityProperties;
    private Object filterObject;
    private Object returnObject;
    private Object target;

    public PermissionMethodSecurityExpressionRoot(Authentication authentication, SecurityProperties securityProperties) {
        super(authentication);
        this.securityProperties = securityProperties;
    }

    public boolean hasPermission(String permission) {
        List<String> permissions = extractPermissions();
        return permissions != null && permissions.contains(permission);
    }

    private List<String> extractPermissions() {
        Authentication authentication = getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken.getToken().getClaimAsStringList(securityProperties.getPermissionsClaim());
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            return jwt.getClaimAsStringList(securityProperties.getPermissionsClaim());
        }

        return null;
    }

    @Override
    public void setFilterObject(Object filterObject) {
        this.filterObject = filterObject;
    }

    @Override
    public Object getFilterObject() {
        return filterObject;
    }

    @Override
    public void setReturnObject(Object returnObject) {
        this.returnObject = returnObject;
    }

    @Override
    public Object getReturnObject() {
        return returnObject;
    }

    @Override
    public Object getThis() {
        return target;
    }

    public void setThis(Object target) {
        this.target = target;
    }
}
