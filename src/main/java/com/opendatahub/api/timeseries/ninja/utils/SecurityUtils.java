// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {

	public static final String ROLE_QUOTA_PREFIX = "ODH_ROLE_";
	public static final String ROLE_QUOTA_GUEST = "GUEST";
	public static final String ROLE_QUOTA_REFERRER = "REFERRER";
	public static final String ROLE_QUOTA_BASIC = "BASIC";
	public static final String ROLE_QUOTA_ADVANCED = "ADVANCED";
	public static final String ROLE_QUOTA_PREMIUM = "PREMIUM";
	public static final String ROLE_QUOTA_ADMIN = "ADMIN";

	public static final String ROLE_OPENDATA_PREFIX = "BDP_";
	public static final String ROLE_OPENDATA_GUEST = "GUEST";
	public static final String ROLE_OPENDATA_ADMIN = "ADMIN";

	public enum RoleType {
		QUOTA,
		OPENDATA
	}

	/**
	 * The Keycloak client (resource) whose client-role mappings carry the ODH_ROLE_ and
	 * BDP_ prefixed roles, mirroring the old Keycloak adapter's "use-resource-role-mappings=true"
	 * behaviour. Held as a static field so the static helpers below keep their existing call sites.
	 */
	private static String clientId;

	@Value("${keycloak.resource}")
	public void setClientId(String clientId) {
		SecurityUtils.clientId = clientId;
	}

	public static List<String> getRolesFromAuthentication() {
		return getRolesFromAuthentication(RoleType.OPENDATA);
	}

	public static List<String> getRolesFromAuthentication(RoleType roleType) {
		return getRolesFromAuthentication(
			SecurityContextHolder.getContext().getAuthentication(),
			roleType
		);
	}

	public static List<String> getRolesFromAuthentication(Authentication auth, RoleType roleType) {
		String prefix;
		String admin;
		String guest;

		switch (roleType) {
			case OPENDATA:
				prefix = ROLE_OPENDATA_PREFIX;
				admin = ROLE_OPENDATA_ADMIN;
				guest = ROLE_OPENDATA_GUEST;
				break;
			case QUOTA:
			default:
				prefix = ROLE_QUOTA_PREFIX;
				admin = ROLE_QUOTA_ADMIN;
				guest = ROLE_QUOTA_GUEST;
				break;
		}

		List<String> result = new ArrayList<>();
		for (String role : getClientRoles(auth)) {
			if (role.startsWith(prefix)) {
				String cleanName = role.replaceFirst(prefix, "");
				if (cleanName.equals(admin)) {
					result.clear();
					result.add(admin);
					return result;
				} else {
					result.add(cleanName);
				}
			}
		}

		if (result.isEmpty() || !result.contains(guest)) {
			result.add(guest);
		}
		return result;
	}

	public static String getSubjectFromAuthentication() {
		return getSubjectFromAuthentication(SecurityContextHolder.getContext().getAuthentication());
	}

	public static String getSubjectFromAuthentication(Authentication auth) {
		Jwt jwt = getJwtFromAuthentication(auth);
		return jwt == null ? null : jwt.getSubject();
	}

	public static Jwt getJwtFromAuthentication() {
		return getJwtFromAuthentication(SecurityContextHolder.getContext().getAuthentication());
	}

	public static Jwt getJwtFromAuthentication(Authentication auth) {
		if (auth instanceof JwtAuthenticationToken jwtAuth) {
			return jwtAuth.getToken();
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private static List<String> getClientRoles(Authentication auth) {
		Jwt jwt = getJwtFromAuthentication(auth);
		if (jwt == null || clientId == null) {
			return Collections.emptyList();
		}

		Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
		if (resourceAccess == null || !(resourceAccess.get(clientId) instanceof Map)) {
			return Collections.emptyList();
		}

		Object roles = ((Map<String, Object>) resourceAccess.get(clientId)).get("roles");
		return roles instanceof List ? (List<String>) roles : Collections.emptyList();
	}

}
