package com.company.observability.starter.service;

import com.company.observability.starter.domain.UserIdExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementacija {@link UserIdExtractor} interfejsa koja pokusava da izdvoji
 * user ID iz Spring Security autentikacionog konteksta.
 * <p>
 * User ID se pokusava pronaci u principal objektu, details objektu ili
 * kroz naziv autentikovanog korisnika.
 */
public class JwtAuthenticationUserIdExtractor implements UserIdExtractor {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationUserIdExtractor.class);
    private static final List<String> CLAIM_NAMES = List.of(
            "userId",
            "user_id",
            "uid",
            "sub",
            "preferred_username"
    );

    private static final ConcurrentMap<Class<?>, Optional<Method>> GET_CLAIM_AS_STRING_METHOD_CACHE =
            new ConcurrentHashMap<>();

    private static final ConcurrentMap<Class<?>, Optional<Method>> GET_CLAIMS_METHOD_CACHE =
            new ConcurrentHashMap<>();


    /**
     * Pokusava da izdvoji user ID iz trenutno autentikovanog korisnika.
     *
     * @return {@link Optional} sa user ID vrednoscu ako je pronadjena,
     *         inace prazan {@link Optional}
     */
    @Override
    public Optional<String> extractUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication == null || !authentication.isAuthenticated()){
            return Optional.empty();
        }

        if (authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }

        return extractFromPrincipal(authentication.getPrincipal())
                .or(() -> extractFromObject(authentication.getDetails()))
                .or(() -> StringUtils.hasText(authentication.getName()) ? Optional.of(authentication.getName()) : Optional.empty());
    }

    private Optional<String> extractFromPrincipal(Object principal) {
        if (principal == null) {
            return Optional.empty();
        }
        return extractFromObject(principal)
                .or(() -> {
                    if (principal instanceof Principal p && StringUtils.hasText(p.getName())) {
                        return Optional.of(p.getName());
                    }
                    return Optional.empty();
                });
    }

    private Optional<String> extractFromObject(Object source) {
        if (source == null) {
            return Optional.empty();
        }

        if (source instanceof Map<?, ?> map) {
            return extractFromMap(map);
        }

        Optional<String> fromClaimMethod = extractUsingGetClaimAsString(source);
        if (fromClaimMethod.isPresent()) {
            return fromClaimMethod;
        }

        return extractUsingGetClaims(source);
    }

    private Optional<String> extractUsingGetClaimAsString(Object source) {
        Optional<Method> methodOptional = GET_CLAIM_AS_STRING_METHOD_CACHE.computeIfAbsent(
                source.getClass(),
                clazz -> resolveMethod(clazz, "getClaimAsString", String.class)
        );

        if (methodOptional.isEmpty()) {
            return Optional.empty();
        }

        Method method = methodOptional.get();

        for (String claimName : CLAIM_NAMES) {
            try {
                Object value = method.invoke(source, claimName);
                if (value instanceof String s && StringUtils.hasText(s)) {
                    return Optional.of(s);
                }
            } catch (Exception e) {
                log.trace(
                        "Could not extract claim '{}' via cached getClaimAsString from {}: {}",
                        claimName,
                        source.getClass().getSimpleName(),
                        e.getMessage()
                );
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    private Optional<String> extractUsingGetClaims(Object source) {
        Optional<Method> methodOptional = GET_CLAIMS_METHOD_CACHE.computeIfAbsent(
                source.getClass(),
                clazz -> resolveMethod(clazz, "getClaims")
        );

        if (methodOptional.isEmpty()) {
            return Optional.empty();
        }

        Method method = methodOptional.get();

        try {
            Object claims = method.invoke(source);
            if (claims instanceof Map<?, ?> map) {
                return extractFromMap(map);
            }
        } catch (Exception e) {
            log.trace(
                    "Could not extract claims via cached getClaims from {}: {}",
                    source.getClass().getSimpleName(),
                    e.getMessage()
            );
        }

        return Optional.empty();
    }

    private Optional<String> extractFromMap(Map<?, ?> map) {
        for (String claimName : CLAIM_NAMES) {
            Object value = map.get(claimName);
            if (value instanceof String s && StringUtils.hasText(s)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    private Optional<Method> resolveMethod(Class<?> sourceClass, String methodName, Class<?>... parameterTypes) {
        try {
            return Optional.of(sourceClass.getMethod(methodName, parameterTypes));
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }
}
