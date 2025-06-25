// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.api.timeseries.ninja.utils.simpleexception;

import java.util.HashMap;
import java.util.Map;

/**
 * JPAException, which is a well-described runtime exception, ready for API consumers.
 * The main goal for such an exception is to provide enough information to an API consumer
 * to handle API errors as easy as possible. Do not expose internal errors.
 *
 * @author Peter Moser
 *
 */
public class SimpleException extends RuntimeException {

	private static final long serialVersionUID = -8271639898842999188L;

	private String description;
	private final ErrorCodeInterface id;
	private Map<String, Object> data;

	public SimpleException(final ErrorCodeInterface id, final Object... params) {
		super(String.format(id.getMsg(), params));
		this.id = id;
	}

	public SimpleException(final ErrorCodeInterface id) {
		this(id, (Object[]) null);
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(final String description) {
		this.description = description;
	}

	public ErrorCodeInterface getId() {
		return id;
	}

	public Map<String, Object> getData() {
		return data;
	}

	public void addData(final String key, final Object data) {
		if (this.data == null) {
			this.data = new HashMap<>();
		}
		this.data.put(key, data);
	}

	public void setData(final Map<String, Object> dataMap) {
		this.data = dataMap;
	}

}
