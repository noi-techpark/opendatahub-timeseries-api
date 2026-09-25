create extension if not exists postgis;
--
-- PostgreSQL database dump
--


-- Dumped from database version 16.14
-- Dumped by pg_dump version 16.14


--
-- Name: intimev2; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA IF NOT EXISTS intimev2;




--
-- Name: edge; Type: TABLE; Schema: intimev2; Owner: -
--

CREATE TABLE intimev2.edge (
    id bigint NOT NULL,
    directed boolean DEFAULT true NOT NULL,
    linegeometry public.geometry(Geometry,25832),
    destination_id bigint,
    edge_data_id bigint NOT NULL,
    origin_id bigint
);


--
-- Name: event; Type: TABLE; Schema: intimev2; Owner: -
--

CREATE TABLE intimev2.event (
    id bigint NOT NULL,
    category character varying(255) NOT NULL,
    event_series_uuid character varying(255) NOT NULL,
    created_on timestamp without time zone NOT NULL,
    description text,
    event_interval tsrange,
    origin character varying(255) NOT NULL,
    uuid character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    location_id bigint,
    meta_data_id bigint,
    provenance_id bigint NOT NULL
);


--
-- Name: location; Type: TABLE; Schema: intimev2; Owner: -
--

CREATE TABLE intimev2.location (
    id bigint NOT NULL,
    description text,
    geometry public.geometry
);


--
-- Name: measurement; Type: TABLE; Schema: intimev2; Owner: -
--

CREATE TABLE intimev2.measurement (
    created_on timestamp without time zone NOT NULL,
    "timestamp" timestamp without time zone NOT NULL,
    double_value double precision NOT NULL,
    provenance_id bigint,
    timeseries_id integer NOT NULL
);


--
-- Name: measurementhistory; Type: TABLE; Schema: intimev2; Owner: -
--

CREATE TABLE intimev2.measurementhistory (
    created_on timestamp without time zone NOT NULL,
    "timestamp" timestamp without time zone NOT NULL,
    double_value double precision NOT NULL,
    provenance_id bigint,
    timeseries_id integer,
    partition_id smallint DEFAULT 1 NOT NULL
);


--
-- Name: measurementjson; Type: TABLE; Schema: intimev2; Owner: -
--

CREATE TABLE intimev2.measurementjson (
    created_on timestamp without time zone NOT NULL,
    "timestamp" timestamp without time zone NOT NULL,
    json_value jsonb,
    provenance_id bigint,
    timeseries_id integer NOT NULL
);


--
-- Name: measurementjsonhistory; Type: TABLE; Schema: intimev2; Owner: -
--

CREATE TABLE intimev2.measurementjsonhistory (
    created_on timestamp without time zone NOT NULL,
    "timestamp" timestamp without time zone NOT NULL,
    json_value jsonb,
    provenance_id bigint,
    json_value_md5 character varying(32) GENERATED ALWAYS AS (md5((json_value)::text)) STORED,
    timeseries_id integer,
    partition_id smallint DEFAULT 1 NOT NULL
);


--
-- Name: measurementstring; Type: TABLE; Schema: intimev2; Owner: -
--

CREATE TABLE intimev2.measurementstring (
    created_on timestamp without time zone NOT NULL,
    "timestamp" timestamp without time zone NOT NULL,
    string_value text NOT NULL,
    provenance_id bigint,
    timeseries_id integer NOT NULL
);


--
-- Name: measurementstringhistory; Type: TABLE; Schema: intimev2; Owner: -
--

CREATE TABLE intimev2.measurementstringhistory (
    created_on timestamp without time zone NOT NULL,
    "timestamp" timestamp without time zone NOT NULL,
    string_value text NOT NULL,
    provenance_id bigint,
    timeseries_id integer NOT NULL,
    partition_id smallint DEFAULT 1 NOT NULL
);


--
-- Name: metadata; Type: TABLE; Schema: intimev2; Owner: -
--

CREATE TABLE intimev2.metadata (
    id bigint NOT NULL,
    created_on timestamp without time zone,
    json jsonb,
    station_id bigint
);


