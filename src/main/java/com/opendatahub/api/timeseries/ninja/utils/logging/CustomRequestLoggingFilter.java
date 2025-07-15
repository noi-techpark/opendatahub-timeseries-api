// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.utils.logging;

import org.keycloak.adapters.springsecurity.account.SimpleKeycloakAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.AbstractRequestLoggingFilter;

import com.opendatahub.api.timeseries.ninja.utils.Referer;
import com.opendatahub.api.timeseries.ninja.utils.SecurityUtils;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static net.logstash.logback.argument.StructuredArguments.entries;

public class CustomRequestLoggingFilter extends AbstractRequestLoggingFilter {

	Logger logger = LoggerFactory.getLogger(CustomRequestLoggingFilter.class);

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

		try {
			try {
				SimpleKeycloakAccount account = SecurityUtils.getKeycloakAccountFromAuthentication();
				request.setAttribute("user_subject", account.getPrincipal().getName());
				request.setAttribute("user_email", account.getKeycloakSecurityContext().getToken().getEmail());
			} catch (Exception e) {
				// nothing to do, ignore these log info, if no login account exists
				// or if it is invalid...
			}
			request.setAttribute("timer_start", System.nanoTime());
			filterChain.doFilter(request, response);
		} finally {
			if (!this.isAsyncStarted(request)) {
				logger.info("Request finished", entries(logData(request, response)));
			}
		}
	}

	public String getRemoteIP(HttpServletRequest request) {
		if (request.getHeader("x-forwarded-for") == null) {
			return request.getRemoteAddr();
		}
		return request.getHeader("x-forwarded-for");
	}

	private Map<String, Object> logData(HttpServletRequest request, HttpServletResponse response) {
		final HashMap<String, Object> result = new HashMap<>();
		result.put("uri", request.getRequestURI());
		result.put("query_string", request.getQueryString());
		result.put("user_agent", request.getHeader("User-Agent"));
		result.put("user_subject", request.getAttribute("user_subject"));
		result.put("user_email", request.getAttribute("user_email"));
		result.put("user_quota", request.getAttribute("X-Rate-Limit-Policy"));
		result.put("user_ip", getRemoteIP(request));
		result.put("user_roles_quota", SecurityUtils.getRolesFromAuthentication(SecurityUtils.RoleType.QUOTA));
		result.put("user_roles_opendata", SecurityUtils.getRolesFromAuthentication(SecurityUtils.RoleType.OPENDATA));
		result.put("status", response.getStatus());
		result.put("origin", request.getParameter("origin"));
		result.put("referer", Referer.getReferer(request));
		result.put("data_fetcher", request.getAttribute("data_fetcher"));
		result.put("response_time", (System.nanoTime() - (long) request.getAttribute("timer_start")) / 1000000);
		return result;
	}

	@Override
	protected void beforeRequest(HttpServletRequest request, String s) {
	}

	@Override
	protected void afterRequest(HttpServletRequest request, String s) {
	}
}
