// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.opendatahub.api.timeseries.ninja.utils.querybuilder.TargetDefList;
import com.opendatahub.api.timeseries.ninja.config.SelectExpansionConfig;
import com.opendatahub.api.timeseries.ninja.utils.conditionals.ConditionalMap;
import com.opendatahub.api.timeseries.ninja.utils.querybuilder.Schema;
import com.opendatahub.api.timeseries.ninja.utils.querybuilder.SelectExpansion;
import com.opendatahub.api.timeseries.ninja.utils.querybuilder.TargetDef;
import com.opendatahub.api.timeseries.ninja.utils.resultbuilder.ResultBuilder;
import com.opendatahub.api.timeseries.ninja.utils.resultbuilder.ResultBuilderConfig;

public class ResultBuilderTests {

	private List<Map<String, Object>> queryResult;
	private SelectExpansion seOpenDataHub;
	private SelectExpansion seNestedMain;
	private ResultBuilderConfig rbConfig;

	@BeforeEach
	public void setUpBefore() throws Exception {
		seOpenDataHub = new SelectExpansionConfig().getSelectExpansion();

		rbConfig = new ResultBuilderConfig()
				.setShowNull(false)
				.addExitPoint("metadatahistory", false)
				.setSchema(seOpenDataHub.getSchema())
				.setMaxAllowedSizeInMB(1000);

		queryResult = new ArrayList<>();

		seNestedMain = new SelectExpansion();
		Schema schemaNestedMain = new Schema();
		TargetDefList defListC = new TargetDefList("C")
				.add(new TargetDef("h", "C.h")
						.setSelectFormat("before, %s"));
		TargetDefList defListD = new TargetDefList("D")
				.add(new TargetDef("d", "D.d")
						.setSelectFormat("%s, after"));
		TargetDefList defListB = new TargetDefList("B")
				.add(new TargetDef("x", "B.x")
						.alias("x_replaced"))
				.add(new TargetDef("y", defListC));
		TargetDefList defListA = new TargetDefList("A")
				.add(new TargetDef("a", "A.a"))
				.add(new TargetDef("b", "A.b"))
				.add(new TargetDef("c", defListB));
		TargetDefList defListMain = new TargetDefList("main")
				.add(new TargetDef("t", defListA));
		schemaNestedMain.add(defListA);
		schemaNestedMain.add(defListB);
		schemaNestedMain.add(defListC);
		schemaNestedMain.add(defListD);
		schemaNestedMain.add(defListMain);
		seNestedMain.setSchema(schemaNestedMain);
	}

	@Test
	public void testOpenDataHubMobility() {
		seOpenDataHub.expand("tname", "datatype");

		queryResult.add(ConditionalMap.mapOf(
			"_stationtype", "parking",
			"_stationcode", "walther",
			"_datatypename", "occ1",
			"tname", "o"
		).get());

		rbConfig.setEntryPoint("stationtype").addExitPoint("datatype", true).setShowNull(true);

		assertEquals("{parking={stations={walther={sdatatypes={occ1={tname=o}}, sparent={}}}}}",
				ResultBuilder.build(rbConfig, queryResult).toString());

		queryResult.add(ConditionalMap.mapOf(
			"_stationtype", "parking",
			"_stationcode", "walther",
			"_datatypename", "occ1",
			"tname", "o"
		).get());

		assertEquals("{parking={stations={walther={sdatatypes={occ1={tname=o}}, sparent={}}}}}",
				ResultBuilder.build(rbConfig, queryResult).toString());

		queryResult.add(ConditionalMap.mapOf(
			"_stationtype", "parking",
			"_stationcode", "walther",
			"_datatypename", "occ2",
			"tname", "x"
		).get());

		assertEquals("{parking={stations={walther={sdatatypes={occ1={tname=o}, occ2={tname=x}}, sparent={}}}}}",
				ResultBuilder.build(rbConfig, queryResult).toString());

	}

	@Test
	public void testMakeObject() {
		seNestedMain.expand("*", "main", "A", "B", "C", "D");
		Map<String, Object> rec = new HashMap<>();
		rec.put("a", "3");
		rec.put("b", "7");
		rec.put("d", "DDD");
		rec.put("x_replaced", "0");
		rec.put("h", "v");

		assertEquals("a", seNestedMain.getUsedTargetNames().get(0));
		assertEquals("b", seNestedMain.getUsedTargetNames().get(1));
		assertEquals("c", seNestedMain.getUsedTargetNames().get(2));
		assertEquals("d", seNestedMain.getUsedTargetNames().get(3));
		assertEquals("h", seNestedMain.getUsedTargetNames().get(4));
		assertEquals("t", seNestedMain.getUsedTargetNames().get(5));
		assertEquals("x_replaced", seNestedMain.getUsedTargetNames().get(6));
		assertEquals("y", seNestedMain.getUsedTargetNames().get(7));

		assertEquals("A", seNestedMain.getUsedDefNames().get(0));
		assertEquals("B", seNestedMain.getUsedDefNames().get(1));
		assertEquals("C", seNestedMain.getUsedDefNames().get(2));
		assertEquals("D", seNestedMain.getUsedDefNames().get(3));
		assertEquals("main", seNestedMain.getUsedDefNames().get(4));

		assertEquals("A.a as a, A.b as b", seNestedMain.getExpansion().get("A"));
		assertEquals("B.x as x_replaced", seNestedMain.getExpansion().get("B"));
		assertEquals("before, C.h as h", seNestedMain.getExpansion().get("C"));
		assertEquals("D.d as d, after", seNestedMain.getExpansion().get("D"));
	}