--
-- Name: partition; Type: TABLE; Schema: intimev2; Owner: -
--

CREATE TABLE intimev2.partition (
    id smallint NOT NULL,
    name character varying(60) NOT NULL,
    description text
);


--
-- Name: partition_def; Type: TABLE; Schema: intimev2; Owner: -
--

CREATE TABLE intimev2.partition_def (
    id integer NOT NULL,
    partition_id smallint NOT NULL,
    origin character varying(255),
    stationtype character varying(255),
    type_id bigint,
    period integer
);


--
-- Name: provenance; Type: TABLE; Schema: intimev2; Owner: -
--

CREATE TABLE intimev2.provenance (
    id bigint NOT NULL,
    data_collector character varying(255) NOT NULL,
    data_collector_version character varying(255),
    lineage character varying(255) NOT NULL,
    uuid character varying(255)
);


--
-- Name: station; Type: TABLE; Schema: intimev2; Owner: -
--

CREATE TABLE intimev2.station (
    id bigint NOT NULL,
    active boolean,
    available boolean DEFAULT true NOT NULL,
    name character varying(255) NOT NULL,
    origin character varying(255),
    pointprojection public.geometry,
    stationcode character varying(255) NOT NULL,
    stationtype character varying(255) NOT NULL,
    meta_data_id bigint,
    parent_id bigint
);


--
-- Name: timeseries; Type: TABLE; Schema: intimev2; Owner: -
--

CREATE TABLE intimev2.timeseries (
    id integer NOT NULL,
    station_id bigint NOT NULL,
    type_id bigint NOT NULL,
    period integer NOT NULL,
    value_table character varying(60) NOT NULL,
    partition_id smallint DEFAULT 1 NOT NULL
);


--
-- Name: type; Type: TABLE; Schema: intimev2; Owner: -
--

CREATE TABLE intimev2.type (
    id bigint NOT NULL,
    cname character varying(255) NOT NULL,
    created_on timestamp without time zone,
    cunit character varying(255),
    description character varying(255),
    rtype character varying(255),
    meta_data_id bigint
);


--
-- Name: type_metadata; Type: TABLE; Schema: intimev2; Owner: -
--

CREATE TABLE intimev2.type_metadata (
    id bigint NOT NULL,
    created_on timestamp without time zone,
    json jsonb,
    type_id bigint
);


--
-- Name: edge edge_pkey; Type: CONSTRAINT; Schema: intimev2; Owner: -
--

ALTER TABLE ONLY intimev2.edge
    ADD CONSTRAINT edge_pkey PRIMARY KEY (id);


--
-- Name: event event_pkey; Type: CONSTRAINT; Schema: intimev2; Owner: -
--

ALTER TABLE ONLY intimev2.event
    ADD CONSTRAINT event_pkey PRIMARY KEY (id);


--
-- Name: location location_pkey; Type: CONSTRAINT; Schema: intimev2; Owner: -
--

ALTER TABLE ONLY intimev2.location
    ADD CONSTRAINT location_pkey PRIMARY KEY (id);


--
-- Name: metadata metadata_pkey; Type: CONSTRAINT; Schema: intimev2; Owner: -
--

ALTER TABLE ONLY intimev2.metadata
    ADD CONSTRAINT metadata_pkey PRIMARY KEY (id);


--
-- Name: partition_def partition_def_pkey; Type: CONSTRAINT; Schema: intimev2; Owner: -
--

ALTER TABLE ONLY intimev2.partition_def
    ADD CONSTRAINT partition_def_pkey PRIMARY KEY (id);


--
-- Name: partition partition_pkey; Type: CONSTRAINT; Schema: intimev2; Owner: -
--

ALTER TABLE ONLY intimev2.partition
    ADD CONSTRAINT partition_pkey PRIMARY KEY (id);


--
-- Name: provenance provenance_pkey; Type: CONSTRAINT; Schema: intimev2; Owner: -
--

ALTER TABLE ONLY intimev2.provenance
    ADD CONSTRAINT provenance_pkey PRIMARY KEY (id);


--
-- Name: station station_pkey; Type: CONSTRAINT; Schema: intimev2; Owner: -
--

