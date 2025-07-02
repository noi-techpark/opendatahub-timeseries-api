-- SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
--
-- SPDX-License-Identifier: AGPL-3.0-or-later

-- Comune Di Bolzano
(
	s.stationtype = 'Mobilestation'
	or (s.stationtype = 'TrafficSensor' and s.origin = 'FAMAS-traffic')
)
