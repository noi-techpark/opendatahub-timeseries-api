// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.utils.resultbuilder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import com.opendatahub.api.timeseries.ninja.utils.querybuilder.Schema;
import com.opendatahub.api.timeseries.ninja.utils.querybuilder.Schema.ExitPoint;

public class ResultBuilderConfig {
    // note: package level visibility
    String entryPoint = null;
    boolean showNull = false;
    Schema schema = null;
    int maxAllowedSizeInMB = 0;

    Map<String, ExitPoint> exitPoints = new HashMap<>();

    public boolean isValid() {
        return !(entryPoint == null || schema == null);
    }

    private ResultBuilderConfig fluid(Runnable fn) {
        fn.run();
        return this;
    }

    public ResultBuilderConfig setEntryPoint(String entryPoint) {
        return fluid(() -> this.entryPoint = entryPoint);
    }

    public ResultBuilderConfig clearExitPoints() {
        return fluid(() -> this.exitPoints.clear());
    }

    public ResultBuilderConfig setExitPoint(String exitPoint) {
        clearExitPoints();
        if (exitPoint != null) {
            addExitPoint(exitPoint, true);
        }
        return this;
    }

    public ResultBuilderConfig addExitPoint(String exitPoint, boolean includePointInResult) {
        return fluid(() -> this.exitPoints.put(exitPoint, new ExitPoint(exitPoint, includePointInResult)));
    }

    public ResultBuilderConfig setShowNull(boolean showNull) {
        return fluid(() -> this.showNull = showNull);
    }

    public ResultBuilderConfig setSchema(Schema schema) {
        return fluid(() -> this.schema = schema);
    }

    public ResultBuilderConfig setMaxAllowedSizeInMB(int maxAllowedSizeInMB) {
        return fluid(() -> this.maxAllowedSizeInMB = maxAllowedSizeInMB);
    }
}
