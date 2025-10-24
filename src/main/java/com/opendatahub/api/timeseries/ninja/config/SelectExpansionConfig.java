// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.opendatahub.api.timeseries.ninja.config;

import com.opendatahub.api.timeseries.ninja.utils.miniparser.Consumer;
import com.opendatahub.api.timeseries.ninja.utils.miniparser.Token;
import com.opendatahub.api.timeseries.ninja.utils.querybuilder.TargetDefList;
import com.opendatahub.api.timeseries.ninja.utils.resultbuilder.LookUp;
import com.opendatahub.api.timeseries.ninja.utils.resultbuilder.LookUpType;
import com.opendatahub.api.timeseries.ninja.utils.querybuilder.Schema;
import com.opendatahub.api.timeseries.ninja.utils.querybuilder.SelectExpansion;
import com.opendatahub.api.timeseries.ninja.utils.querybuilder.TargetDef;

public class SelectExpansionConfig {

	private SelectExpansion se;

	// TODO make this static and immutable: private static final Schema schema = new Schema();

	public SelectExpansionConfig() {
		super();

		Schema schema = new Schema();

		TargetDefList provenance = TargetDefList
			.init("provenance")
			.setLookUp(new LookUp(LookUpType.INLINE, "measurement", "mprovenance", null))
			.add(new TargetDef("prname", "pr.data_collector"))
			.add(new TargetDef("prversion", "pr.data_collector_version"))
			.add(new TargetDef("prlineage", "pr.lineage"));

		schema.add(provenance);

		TargetDefList measurement = TargetDefList
			.init("measurement")
			.setLookUp(new LookUp(LookUpType.LIST, "datatype", "tmeasurements", null))
			.add(new TargetDef("mvalidtime", "me.timestamp")
					.setColumnFormat("timezone('UTC', %s)"))
			.add(new TargetDef("mtransactiontime", "me.created_on")
					.setColumnFormat("timezone('UTC', %s)"))
			.add(new TargetDef("mperiod", "ts.period"))
			.add(new TargetDef("mprovenance", provenance));

		schema.add(measurement);

		TargetDefList measurementdouble = TargetDefList
			.init("measurementdouble")
			.setLookUp(new LookUp(LookUpType.MERGE, "measurement", "mvalue", null))
			.add(new TargetDef("mvalue_double", "me.double_value")
				.setSelectFormat("%s, null::character varying as mvalue_string, null::jsonb as mvalue_json")
				.alias("mvalue"));

		schema.add(measurementdouble);

		TargetDefList measurementstring = TargetDefList
			.init("measurementstring")
			.setLookUp(new LookUp(LookUpType.MERGE, "measurement", "mvalue", null))
			.add(new TargetDef("mvalue_string", "me.string_value")
				.setSelectFormat("null::double precision as mvalue_double, %s, null::jsonb as mvalue_json")
				.alias("mvalue"));

		schema.add(measurementstring);

		TargetDefList measurementjson = TargetDefList
		.init("measurementjson")
		.setLookUp(new LookUp(LookUpType.MERGE, "measurement", "mvalue", null))
		.add(new TargetDef("mvalue_json", "me.json_value")
			.setSelectFormat("null::double precision as mvalue_double, null::character varying as mvalue_string, %s")
			.alias("mvalue"));

		schema.add(measurementjson);

		TargetDefList datatype = TargetDefList
			.init("datatype")
			.setLookUp(new LookUp(LookUpType.MAP, "station", "sdatatypes", "_datatypename"))
			.add(new TargetDef("tname", "t.cname"))
			.add(new TargetDef("tunit", "t.cunit"))
			.add(new TargetDef("ttype", "t.rtype"))
			.add(new TargetDef("tdescription", "t.description"))
			.add(new TargetDef("tmetadata", "tm.json"))
			.add(new TargetDef("tmeasurements", measurement, measurementdouble, measurementstring, measurementjson));

		schema.add(datatype);

		TargetDefList metadatahistory = TargetDefList
			.init("metadatahistory")
			.setLookUp(new LookUp(LookUpType.LIST, "station", "smetadatahistory", null))
			.add(new TargetDef("mhmetadata", "mh.json"))
			.add(new TargetDef("mhtransactiontime", "mh.created_on")
					.setColumnFormat("timezone('UTC', %s)"));

		schema.add(metadatahistory);

		TargetDefList parent = TargetDefList
			.init("parent")
			.setLookUp(new LookUp(LookUpType.INLINE, "station", "sparent", null))
			.add(new TargetDef("pname", "p.name"))
			.add(new TargetDef("ptype", "p.stationtype"))
			.add(new TargetDef("pcode", "p.stationcode"))
			.add(new TargetDef("porigin", "p.origin"))
			.add(new TargetDef("pactive", "p.active"))
			.add(new TargetDef("pavailable", "p.available"))
			.add(new TargetDef("pcoordinate", "p.pointprojection"))
			.add(new TargetDef("pmetadata", "pm.json"));

		schema.add(parent);

		TargetDefList station = TargetDefList
			.init("station")
			.setLookUp(new LookUp(LookUpType.MAP, "stationtype", "stations", "_stationcode"))
			.add(new TargetDef("sname", "s.name"))
			.add(new TargetDef("stype", "s.stationtype"))
			.add(new TargetDef("scode", "s.stationcode"))
			.add(new TargetDef("sorigin", "s.origin"))
			.add(new TargetDef("sactive", "s.active"))
			.add(new TargetDef("savailable", "s.available"))
			.add(new TargetDef("scoordinate", "s.pointprojection"))
			.add(new TargetDef("smetadata", "m.json"))
			.add(new TargetDef("sparent", parent))
			.add(new TargetDef("sdatatypes", datatype))
			.add(new TargetDef("smetadatahistory", metadatahistory));

		schema.add(station);

		TargetDefList stationBegin = TargetDefList
			.init("stationbegin")
			.setLookUp(new LookUp(LookUpType.INLINE, "edge", "ebegin", null))
			.add(new TargetDef("sbname", "o.name"))
			.add(new TargetDef("sbtype", "o.stationtype"))
			.add(new TargetDef("sbcode", "o.stationcode"))
			.add(new TargetDef("sborigin", "o.origin"))
			.add(new TargetDef("sbactive", "o.active"))
			.add(new TargetDef("sbavailable", "o.available"))
			.add(new TargetDef("sbcoordinate", "o.pointprojection"));

		schema.add(stationBegin);

		TargetDefList stationEnd = TargetDefList
			.init("stationend")
			.setLookUp(new LookUp(LookUpType.INLINE, "edge", "eend", null))
			.add(new TargetDef("sename", "d.name"))
			.add(new TargetDef("setype", "d.stationtype"))
			.add(new TargetDef("secode", "d.stationcode"))
			.add(new TargetDef("seorigin", "d.origin"))
			.add(new TargetDef("seactive", "d.active"))
			.add(new TargetDef("seavailable", "d.available"))
			.add(new TargetDef("secoordinate", "d.pointprojection"));

		schema.add(stationEnd);

		TargetDefList edge = TargetDefList
			.init("edge")
			.setLookUp(new LookUp(LookUpType.MAP, "edgetype", "edges", "_edgecode"))
			.add(new TargetDef("ename", "i.name"))
			.add(new TargetDef("etype", "i.stationtype"))
			.add(new TargetDef("ecode", "i.stationcode"))
			.add(new TargetDef("eorigin", "i.origin"))
			.add(new TargetDef("eactive", "i.active"))
			.add(new TargetDef("eavailable", "i.available"))
			.add(new TargetDef("edirected", "e.directed"))
			// See https://postgis.net/docs/ST_AsGeoJSON.html
			// We use a 9 decimal digits precision and option #3 (= 1:bounding box + 2:short CRS)
			.add(new TargetDef("egeometry", "st_transform(e.linegeometry, 4326)")
				.setColumnFormat("st_asgeojson(%s, 9, 3)::jsonb"))
			.add(new TargetDef("ebegin", stationBegin))
			.add(new TargetDef("eend", stationEnd));

		schema.add(edge);

		TargetDefList stationtype = TargetDefList
			.init("stationtype")
			.setLookUp(new LookUp(LookUpType.MAP, null, null, "_stationtype"))
			.add(new TargetDef("stations", station));

		schema.add(stationtype);

		TargetDefList edgetype = TargetDefList
			.init("edgetype")
			.setLookUp(new LookUp(LookUpType.MAP, null, null, "_edgetype"))
			.add(new TargetDef("edges", edge));

		schema.add(edgetype);

		TargetDefList location = TargetDefList
			.init("location")
			.setLookUp(new LookUp(LookUpType.INLINE, "event", "evlocation", "_locationid"))
			.add(new TargetDef("evldescription", "loc.description"))
			.add(new TargetDef("evlgeometry", "st_transform(loc.geometry, 4326)")
				.setColumnFormat("st_asgeojson(%s, 9, 3)::jsonb"));

		schema.add(location);

		TargetDefList provenanceEvent = TargetDefList
			.init("provenanceevent")
			.setLookUp(new LookUp(LookUpType.INLINE, "event", "evprovenance", null))
			.add(new TargetDef("prname", "pr.data_collector"))
			.add(new TargetDef("prversion", "pr.data_collector_version"))
			.add(new TargetDef("prlineage", "pr.lineage"));

		schema.add(provenanceEvent);

		TargetDefList event = TargetDefList
			.init("event")
			.setLookUp(new LookUp(LookUpType.MAP, "eventseries", "events", "_eventuuid"))
			.add(new TargetDef("evcategory", "ev.category"))
			.add(new TargetDef("evseriesuuid", "ev.event_series_uuid"))
			.add(new TargetDef("evtransactiontime", "ev.created_on"))
			.add(new TargetDef("evdescription", "ev.description"))
			.add(new TargetDef("evstart", "lower(ev.event_interval)"))
			.add(new TargetDef("evend", "upper(ev.event_interval)"))
			.add(new TargetDef("evorigin", "ev.origin"))
			.add(new TargetDef("evuuid", "ev.uuid"))
			.add(new TargetDef("evname", "ev.name"))
			.add(new TargetDef("evmetadata", "evm.json"))
			.add(new TargetDef("evlocation", location))
			.add(new TargetDef("evprovenance", provenanceEvent));

		schema.add(event);

		TargetDefList eventseries = TargetDefList
			.init("eventseries")
			.setLookUp(new LookUp(LookUpType.MAP, "eventorigin", "eventseries", "_eventseriesuuid"))
			//.add(new TargetDef("evseriesuuid", "ev.event_series_uuid"))
			.add(new TargetDef("events", event));

		schema.add(eventseries);


		TargetDefList eventorigin = TargetDefList
			.init("eventorigin")
			.setLookUp(new LookUp(LookUpType.MAP, null, null, "_eventorigin"))
			.add(new TargetDef("eventseries", eventseries));

		schema.add(eventorigin);

		se = new SelectExpansion();
		se.setSchema(schema);

		/*
		 * Define where-clause items and their mappings to SQL. Some operators need
		 * checks of their values or list items. These can be defined with Lambda
		 * functions.
		 */

		/* Primitive operators */
		se.addOperator("NULL", "eq", "%c is %v");
		se.addOperator("NULL", "neq", "%c is not %v");

		se.addOperator("BOOLEAN", "eq", "%c = %v");
		se.addOperator("BOOLEAN", "neq", "%c <> %v");

		se.addOperator("NUMBER", "eq", "%c = %v");
		se.addOperator("NUMBER", "neq", "%c <> %v");
		se.addOperator("NUMBER", "lt", "%c < %v");
		se.addOperator("NUMBER", "gt", "%c > %v");
		se.addOperator("NUMBER", "lteq", "%c =< %v");
		se.addOperator("NUMBER", "gteq", "%c >= %v");

		se.addOperator("STRING", "eq", "%c = %v");
		se.addOperator("STRING", "neq", "%c <> %v");
		se.addOperator("STRING", "re", "%c ~ %v");
		se.addOperator("STRING", "ire", "%c ~* %v");
		se.addOperator("STRING", "nre", "%c !~ %v");
		se.addOperator("STRING", "nire", "%c !~* %v");

		/* JSON operators */
		se.addOperator("JSON/NULL", "eq", "%c#>'{%j}' is %v");
		se.addOperator("JSON/NULL", "neq", "%c#>'{%j}' is not %v");

		se.addOperator("JSON/BOOLEAN", "eq", "%c#>'{%j}' = %v");
		se.addOperator("JSON/BOOLEAN", "neq", "%c#>'{%j}' <> %v");

		se.addOperator("JSON/NUMBER", "eq", "(%c#>'{%j}')::double precision = %v");
		se.addOperator("JSON/NUMBER", "neq", "(%c#>'{%j}')::double precision <> %v");
		se.addOperator("JSON/NUMBER", "lt", "(%c#>'{%j}')::double precision < %v");
		se.addOperator("JSON/NUMBER", "gt", "(%c#>'{%j}')::double precision > %v");
		se.addOperator("JSON/NUMBER", "lteq", "(%c#>'{%j}')::double precision =< %v");
		se.addOperator("JSON/NUMBER", "gteq", "(%c#>'{%j}')::double precision >= %v");

		se.addOperator("JSON/STRING", "eq", "%c#>>'{%j}' = %v");
		se.addOperator("JSON/STRING", "neq", "%c#>>'{%j}' <> %v");
		se.addOperator("JSON/STRING", "re", "%c#>>'{%j}' ~ %v");
		se.addOperator("JSON/STRING", "ire", "%c#>>'{%j}' ~* %v");
		se.addOperator("JSON/STRING", "nre", "%c#>>'{%j}' !~ %v");
		se.addOperator("JSON/STRING", "nire", "%c#>>'{%j}' !~* %v");

		/* LIST operators */
		se.addOperator("LIST/NUMBER", "in", "%c in (%v)");
		se.addOperator("LIST/STRING", "in", "%c in (%v)");
		se.addOperator("LIST/NULL", "in", "%c in (%v)");

		se.addOperator("LIST/NUMBER", "nin", "%c not in (%v)");
		se.addOperator("LIST/STRING", "nin", "%c not in (%v)");
		se.addOperator("LIST/NULL", "nin", "%c not in (%v)");

		Consumer checkMakeEnvelope = new Consumer() {
			@Override
			public boolean middle(Token t) {
				return t.getChildCount() == 4 || t.getChildCount() == 5;
			}
		};
		se.addOperator("LIST/NUMBER", "bbi", "%c && ST_MakeEnvelope(%v)", checkMakeEnvelope);
		se.addOperator("LIST/NUMBER", "bbc", "%c @ ST_MakeEnvelope(%v)", checkMakeEnvelope);

		Consumer checkDistance = new Consumer() {
			@Override
			public boolean middle(Token t) {
				return t.getChildCount() == 3 || t.getChildCount() == 4;
			}
		};
		// Newer postgis (post 3.2.0) versions support ST_Point(x, y, srid), but at time of implementation we were on an older one that does not support passing the SRID
		// Once we're on 3.2.0 +, drop the ST_SetSrid and just pass %v[1:] to ST_Point
		se.addOperator("LIST/NUMBER", "dlt", "ST_Distance(%c::geography, ST_Transform(ST_SetSRID(ST_Point(%v[1:3]), coalesce(%v[3], 4326)),4326)::geography, false) < %v[0]", checkDistance);

		/* JSON/LIST operators */
		se.addOperator("JSON/LIST/STRING", "in", "%c#>>'{%j}' in (%v)");
		se.addOperator("JSON/LIST/NUMBER", "in", "(%c#>'{%j}')::double precision in (%v)");
		se.addOperator("JSON/LIST/NULL", "in", "%c#>'{%j}' in (%v)");
		se.addOperator("JSON/LIST/MIXED", "in", "%c#>'{%j}' in (%v)");

		se.addOperator("JSON/LIST/STRING", "nin", "%c#>>'{%j}' not in (%v)");
		se.addOperator("JSON/LIST/NUMBER", "nin", "(%c#>'{%j}')::double precision not in (%v)");
		se.addOperator("JSON/LIST/NULL", "nin", "%c#>'{%j}' not in (%v)");
		se.addOperator("JSON/LIST/MIXED", "nin", "%c#>'{%j}' not in (%v)");
	}

	public SelectExpansion getSelectExpansion() {
		return se;
	}

}
