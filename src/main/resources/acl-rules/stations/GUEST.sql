-- SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
--
-- SPDX-License-Identifier: AGPL-3.0-or-later

-- OPEN DATA WHERE CLAUSE FOR THE GUEST ACCOUNT
-- This is the default role, which will be concatenated with a logical OR operator
-- to all other rules.
-- It defines OPEN DATA RULES seen by all guests, that is, people that are not logged in
(
    -- station types that are always open, regardless of the origin
	s.stationtype in (
		'NOI-Place',
		'CreativeIndustry',
		'Bicycle',
		'Bicyclestationbay',
		'BikeCounter',
		'BikesharingStation',
		'BikeParking',
		'BikeParkingBay',
		'BikeParkingLocation',
		'CarpoolingHub',
		'CarpoolingService',
		'CarpoolingUser',
		'CarpoolingTrip',
		'CarsharingCar',
		'CarsharingStation',
		'CompanyGamificationAction',
		'GamificationAction',
		'EChargingPlug',
		'EChargingStation',
		'Flight',
		'Streetstation',
		'ParkingSensor',
		'ParkingFacility',
		'Culture',
		'ON_DEMAND_VEHICLE',
		'ON_DEMAND_ITINERARY',
		'BIKE_CHARGER',
		'BIKE_CHARGER_BAY',
		'WeatherForecastService',
		'WebStatistics',
		'IndoorStation',
		'TrafficForecast'
	)

    -- station types that are only partly open, constrained by the origin and/or datatype
	or (s.stationtype = 'WeatherForecast' and s.origin != 'EUREGIO')
	or (s.stationtype = 'EnvironmentStation' and s.origin = 'APPATN-open')
	or (s.stationtype = 'EnvironmentStation' and t.cname = 'EAQI-NO2')
	or (s.stationtype = 'LinkStation' and (s.origin is null or s.origin = 'NOI'))
	or (s.stationtype = 'LinkStation' and s.origin = 'A22' and t.cname in ('lds_leggeri_desc', 'lds_pesanti_desc'))
	or (s.stationtype = 'MeteoStation' and s.origin in ('meteotrentino', 'SIAG', 'EURAC'))
	or (s.stationtype = 'ParkingStation' and s.origin in ('FAMAS', 'FBK', 'Municipality Merano', 'skidata', 'Municipality Bolzano', 'STA', 'GARDENA'))
	or (s.stationtype = 'RWISstation' and s.origin = 'InfoMobility')
	or (s.stationtype = 'BluetoothStation' and s.origin = 'bluetoothbox')
	or (s.stationtype = 'PeopleCounter' and s.origin = 'systems-merano')

    -- special rules
	or (s.origin = 'APPABZ' and me.period = 3600)
	or (s.origin = 'ON_DEMAND_MERANO')
	or (s.origin = 'ON_DEMAND_BADIA')
)
