-- SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
--
-- SPDX-License-Identifier: CC0-1.0

-- Minimal subset of the bdp "intimev2" schema, containing only what the API reads.
create extension if not exists postgis;
create schema intimev2;
set search_path = intimev2, public;

create table metadata (id bigint primary key, json jsonb, station_id bigint, created_on timestamptz);
create table station (
	id bigint primary key, name varchar, stationtype varchar, stationcode varchar, origin varchar,
	active boolean, available boolean, pointprojection geometry(Point, 4326),
	meta_data_id bigint references metadata (id), parent_id bigint references station (id)
);
create table type_metadata (id bigint primary key, json jsonb);
create table type (id bigint primary key, cname varchar, cunit varchar, rtype varchar, description varchar, meta_data_id bigint);
create table provenance (id bigint primary key, data_collector varchar, data_collector_version varchar, lineage varchar);
create table timeseries (id bigint primary key, station_id bigint, type_id bigint, period int, partition_id int default 0);

create table measurement (timeseries_id bigint, timestamp timestamptz, double_value double precision, created_on timestamptz, provenance_id bigint);
create table measurementstring (timeseries_id bigint, timestamp timestamptz, string_value varchar, created_on timestamptz, provenance_id bigint);
create table measurementjson (timeseries_id bigint, timestamp timestamptz, json_value jsonb, created_on timestamptz, provenance_id bigint);
create table measurementhistory (like measurement, partition_id int default 0);
create table measurementstringhistory (like measurementstring, partition_id int default 0);
create table measurementjsonhistory (like measurementjson, partition_id int default 0);

create table edge (id bigint primary key, edge_data_id bigint, origin_id bigint, destination_id bigint, directed boolean, linegeometry geometry(LineString, 4326));

create table location (id bigint primary key, description varchar, geometry geometry);
create table event (
	id bigint primary key, uuid varchar, event_series_uuid varchar, origin varchar, category varchar,
	description varchar, name varchar, event_interval tsrange, created_on timestamptz,
	meta_data_id bigint, location_id bigint, provenance_id bigint
);
