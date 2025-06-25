// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.TimeZone;

import com.jsoniter.output.JsonStream;

import com.opendatahub.api.timeseries.ninja.utils.jsonserializer.JsonIterPostgresSupport;
import com.opendatahub.api.timeseries.ninja.utils.queryexecutor.ColumnMapRowMapper;
import com.opendatahub.api.timeseries.ninja.utils.queryexecutor.QueryExecutor;


@Component
public class NinjaConfig implements ApplicationListener<ContextRefreshedEvent> {

	@Autowired
    NamedParameterJdbcTemplate jdbcTemplate;

	@Value("${server.compression.enabled:true}")
	private boolean enableCompression4JSON;

    private boolean alreadySetup = false;

	@Override
	public void onApplicationEvent(ContextRefreshedEvent event) {
		if (alreadySetup) {
            return;
        }

		/* Set the query builder, JDBC template's row mapper and JSON parser up */
		QueryExecutor.setup(jdbcTemplate);

		/* Set the global timezone for this Java application */
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

		ColumnMapRowMapper.setTargetDefNameToAliasMap(new SelectExpansionConfig().getSelectExpansion().getSchema().getTargetDefNameToAliasMap());

		if (!enableCompression4JSON) {
			JsonStream.setIndentionStep(4);
		}
		JsonIterPostgresSupport.enable();
	}

}

