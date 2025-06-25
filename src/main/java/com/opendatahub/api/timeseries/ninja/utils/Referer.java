// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.opendatahub.api.timeseries.ninja.utils;

import javax.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.util.Strings;

public class Referer {
	public static String getReferer(HttpServletRequest req) {
		String referer = req.getParameter("referer");
		if (Strings.isNotBlank(referer))
			return referer;

		referer = req.getParameter("Referer");
		if (Strings.isNotBlank(referer))
			return referer;

		referer = req.getHeader("Referer");
		return referer;
	}
}