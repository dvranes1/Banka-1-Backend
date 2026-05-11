package com.banka1.security_lib;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;

/**
 * Method-security expression handler that exposes {@code hasPermission('PERMISSION')}
 * against the authenticated JWT permission claim.
 */
public class PermissionMethodSecurityExpressionHandler extends DefaultMethodSecurityExpressionHandler {

    private final SecurityProperties securityProperties;

    public PermissionMethodSecurityExpressionHandler(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    protected MethodSecurityExpressionOperations createSecurityExpressionRoot(
            Authentication authentication,
            MethodInvocation invocation) {
        PermissionMethodSecurityExpressionRoot root =
                new PermissionMethodSecurityExpressionRoot(authentication, securityProperties);
        root.setThis(invocation.getThis());
        root.setPermissionEvaluator(getPermissionEvaluator());
        root.setTrustResolver(getTrustResolver());
        root.setRoleHierarchy(getRoleHierarchy());
        root.setDefaultRolePrefix(getDefaultRolePrefix());
        return root;
    }
}
