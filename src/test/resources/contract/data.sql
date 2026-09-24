-- SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
--
-- SPDX-License-Identifier: CC0-1.0

set search_path = intimev2, public;

insert into metadata (id, json, station_id, created_on) values
	(1, '{"city":"Bolzano","address":{"street":"Via Roma","nr":12},"capacity":120}', null, now()),
	(2, '{"city":"Merano","capacity":60,"tags":["a","b"]}', null, now()),
	(3, '{"municipality":{"cap":"39100"},"sector":"Software"}', null, now()),
	(4, '{"voltage":230,"outlets":2}', null, now()),
	(5, '{"nullvalue":null,"unicode":"äöü \"quoted\" \\ back\nline"}', null, now()),
	(6, '{"city":"Trento"}', null, now());

-- stations -------------------------------------------------------------------------------
insert into station (id, name, stationtype, stationcode, origin, active, available, pointprojection, meta_data_id, parent_id) values
	(1, 'Parking Bolzano Centro', 'ParkingStation', 'P1', 'FAMAS', true, true, st_setsrid(st_makepoint(11.35, 46.49), 4326), 1, null),
	(2, 'Parking Merano', 'ParkingStation', 'P2', 'FAMAS', true, true, st_setsrid(st_makepoint(11.16, 46.67), 4326), 2, null),
	(3, 'Parking Hidden', 'ParkingStation', 'P3', 'PRIVATE', true, true, st_setsrid(st_makepoint(11.0, 46.0), 4326), null, null),
	(4, 'Parking Unavailable', 'ParkingStation', 'P4', 'FAMAS', true, false, null, null, null),
	(5, 'Charger Bolzano', 'EChargingStation', 'C1', 'ROUTE220', true, true, st_setsrid(st_makepoint(11.34, 46.50), 4326), 4, null),
	(6, 'Charger Plug 1', 'EChargingPlug', 'C1-1', 'ROUTE220', true, true, st_setsrid(st_makepoint(11.34, 46.50), 4326), 4, 5),
	(7, 'Charger Plug 2', 'EChargingPlug', 'C1-2', 'ROUTE220', false, true, null, null, 5),
	(8, 'Creative Co', 'CreativeIndustry', 'CI1', 'NOI', true, true, st_setsrid(st_makepoint(11.33, 46.47), 4326), 3, null),
	(9, 'Creative No Sector', 'CreativeIndustry', 'CI2', 'NOI', true, true, null, null, null),
	(10, 'Traffic A22 1', 'TrafficSensor', 'A22:1:3', 'A22', true, true, st_setsrid(st_makepoint(11.4, 46.3), 4326), null, null),
	(11, 'Traffic A22 2', 'TrafficSensor', 'A22:2:3', 'A22', true, true, st_setsrid(st_makepoint(11.5, 46.2), 4326), null, null),
	(12, 'Traffic Merano', 'TrafficSensor', 'M1', 'Municipality of Merano', true, true, null, null, null),
	(13, 'Meteo Trento', 'MeteoStation', 'T1', 'meteotrentino', true, true, st_setsrid(st_makepoint(11.12, 46.07), 4326), 6, null),
	(14, 'Link start', 'LinkStation', 'L1', 'NOI', true, true, st_setsrid(st_makepoint(11.1, 46.1), 4326), null, null),
	(15, 'Link end', 'LinkStation', 'L2', 'NOI', true, true, st_setsrid(st_makepoint(11.2, 46.2), 4326), null, null),
	(16, 'Link a->b', 'LinkStation', 'L1->L2', 'NOI', true, true, null, null, null),
	(17, 'Link hidden edge', 'LinkStation', 'L3', 'NOI', true, false, null, null, null),
	(18, 'Unicode Stätiön "q"', 'ParkingStation', 'P5:ü', 'FAMAS', true, true, null, 5, null);

update metadata set station_id = 1 where id = 1;
insert into metadata (id, json, station_id, created_on) values
	(101, '{"v":1}', 1, '2023-01-01T10:00:00Z'),
	(102, '{"v":2}', 1, '2023-06-01T10:00:00Z'),
	(103, '{"v":3,"x":{"y":true}}', 2, '2023-03-15T00:00:00Z'),
	(104, '{"v":9}', 3, '2023-03-15T00:00:00Z');