ALTER TABLE ONLY intimev2.station
    ADD CONSTRAINT station_pkey PRIMARY KEY (id);


--
-- Name: timeseries timeseries_pkey; Type: CONSTRAINT; Schema: intimev2; Owner: -
--

ALTER TABLE ONLY intimev2.timeseries
    ADD CONSTRAINT timeseries_pkey PRIMARY KEY (id);


--
-- Name: type_metadata type_metadata_pkey; Type: CONSTRAINT; Schema: intimev2; Owner: -
--

ALTER TABLE ONLY intimev2.type_metadata
    ADD CONSTRAINT type_metadata_pkey PRIMARY KEY (id);


--
-- Name: type type_pkey; Type: CONSTRAINT; Schema: intimev2; Owner: -
--

ALTER TABLE ONLY intimev2.type
    ADD CONSTRAINT type_pkey PRIMARY KEY (id);


--
-- Name: edge uc_edge_edge_data_id; Type: CONSTRAINT; Schema: intimev2; Owner: -
--

ALTER TABLE ONLY intimev2.edge
    ADD CONSTRAINT uc_edge_edge_data_id UNIQUE (edge_data_id);


--
-- Name: event uc_event_uuid; Type: CONSTRAINT; Schema: intimev2; Owner: -
--

ALTER TABLE ONLY intimev2.event
    ADD CONSTRAINT uc_event_uuid UNIQUE (uuid);


--
-- Name: partition_def uc_partition_def; Type: CONSTRAINT; Schema: intimev2; Owner: -
--

ALTER TABLE ONLY intimev2.partition_def
    ADD CONSTRAINT uc_partition_def UNIQUE NULLS NOT DISTINCT (origin, stationtype, type_id, period);


--
-- Name: provenance uc_provenance_lineage_data_collector_data_collector_version; Type: CONSTRAINT; Schema: intimev2; Owner: -
--

ALTER TABLE ONLY intimev2.provenance
    ADD CONSTRAINT uc_provenance_lineage_data_collector_data_collector_version UNIQUE (lineage, data_collector, data_collector_version);


--
-- Name: provenance uc_provenance_uuid; Type: CONSTRAINT; Schema: intimev2; Owner: -
--

ALTER TABLE ONLY intimev2.provenance
    ADD CONSTRAINT uc_provenance_uuid UNIQUE (uuid);


--
-- Name: station uc_station_stationcode_stationtype; Type: CONSTRAINT; Schema: intimev2; Owner: -
--

ALTER TABLE ONLY intimev2.station
    ADD CONSTRAINT uc_station_stationcode_stationtype UNIQUE (stationcode, stationtype);


--
-- Name: timeseries uc_timeseries_station_id_type_id_period; Type: CONSTRAINT; Schema: intimev2; Owner: -
--

ALTER TABLE ONLY intimev2.timeseries
    ADD CONSTRAINT uc_timeseries_station_id_type_id_period UNIQUE (station_id, type_id, period, value_table);


--
-- Name: partition uc_tpartition_name; Type: CONSTRAINT; Schema: intimev2; Owner: -
--

ALTER TABLE ONLY intimev2.partition
    ADD CONSTRAINT uc_tpartition_name UNIQUE (name);


--
-- Name: type uc_type_cname; Type: CONSTRAINT; Schema: intimev2; Owner: -
--

ALTER TABLE ONLY intimev2.type
    ADD CONSTRAINT uc_type_cname UNIQUE (cname);


--
-- Name: idx_measurement_timeseries_ts; Type: INDEX; Schema: intimev2; Owner: -
--

CREATE UNIQUE INDEX idx_measurement_timeseries_ts ON intimev2.measurement USING btree (timeseries_id);


--
-- Name: idx_measurement_timestamp; Type: INDEX; Schema: intimev2; Owner: -
--

CREATE INDEX idx_measurement_timestamp ON intimev2.measurement USING btree ("timestamp" DESC);


--
-- Name: idx_measurementhistory_timeseries_ts; Type: INDEX; Schema: intimev2; Owner: -
--

