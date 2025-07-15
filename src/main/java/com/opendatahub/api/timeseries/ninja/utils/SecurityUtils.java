// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.utils;

import java.util.ArrayList;
import java.util.List;

import org.keycloak.adapters.springsecurity.KeycloakAuthenticationException;
import org.keycloak.adapters.springsecurity.account.SimpleKeycloakAccount;
import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

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

	private SecurityUtils() {
		// This is just an utility class
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
		String prefix = null;
		String admin = null;
		String guest = null;

		switch (roleType) {
			case OPENDATA:
				prefix = ROLE_OPENDATA_PREFIX;
				admin = ROLE_OPENDATA_ADMIN;
				guest = ROLE_OPENDATA_GUEST;
				break;
			case QUOTA:
				prefix = ROLE_QUOTA_PREFIX;
				admin = ROLE_QUOTA_ADMIN;
				guest = ROLE_QUOTA_GUEST;
				break;
		}

		List<String> result = new ArrayList<>();
		if (auth instanceof KeycloakAuthenticationToken) {
			SimpleKeycloakAccount user = (SimpleKeycloakAccount) auth.getDetails();
			for (String role : user.getRoles()) {
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

		try {
			return getKeycloakAccountFromAuthentication(auth).getPrincipal().getName();
		} catch (KeycloakAuthenticationException e) {
			return null;
		}
	}

	public static SimpleKeycloakAccount getKeycloakAccountFromAuthentication(Authentication auth) {
		if (auth instanceof KeycloakAuthenticationToken) {
			return (SimpleKeycloakAccount) auth.getDetails();
		}
		throw new KeycloakAuthenticationException("Given parameter is not a KeycloakAuthenticationToken");
	}

	public static SimpleKeycloakAccount getKeycloakAccountFromAuthentication() {
		return getKeycloakAccountFromAuthentication(SecurityContextHolder.getContext().getAuthentication());
	}

}
