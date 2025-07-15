// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.quota;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.opendatahub.api.timeseries.ninja.quota.PricingPlan.Policy;
import com.opendatahub.api.timeseries.ninja.utils.Referer;
import com.opendatahub.api.timeseries.ninja.utils.SecurityUtils;

/**
 * See issue https://github.com/noi-techpark/bdp-core/issues/261
 * 
 * Implement a quota limit for historic data requests
 */
@Component
public class HistoryLimit {
	private final Logger LOG = LoggerFactory.getLogger(HistoryLimit.class);

	@Value("${ninja.quota.history.guest}")
	private long quotaGuest;

	@Value("${ninja.quota.history.referer}")
	private long quotaReferer;

	@Value("${ninja.quota.history.basic}")
	private long quotaBasic;

	@Value("${ninja.quota.history.advanced}")
	private long quotaAdvanced;

	@Value("${ninja.quota.history.premium}")
	private long quotaPremium;

	@Value("${ninja.quota.history.url}")
	private String quotaUrl;

	private Map<PricingPlan.Policy, Long> quotaMap;

	@PostConstruct
	public void initQuotaMap() {
		quotaMap = new EnumMap<>(PricingPlan.Policy.class);

		quotaMap.put(Policy.ANONYMOUS, quotaGuest);
		quotaMap.put(Policy.REFERER, quotaReferer);
		quotaMap.put(Policy.AUTHENTICATED_BASIC, quotaBasic);
		quotaMap.put(Policy.AUTHENTICATED_ADVANCED, quotaAdvanced);
		quotaMap.put(Policy.AUTHENTICATED_PREMIUM, quotaPremium);
		quotaMap.put(Policy.NO_RESTRICTION, Long.valueOf(0));

		LOG.debug("Loaded history limit quota map: {}", quotaMap);
	}

	/**
	 * Checks if the caller is allowed to request data with this time range according to quota limits
	 * 
	 * @param request HttpRequest of the call. Used to retrieve the caller authorization and role
	 * @param from Start of requested period
	 * @param to End of requested period. If null, the end of the period is current time (in the timezone of the start date)
	 * 
	* @return Optional that wraps a QuotaLimitException. <p>
	 * This way the caller must do an explicit throw of the Exception and it's clear that an Exception is being thrown
	 */
	public Optional<QuotaLimitException> check(HttpServletRequest request, ZonedDateTime from, ZonedDateTime to) {
		LOG.debug("Checking history quota for request {}?{}", request.getRequestURI(), request.getQueryString());
		PricingPlan plan = getPricingPlan(request);

		if (plan.getLimit() <= 0) {
			LOG.debug("No history quota limit enabled for this role", from, to, plan.getLimit());
			return Optional.empty();
		}

		LOG.debug("Requested date range is {} to {}, limit is {} days", from, to, plan.getLimit());

		if (to == null) {
			to = ZonedDateTime.now(from.getZone());
		}

		long dateRangeInDays = Duration.between(from.toLocalDate().atStartOfDay(), to.toLocalDate().atStartOfDay()).toDays();

		if (dateRangeInDays > plan.getLimit()) {
			LOG.info("Caller hit history range limit!");
			return Optional.of(new QuotaLimitException(
					String.format("You have exceeded the date range limit of %s days", plan.getLimit()),
					plan.toString(),
					quotaUrl));
		} else {
			return Optional.empty();
		}
	}

	private PricingPlan getPricingPlan(HttpServletRequest request) {
		List<String> roles = SecurityUtils.getRolesFromAuthentication(SecurityUtils.RoleType.QUOTA);
		String referer = Referer.getReferer(request);
		String user = SecurityUtils.getSubjectFromAuthentication();

		LOG.debug("History Limiting Roles: {}", roles);

		PricingPlan plan = PricingPlan.resolvePlan(roles, user, referer, quotaMap);
		return plan;
	}
}
