// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.quota;

/**
 * This Exception has a special handler in
 * {@link com.opendatahub.api.timeseries.ninja.config.ErrorResponseConfig}
 * 
 */
public class QuotaLimitException extends RuntimeException {
    public final String message;
    public final String policy;
    public final String hint;

    public QuotaLimitException(String message, String policy, String hint) {
        this.message = message;
        this.policy = policy;
        this.hint = hint;
    }
}