-- data types -----------------------------------------------------------------------------
insert into type_metadata (id, json) values (1, '{"scale":"linear","range":[0,100]}');
insert into type (id, cname, cunit, rtype, description, meta_data_id) values
	(1, 'occupied', 'count', 'instantaneous', 'Occupied places', 1),
	(2, 'free', 'count', 'instantaneous', 'Free places', null),
	(3, 'state', null, 'instantaneous', 'Textual state', null),
	(4, 'raw', null, 'instantaneous', 'Json payload', null),
	(5, 'vehicle-count', 'n', 'total', 'Vehicles', null),
	(6, 'air-temperature', 'C', 'mean', 'Air temp', null),
	(7, 'EAQI-NO2', 'idx', 'instantaneous', 'Open although closed station', null);

insert into provenance (id, data_collector, data_collector_version, lineage) values
	(1, 'collector-a', '1.0.0', 'NOI'), (2, 'collector-b', '2.1.0', 'FAMAS');

insert into timeseries (id, station_id, type_id, period, partition_id) values
	(1, 1, 1, 300, 0), (2, 1, 2, 300, 0), (3, 1, 3, 300, 0), (4, 1, 4, 300, 0),
	(5, 2, 1, 300, 0), (6, 2, 2, 300, 0),
	(7, 3, 1, 300, 0),
	(8, 5, 3, 300, 0), (9, 6, 3, 300, 0), (10, 7, 3, 300, 0), (11, 5, 4, 300, 0),
	(12, 10, 5, 600, 0), (13, 11, 5, 600, 0), (14, 12, 5, 600, 0),
	(15, 13, 6, 600, 0),
	(16, 1, 1, 3600, 0),
	(17, 4, 1, 300, 0),
	(18, 18, 1, 300, 0), (19, 18, 3, 300, 0);

-- latest measurements --------------------------------------------------------------------
insert into measurement (timeseries_id, timestamp, double_value, created_on, provenance_id) values
	(1, '2024-05-01T12:00:00Z', 42, '2024-05-01T12:00:01Z', 1),
	(2, '2024-05-01T12:00:00Z', 78.5, '2024-05-01T12:00:01Z', 1),
	(5, '2024-05-01T12:05:00Z', 7, '2024-05-01T12:05:02Z', 2),
	(6, '2024-05-01T12:05:00Z', 3e-7, '2024-05-01T12:05:02Z', null),
	(7, '2024-05-01T12:00:00Z', 1, '2024-05-01T12:00:01Z', null),
	(12, '2024-05-01T12:00:00Z', 1234567890.125, '2024-05-01T12:00:01Z', null),
	(13, '2024-05-01T12:00:00Z', -5, '2024-05-01T12:00:01Z', null),
	(14, '2024-05-01T12:00:00Z', 0, '2024-05-01T12:00:01Z', null),
	(15, '2024-05-01T12:00:00Z', 21.3, '2024-05-01T12:00:01Z', 1),
	(16, '2024-05-01T12:00:00Z', 55, '2024-05-01T12:00:01Z', null),
	(17, '2024-05-01T12:00:00Z', 5, '2024-05-01T12:00:01Z', null),
	(18, '2024-05-01T12:00:00Z', 9, '2024-05-01T12:00:01Z', null);
insert into measurementstring (timeseries_id, timestamp, string_value, created_on, provenance_id) values
	(3, '2024-05-01T12:00:00Z', 'open', '2024-05-01T12:00:01Z', 1),
	(8, '2024-05-01T12:00:00Z', 'available', '2024-05-01T12:00:01Z', null),
	(9, '2024-05-01T12:00:00Z', 'occupied', '2024-05-01T12:00:01Z', null),
	(10, '2024-05-01T12:00:00Z', 'broken "x"', '2024-05-01T12:00:01Z', null),
	(19, '2024-05-01T12:00:00Z', 'ünï\ncode\t"q"\\', '2024-05-01T12:00:01Z', null);
insert into measurementjson (timeseries_id, timestamp, json_value, created_on, provenance_id) values
	(4, '2024-05-01T12:00:00Z', '{"a":1,"b":{"c":[1,2,3]},"s":"x"}', '2024-05-01T12:00:01Z', null),
	(11, '2024-05-01T12:00:00Z', '{"a":2,"nested":{"deep":null}}', '2024-05-01T12:00:01Z', null);

