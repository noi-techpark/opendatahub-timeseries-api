// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.api.timeseries.ninja.config;

import java.util.Date;
import java.util.Map;

import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.opendatahub.api.timeseries.ninja.quota.QuotaLimitException;
import com.opendatahub.api.timeseries.ninja.utils.conditionals.ConditionalMap;
import com.opendatahub.api.timeseries.ninja.utils.simpleexception.SimpleException;

/**
 * API exception handler mapping every exception to a serializable object
 *
 * @author Peter Moser
 */
@ControllerAdvice
public class ErrorResponseConfig extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ErrorResponseConfig.class);

	@ExceptionHandler
	public ResponseEntity<Object> handleException(Exception ex) {
		log.error(ex.getMessage(), ex);
		return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex);
	}

	@ExceptionHandler
	public ResponseEntity<Object> handleException(SimpleException ex) {
		return buildResponse(HttpStatus.BAD_REQUEST, ex);
	}

	@ExceptionHandler
	public ResponseEntity<Object> handleException(ResponseStatusException ex) {
		return buildResponse(ex.getStatus(), ex);
	}

	@ExceptionHandler
	public ResponseEntity<Object> handleException(DataAccessException ex) {
		Throwable cause = ex.getCause();
		if (cause instanceof PSQLException) {
			return buildResponse(HttpStatus.BAD_REQUEST, (PSQLException) cause);
		}
		return buildResponse(HttpStatus.BAD_REQUEST, ex);
	}

	@ExceptionHandler
	public ResponseEntity<Object> handleQuotaLimitException(QuotaLimitException ex) {
		HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;

		HttpHeaders headers = new HttpHeaders();

		Map<String, Object> body = Map.of(
			"message", ex.message,
			"policy", ex.policy,
			"hint", ex.hint
		);

		ResponseEntity<Object> response = new ResponseEntity<>(body, headers, status );
		return response;
	}

	private ResponseEntity<Object> buildResponse(final HttpStatus httpStatus, final Exception exception) {
		String message = (exception == null || exception.getMessage() == null) ? exception.getClass().getSimpleName() : exception.getMessage();
		message = message.replace("\\n", " ").replace("\"", "'");
		ConditionalMap map = ConditionalMap
			.init()
			.put("message", message)
			.put("timestamp", new Date())
			.put("code", httpStatus.value())
			.put("error", httpStatus.getReasonPhrase());
		if (exception instanceof SimpleException) {
			SimpleException se = (SimpleException) exception;
			map.putIfNotNull("description", se.getDescription());
			map.putIfNotEmpty("info", se.getData());
		} else if (exception instanceof PSQLException) {
			PSQLException cause = (PSQLException) exception;
			switch(cause.getSQLState()) {
				case "57014":
					map.put("description", "Query timed out");
					map.put("hint", "Query for smaller response chunks. Use SELECT, WHERE, LIMIT with OFFSET, or a narrow time interval.");
					break;
				default:
					map.put("message", message);
					map.put("description", "Error from the database backend");
			}
		}

		if (log.isDebugEnabled()) {
			log.error(message, exception);
		}
		return new ResponseEntity<>(map.get(), httpStatus);
	}
}
