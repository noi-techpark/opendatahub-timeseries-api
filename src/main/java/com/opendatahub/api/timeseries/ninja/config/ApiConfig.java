// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.opendatahub.api.timeseries.ninja.quota.RateLimitInterceptor;

@Configuration
@EnableWebMvc
@EnableCaching
public class ApiConfig implements WebMvcConfigurer {

	/*
	 * Configuration of the CrossOriginFilter (CORS)
	 */
	@Value("${ninja.security.cors.allowed-origins:*}")
    private String allowedOrigins;

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry
			.addMapping("/**")
			.allowedOriginPatterns(allowedOrigins)
			.allowedHeaders(CorsConfiguration.ALL)
			.allowedMethods("GET", "HEAD", "OPTIONS")
			.allowCredentials(true);
	}

	/*
	 * Configuration of the RateLimitInterceptor (QUOTA limits)
	 */
	@Autowired
	private RateLimitInterceptor interceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry
			.addInterceptor(interceptor)
            .addPathPatterns("/**");
    }

}