-- history: 3 hours in 5 min steps for a few series + one bulk series for size tests ------
insert into measurementhistory (timeseries_id, timestamp, double_value, created_on, provenance_id, partition_id)
select ts, t, round((extract(epoch from t)::bigint % 97)::numeric * ts / 10, 3), t + interval '1 second', case when ts = 1 then 1 end, 0
from (values (1), (2), (5), (6), (7), (12)) as x(ts),
	generate_series('2024-04-01T00:00:00Z'::timestamptz, '2024-04-01T02:55:00Z', interval '5 minutes') as t;
insert into measurementstringhistory (timeseries_id, timestamp, string_value, created_on, provenance_id, partition_id)
select 3, t, case when extract(minute from t)::int % 20 = 0 then 'closed' else 'open' end, t + interval '1 second', null, 0
from generate_series('2024-04-01T00:00:00Z'::timestamptz, '2024-04-01T02:55:00Z', interval '5 minutes') as t;
insert into measurementjsonhistory (timeseries_id, timestamp, json_value, created_on, provenance_id, partition_id)
select 4, t, jsonb_build_object('n', extract(minute from t)::int, 'flag', extract(minute from t)::int % 2 = 0), t + interval '1 second', null, 0
from generate_series('2024-04-01T00:00:00Z'::timestamptz, '2024-04-01T02:55:00Z', interval '5 minutes') as t;
-- bulk: 40000 rows with padding, used by size-limit and memory tests (timeseries 18: station P5)
insert into measurementhistory (timeseries_id, timestamp, double_value, created_on, provenance_id, partition_id)
select 18, t, (extract(epoch from t)::bigint % 1000) / 7.0, t, null, 0
from generate_series('2023-01-01T00:00:00Z'::timestamptz, '2023-01-01T00:00:00Z'::timestamptz + interval '39999 minutes', interval '1 minute') as t;

-- edges ----------------------------------------------------------------------------------
insert into edge (id, edge_data_id, origin_id, destination_id, directed, linegeometry) values
	(1, 16, 14, 15, true, st_setsrid(st_makeline(st_makepoint(11.1, 46.1), st_makepoint(11.2, 46.2)), 4326)),
	(2, 17, 14, 15, false, null);

-- events ---------------------------------------------------------------------------------
insert into location (id, description, geometry) values
	(1, 'Brenner section', st_setsrid(st_makeline(st_makepoint(11.4, 46.9), st_makepoint(11.5, 47.0)), 4326)),
	(2, 'Point loc', st_setsrid(st_makepoint(11.0, 46.0), 4326)),
	(3, 'Poly loc', st_setsrid(st_makepolygon(st_makeline(array[st_makepoint(0,0), st_makepoint(0,1), st_makepoint(1,1), st_makepoint(0,0)])), 4326));
insert into event (id, uuid, event_series_uuid, origin, category, description, name, event_interval, created_on, meta_data_id, location_id, provenance_id) values
	(1, 'e0000000-0000-0000-0000-000000000001', 's0000000-0000-0000-0000-000000000001', 'A22', 'roadworks', 'Work 1', 'W1', '[2022-01-01 00:00, 2022-01-05 00:00)', '2022-01-01T00:00:00Z', 1, 1, 1),
	(2, 'e0000000-0000-0000-0000-000000000002', 's0000000-0000-0000-0000-000000000001', 'A22', 'roadworks', 'Work 1b', 'W1b', '[2022-01-05 00:00, 2022-02-01 00:00)', '2022-01-05T00:00:00Z', null, 2, null),
	(3, 'e0000000-0000-0000-0000-000000000003', 's0000000-0000-0000-0000-000000000002', 'A22', 'accident', 'Open ended', 'A1', '[2022-01-03 00:00,)', '2022-01-03T00:00:00Z', null, null, 2),
	(4, 'e0000000-0000-0000-0000-000000000004', 's0000000-0000-0000-0000-000000000003', 'PROVINCE_BZ', 'snow', 'Snow', 'S1', '[2023-01-01 00:00, 2023-01-02 00:00)', '2023-01-01T00:00:00Z', null, 3, null),
	(5, 'e0000000-0000-0000-0000-000000000005', 's0000000-0000-0000-0000-000000000004', 'OTHER', 'misc', 'Hidden', 'H1', '[2022-01-01 00:00, 2022-01-05 00:00)', '2022-01-01T00:00:00Z', null, null, null);

analyze;
