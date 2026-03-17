package com.campusfit.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class UserWebMvcConfig implements WebMvcConfigurer {

    private final UserAuthInterceptor userAuthInterceptor;

    public UserWebMvcConfig(UserAuthInterceptor userAuthInterceptor) {
        this.userAuthInterceptor = userAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userAuthInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns(
                "/api/health",
                "/api/admin/**",
                "/api/auth/login",
                "/api/auth/register"
            );
    }
}