	@Test
	public void testMakeObjectJSON() {
		seNestedMain.expand("x_replaced.address.cap, x_replaced.address.city", "A", "B");
		Map<String, Object> rec = new HashMap<>();
		rec.put("x_replaced.address.cap", 39100);
		rec.put("x_replaced.address.city", "BZ");

		assertEquals("x_replaced", seNestedMain.getUsedTargetNames().get(0));
		assertEquals("B", seNestedMain.getUsedDefNames().get(0));

		List<String> expB = Arrays.asList(seNestedMain.getExpansion().get("B").split(", "));
		assertTrue(expB.contains("B.x#>'{address,city}' as \"x_replaced.address.city\""));
		assertTrue(expB.contains("B.x#>'{address,cap}' as \"x_replaced.address.cap\""));
	}

	@Test
	public void testNewGenericResultBuilder() {

		Map<String, Object> rec1 = ConditionalMap.mapOf(
				"_stationtype", "AAA",
				"_stationcode", "123",
				"_datatypename", "t1",
				"sname", "edgename1",
				"tname", "t1",
				"mperiod", 200,
				"mvalidtime", 13,
				"mtransactiontime", 88,
				"mvalue", 1111).get();

		Map<String, Object> rec2 = ConditionalMap.mapOf(
				"_stationtype", "AAA",
				"_stationcode", "456",
				"_datatypename", "t2",
				"sname", "edgename2",
				"tname", "t2",
				"mperiod", 100,
				"mvalidtime", 133,
				"mtransactiontime", 8899,
				"mvalue", 2222).get();

		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList.add(rec1);
		resultList.add(rec2);

		assertEquals(
				"{AAA={stations={123={sdatatypes={t1={tmeasurements=[{mperiod=200, mtransactiontime=88, mvalidtime=13, mvalue=1111}], tname=t1}}, sname=edgename1}, 456={sdatatypes={t2={tmeasurements=[{mperiod=100, mtransactiontime=8899, mvalidtime=133, mvalue=2222}], tname=t2}}, sname=edgename2}}}}",
				ResultBuilder.build(rbConfig.setEntryPoint("stationtype"), resultList)
						.toString());
	}

	@Test
	public void testNewGenericResultBuilderOnlyMvalue() {
		Map<String, Object> rec1 = ConditionalMap.mapOf(
				"_stationtype", "EChargingStation",
				"_stationcode", "StationMerano1",
				"_datatypename", "count",
				"mvalue", 42).get();

		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList.add(rec1);

		String result = ResultBuilder.build(rbConfig.setEntryPoint("stationtype"), resultList)
				.toString();

		assertEquals(
				"{EChargingStation={stations={StationMerano1={sdatatypes={count={tmeasurements=[{mvalue=42}]}}}}}}",
				result);
	}

	@Test
	public void testNewGenericResultBuilderOnlyMvalueJsonSub() {
		Map<String, Object> rec1 = ConditionalMap.mapOf(
				"_stationtype", "EChargingStation",
				"_stationcode", "StationMerano1",
				"_datatypename", "count",
				"mvalue", 42,
				"pmetadata.state", "online").get();

		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList.add(rec1);

		String result = ResultBuilder.build(rbConfig.setEntryPoint("stationtype"), resultList).toString();

		assertEquals(
				"{EChargingStation={stations={StationMerano1={sdatatypes={count={tmeasurements=[{mvalue=42}]}}, sparent={pmetadata={state=online}}}}}}",
				result);
	}

	@Test
	@Disabled("This is about issue #23, currently in Backlog... future development")
	public void testNewGenericResultBuilderOnlyMvalueJsonSub2() {
		Map<String, Object> rec1 = ConditionalMap.mapOf(
				"_stationtype", "EChargingStation",
				"_stationcode", "StationMerano1",
				"_datatypename", "count",
				"sname", "ABC",
				"pmetadata.state", "online").get();

		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList.add(rec1);

		String result = ResultBuilder.build(rbConfig.setEntryPoint("stationtype").setExitPoint(null), resultList)
				.toString();

		assertEquals(
				"{EChargingStation={stations={StationMerano1={sname=ABC, sparent={pmetadata={state=online}}}}}}",
				result);
	}

	@Test
	public void testNewGenericResultBuilderEvents() {

		Map<String, Object> rec1 = ConditionalMap.mapOf(
				"_eventorigin", "A22",
				"_eventseriesuuid", "series3",
				"_eventuuid", "ev1",
				"_locationid", "location1",
				"evuuid", "ev1",
				"evseriesuuid", "series3",
				"evldescription", null).get();

		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList.add(rec1);

		String result = ResultBuilder.build(rbConfig.setEntryPoint("eventorigin").setShowNull(true), resultList).toString();

		assertEquals(
				"{A22={eventseries={series3={events={ev1={evlocation={evldescription=null}, evprovenance={}, evseriesuuid=series3, evuuid=ev1}}}}}}",
				result);
	}

}
