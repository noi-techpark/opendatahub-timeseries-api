-- SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
--
-- SPDX-License-Identifier: AGPL-3.0-or-later

-- A22 Trafficdata of A22
(
    (s.stationtype = 'TrafficSensor' and s.origin = 'A22')
    or (s.stationtype = 'TrafficForecast' and s.origin = 'a22-web-site')
    or (s.stationtype = 'TrafficDirection' and s.origin = 'A22')
)
