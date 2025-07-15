// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.quota;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.jsoniter.output.JsonStream;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import com.opendatahub.api.timeseries.ninja.utils.Referer;
import com.opendatahub.api.timeseries.ninja.utils.SecurityUtils;
import com.opendatahub.api.timeseries.ninja.utils.conditionals.ConditionalMap;

import static com.opendatahub.api.timeseries.ninja.quota.PricingPlan.Policy;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

	private static final Logger LOG = LoggerFactory.getLogger(RateLimitInterceptor.class);

	@Value("${ninja.quota.guest:10}")
    private Long quotaGuest;

	@Value("${ninja.quota.referer:20}")
    private Long quotaReferer;

	@Value("${ninja.quota.basic:50}")
    private Long quotaBasic;

	@Value("${ninja.quota.advanced:100}")
    private Long quotaAdvanced;

	@Value("${ninja.quota.premium:200}")
    private Long quotaPremium;

	@Value("${ninja.quota.url}")
    private String quotaUrl;

	private static final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    public static Bucket resolveBucket(PricingPlan limitation, String user, String referer, String ip, String path) {
		StringJoiner cacheKey = new StringJoiner("+++");
		switch (limitation.getPolicy()) {
			case NO_RESTRICTION:
				cacheKey.add("ADN");
				break;
			case AUTHENTICATED_BASIC:
				cacheKey.add("BSC");
				cacheKey.add(user);
				cacheKey.add(referer);
				cacheKey.add(ip);
				cacheKey.add(path);
				break;
			case AUTHENTICATED_ADVANCED:
				cacheKey.add("ADV");
				cacheKey.add(user);
				cacheKey.add(referer);
				cacheKey.add(ip);
				cacheKey.add(path);
				break;
			case AUTHENTICATED_PREMIUM:
				cacheKey.add("PRM");
				cacheKey.add(user);
				cacheKey.add(referer);
				cacheKey.add(ip);
				cacheKey.add(path);
				break;
			case REFERER:
				cacheKey.add("REF");
				cacheKey.add(referer);
				cacheKey.add(ip);
				cacheKey.add(path);
				break;
			default:
			case ANONYMOUS:
				cacheKey.add("GST");
				cacheKey.add(ip);
				cacheKey.add(path);
				break;
		}
		return cache.computeIfAbsent(
			cacheKey.toString(),
			k -> {
				return Bucket
					.builder()
					.addLimit(getBandwidth(limitation))
					.build();
			}
		);
	}

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

		// FIXME: This should not be necessary, but sometimes we get the old
		// security context authentication token in a consecutive call.
		// See https://stackoverflow.com/questions/72089100/securitycontextholder-getcontext-getauthentication-not-anonymous-but-reques
		if (request.getHeader("Authorization") == null)
			SecurityContextHolder.clearContext();

		List<String> roles = SecurityUtils.getRolesFromAuthentication(SecurityUtils.RoleType.QUOTA);
		String referer = Referer.getReferer(request);
		String ip = request.getLocalAddr();
		String path = request.getRequestURI();
		String user = SecurityUtils.getSubjectFromAuthentication();

		LOG.debug("Rate Limiting Roles: {}", roles);

		Map<PricingPlan.Policy, Long> quotaMap = new EnumMap<>(PricingPlan.Policy.class);
		quotaMap.put(Policy.ANONYMOUS, quotaGuest);
		quotaMap.put(Policy.REFERER, quotaReferer);
		quotaMap.put(Policy.AUTHENTICATED_BASIC, quotaBasic);
		quotaMap.put(Policy.AUTHENTICATED_ADVANCED, quotaAdvanced);
		quotaMap.put(Policy.AUTHENTICATED_PREMIUM, quotaPremium);
		quotaMap.put(Policy.NO_RESTRICTION, Long.valueOf(-1));

		PricingPlan plan = PricingPlan.resolvePlan(roles, user, referer, quotaMap);
		response.addHeader("X-Rate-Limit-Policy", plan.toString());
		request.setAttribute("X-Rate-Limit-Policy", plan.toString());
		if (plan.is(Policy.NO_RESTRICTION)) {
			return true;
		}

        Bucket tokenBucket = resolveBucket(plan, user, referer, ip, path);
        ConsumptionProbe probe = tokenBucket.tryConsumeAndReturnRemaining(1);

		long waitForRefill = probe.getNanosToWaitForRefill() / 1_000_000_000;
		response.addHeader("X-Rate-Limit-Reset", String.valueOf(waitForRefill));
		response.addHeader("X-Rate-Limit-Limit", String.valueOf(getBandwidth(plan).getCapacity()));

		if (probe.isConsumed()) {
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
			return true;
        }

		response.addHeader("X-Rate-Limit-Remaining", "0");
		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		JsonStream.setIndentionStep(2);
		response.getWriter().write(
			JsonStream.serialize(
				ConditionalMap
					.init()
					.put("message", "You have exhausted your API Request Quota")
					.put("policy", plan.toString())
					.put("hint", quotaUrl)
					.get()
			)
		);

		return false;
    }

	private static Bandwidth getBandwidth(PricingPlan plan) {
		long quota = plan.getLimit();
		return Bandwidth.classic(
			quota,
			Refill.intervally(
				quota,
				Duration.ofSeconds(1)
			)
		);
	}
}