CREATE INDEX idx_measurementhistory_timeseries_ts ON intimev2.measurementhistory USING btree (timeseries_id, "timestamp");


--
-- Name: idx_measurementjson_timeseries_ts; Type: INDEX; Schema: intimev2; Owner: -
--

CREATE UNIQUE INDEX idx_measurementjson_timeseries_ts ON intimev2.measurementjson USING btree (timeseries_id);


--
-- Name: idx_measurementjson_timestamp; Type: INDEX; Schema: intimev2; Owner: -
--

CREATE INDEX idx_measurementjson_timestamp ON intimev2.measurementjson USING btree ("timestamp" DESC);


--
-- Name: idx_measurementjsonhistory_timeseries_ts; Type: INDEX; Schema: intimev2; Owner: -
--

CREATE INDEX idx_measurementjsonhistory_timeseries_ts ON intimev2.measurementjsonhistory USING btree (timeseries_id, "timestamp");


--
-- Name: idx_measurementstring_timeseries_ts; Type: INDEX; Schema: intimev2; Owner: -
--

CREATE UNIQUE INDEX idx_measurementstring_timeseries_ts ON intimev2.measurementstring USING btree (timeseries_id);


--
-- Name: idx_measurementstring_timestamp; Type: INDEX; Schema: intimev2; Owner: -
--

CREATE INDEX idx_measurementstring_timestamp ON intimev2.measurementstring USING btree ("timestamp" DESC);


--
-- Name: idx_measurementstringhistory_timeseries_ts; Type: INDEX; Schema: intimev2; Owner: -
--

CREATE INDEX idx_measurementstringhistory_timeseries_ts ON intimev2.measurementstringhistory USING btree (timeseries_id, "timestamp");


--
-- Name: idx_metadata_history; Type: INDEX; Schema: intimev2; Owner: -
--

CREATE INDEX idx_metadata_history ON intimev2.metadata USING btree (station_id, created_on);


--
-- Name: idx_partition_def_origin; Type: INDEX; Schema: intimev2; Owner: -
--

CREATE INDEX idx_partition_def_origin ON intimev2.partition_def USING btree (origin);


--
-- Name: idx_partition_def_period; Type: INDEX; Schema: intimev2; Owner: -
--

CREATE INDEX idx_partition_def_period ON intimev2.partition_def USING btree (period);


--
-- Name: idx_partition_def_stationtype; Type: INDEX; Schema: intimev2; Owner: -
--

CREATE INDEX idx_partition_def_stationtype ON intimev2.partition_def USING btree (stationtype);


--
-- Name: idx_partition_def_type; Type: INDEX; Schema: intimev2; Owner: -
--

CREATE INDEX idx_partition_def_type ON intimev2.partition_def USING btree (type_id);


--
-- Name: idx_station_metadata; Type: INDEX; Schema: intimev2; Owner: -
--

CREATE INDEX idx_station_metadata ON intimev2.station USING btree (meta_data_id);


--
-- Name: idx_station_parent; Type: INDEX; Schema: intimev2; Owner: -
--

CREATE INDEX idx_station_parent ON intimev2.station USING btree (parent_id);


--
-- Name: idx_station_parkingstation; Type: INDEX; Schema: intimev2; Owner: -
--

CREATE INDEX idx_station_parkingstation ON intimev2.station USING btree (id) WHERE ((stationtype)::text = 'ParkingStation'::text);


--
-- Name: idx_timeseries_station; Type: INDEX; Schema: intimev2; Owner: -
--

CREATE INDEX idx_timeseries_station ON intimev2.timeseries USING btree (station_id);


--
-- Name: idx_timeseries_type; Type: INDEX; Schema: intimev2; Owner: -
--

CREATE INDEX idx_timeseries_type ON intimev2.timeseries USING btree (type_id);


--
-- Name: partial_active_stationtypes_idx; Type: INDEX; Schema: intimev2; Owner: -
--

CREATE UNIQUE INDEX partial_active_stationtypes_idx ON intimev2.station USING btree (active, stationtype, id) WHERE (active = true);


--
-- PostgreSQL database dump complete
--


