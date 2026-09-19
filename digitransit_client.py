#!/usr/bin/env python3
"""HSL Digitransit API v2 Client for Python.

Provides routing, itinerary planning, geocoding, and nearest departures
for the Helsinki Metropolitan Area (HSL) using the Digitransit GraphQL API.
"""

import json
import os
import time
import urllib.parse
import urllib.request
from datetime import datetime, timezone, timedelta
from pathlib import Path

# Helsinki Timezone with graceful fallback
try:
    from zoneinfo import ZoneInfo
    HELSINKI_TZ = ZoneInfo("Europe/Helsinki")
except Exception:
    now_m = datetime.now(timezone.utc).month
    # Finland is UTC+3 in summer (Apr-Oct), UTC+2 in winter
    offset = 3 if 4 <= now_m <= 10 else 2
    HELSINKI_TZ = timezone(timedelta(hours=offset), name="Europe/Helsinki")


# Digitransit API endpoints
ROUTING_ENDPOINT = "https://api.digitransit.fi/routing/v2/hsl/gtfs/v1"
GEOCODING_ENDPOINT = "https://api.digitransit.fi/geocoding/v1/search"
HEADER_API_KEY = "digitransit-subscription-key"

# Common Helsinki presets for instant offline/fallback resolution
PRESET_LOCATIONS = [
    {"name": "Kamppi", "label": "Kamppi, Urho Kekkosen katu 1, Helsinki", "lat": 60.1687, "lon": 24.9332},
    {"name": "Helsingin päärautatieasema", "label": "Rautatieasema, Kaivokatu 1, Helsinki", "lat": 60.1708, "lon": 24.9414},
    {"name": "Rautatieasema", "label": "Rautatieasema, Kaivokatu 1, Helsinki", "lat": 60.1708, "lon": 24.9414},
    {"name": "Pasila", "label": "Pasila, Firdonkatu 2, Helsinki", "lat": 60.1989, "lon": 24.9304},
    {"name": "Mall of Tripla", "label": "Mall of Tripla, Firdonkatu 2, Helsinki", "lat": 60.1989, "lon": 24.9304},
    {"name": "Tripla", "label": "Mall of Tripla, Firdonkatu 2, Helsinki", "lat": 60.1989, "lon": 24.9304},
    {"name": "Otaniemi", "label": "Aalto-yliopisto, Otakaari 1, Espoo", "lat": 60.1871, "lon": 24.8290},
    {"name": "Aalto-yliopisto", "label": "Aalto-yliopisto, Otakaari 1, Espoo", "lat": 60.1871, "lon": 24.8290},
    {"name": "Helsinki-Vantaan lentoasema", "label": "Helsinki-Vantaan lentoasema, Vantaa", "lat": 60.3172, "lon": 24.9633},
    {"name": "Lentoasema", "label": "Helsinki-Vantaan lentoasema, Vantaa", "lat": 60.3172, "lon": 24.9633},
    {"name": "Airport", "label": "Helsinki-Vantaan lentoasema, Vantaa", "lat": 60.3172, "lon": 24.9633},
    {"name": "Itis", "label": "Kauppakeskus Itis, Itäkatu 1-7, Helsinki", "lat": 60.2117, "lon": 25.0818},
    {"name": "Itäkeskus", "label": "Itäkeskus, Helsinki", "lat": 60.2117, "lon": 25.0818},
    {"name": "Redi", "label": "Kauppakeskus Redi, Hermannin rantatie 5, Helsinki", "lat": 60.1872, "lon": 24.9794},
    {"name": "Kalasatama", "label": "Kalasatama, Helsinki", "lat": 60.1872, "lon": 24.9794},
    {"name": "Jumbo", "label": "Kauppakeskus Jumbo, Vantaanportinkatu 3, Vantaa", "lat": 60.2923, "lon": 24.9659},
    {"name": "Sello", "label": "Kauppakeskus Sello, Leppävaara, Espoo", "lat": 60.2185, "lon": 24.8115},
    {"name": "Leppävaara", "label": "Leppävaara, Espoo", "lat": 60.2185, "lon": 24.8115},
    {"name": "Tapiola", "label": "Tapiola, Espoo", "lat": 60.1764, "lon": 24.8055},
    {"name": "Matinkylä", "label": "Matinkylä, Espoo", "lat": 60.1585, "lon": 24.7438},
    {"name": "Vuosaari", "label": "Vuosaari, Helsinki", "lat": 60.2078, "lon": 25.1444},
    {"name": "Tikkurila", "label": "Tikkurila, Vantaa", "lat": 60.2933, "lon": 25.0441},
    {"name": "Hakaniemi", "label": "Hakaniemi, Helsinki", "lat": 60.1788, "lon": 24.9512},
    {"name": "Kallio", "label": "Kallio, Helsinki", "lat": 60.1844, "lon": 24.9500},
    {"name": "Oodi", "label": "Keskustakirjasto Oodi, Töölönlahdenkatu 4, Helsinki", "lat": 60.1740, "lon": 24.9382},
    {"name": "Meilahti", "label": "Meilahden sairaala-alue, Haartmaninkatu 4, Helsinki", "lat": 60.1901, "lon": 24.9065},
    {"name": "Mannerheimintie 1", "label": "Mannerheimintie 1, Helsinki", "lat": 60.1698, "lon": 24.9406},
]


class DigitransitClient:
    """Client for HSL Digitransit Routing and Geocoding APIs."""

    def __init__(self, api_key: str = None):
        # Read API key from passed argument or environment variables
        self.api_key = api_key or os.getenv("DIGITRANSIT_API_KEY") or os.getenv("HSL_API_KEY")
        if not self.api_key:
            # Check local .env file in project directory or parent directory
            self._load_dotenv_keys()

    def _load_dotenv_keys(self):
        for candidate in [Path(__file__).resolve().parent / ".env", Path(__file__).resolve().parent.parent / ".env"]:
            if candidate.exists():
                try:
                    for line in candidate.read_text(encoding="utf-8").splitlines():
                        line = line.strip()
                        if line and not line.startswith("#") and "=" in line:
                            k, v = line.split("=", 1)
                            k, v = k.strip(), v.strip().strip('"').strip("'")
                            if k in ("DIGITRANSIT_API_KEY", "HSL_API_KEY") and not self.api_key:
                                self.api_key = v
                except Exception:
                    pass

    def _make_post_request(self, url: str, json_data: dict) -> dict:
        """Executes a JSON POST request with Digitransit API key header."""
        payload = json.dumps(json_data).encode("utf-8")
        req = urllib.request.Request(
            url,
            data=payload,
            headers={
                "Content-Type": "application/json; charset=utf-8",
                "User-Agent": "HslNearYou-MCP/1.0",
                **( {HEADER_API_KEY: self.api_key} if self.api_key else {} )
            }
        )
        try:
            with urllib.request.urlopen(req, timeout=15) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as ex:
            body = ex.read().decode("utf-8", errors="replace")
            if ex.code in (401, 403):
                raise ValueError(
                    "Digitransit API key is missing, invalid, or expired. "
                    "Get a free API key at https://portal-api.digitransit.fi/ and save it as DIGITRANSIT_API_KEY."
                ) from ex
            elif ex.code == 429:
                raise ValueError("Digitransit API rate limit reached (HTTP 429). Please try again shortly.") from ex
            raise RuntimeError(f"Digitransit API HTTP Error {ex.code}: {body}") from ex

    def _make_get_request(self, url: str) -> dict:
        """Executes an HTTP GET request."""
        req = urllib.request.Request(
            url,
            headers={
                "User-Agent": "HslNearYou-MCP/1.0",
                **( {HEADER_API_KEY: self.api_key} if self.api_key else {} )
            }
        )
        try:
            with urllib.request.urlopen(req, timeout=12) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as ex:
            if ex.code in (401, 403):
                # Fallback to geocoding without key or presets
                return {}
            raise

    # -------------------------------------------------------------------------
    # Geocoding / Place Search
    # -------------------------------------------------------------------------

    def search_places(self, query: str, max_results: int = 5) -> list[dict]:
        """Search for locations, stops, or addresses in the Helsinki region."""
        q = query.strip()
        if not q:
            return []

        # 1. Check presets first
        preset_matches = [
            {"label": p["label"], "name": p["name"], "lat": p["lat"], "lon": p["lon"]}
            for p in PRESET_LOCATIONS
            if q.lower() in p["name"].lower() or q.lower() in p["label"].lower()
        ]

        # 2. Try Digitransit Geocoding API if key is available
        if self.api_key:
            params = urllib.parse.urlencode({
                "text": q,
                "boundary.circle.lat": "60.1699",
                "boundary.circle.lon": "24.9384",
                "boundary.circle.radius": "75",
                "size": str(max_results)
            })
            url = f"{GEOCODING_ENDPOINT}?{params}"
            try:
                data = self._make_get_request(url)
                features = data.get("features", [])
                results = []
                for f in features:
                    coords = f.get("geometry", {}).get("coordinates", [])
                    props = f.get("properties", {})
                    if len(coords) >= 2:
                        results.append({
                            "label": props.get("label", q),
                            "name": props.get("name") or props.get("label", q),
                            "lat": coords[1],
                            "lon": coords[0]
                        })
                if results:
                    return results
            except Exception:
                pass

        # 3. Fallback: OpenStreetMap Nominatim for Finland
        try:
            nom_params = urllib.parse.urlencode({
                "q": f"{q}, Uusimaa, Finland",
                "format": "json",
                "countrycodes": "fi",
                "limit": str(max_results)
            })
            nom_url = f"https://nominatim.openstreetmap.org/search?{nom_params}"
            req = urllib.request.Request(
                nom_url,
                headers={"User-Agent": "HslNearYou-MCP/1.0 (transit-agent@lightsaber.biz)"}
            )
            with urllib.request.urlopen(req, timeout=6) as response:
                nom_data = json.loads(response.read().decode("utf-8"))
                nom_results = []
                for item in nom_data:
                    nom_results.append({
                        "label": item.get("display_name", q),
                        "name": item.get("name") or q,
                        "lat": float(item["lat"]),
                        "lon": float(item["lon"])
                    })
                if nom_results:
                    return nom_results
        except Exception:
            pass

        return preset_matches

    search_locations = search_places

    def resolve_location(self, place: str) -> dict | None:
        """Resolves place name or 'lat,lon' string to {lat, lon, label}."""
        p = place.strip()
        # Direct coordinates check (e.g. "60.1687, 24.9332")
        if "," in p:
            parts = [x.strip() for x in p.split(",")]
            if len(parts) == 2:
                try:
                    lat, lon = float(parts[0]), float(parts[1])
                    return {"lat": lat, "lon": lon, "label": f"Coordinates ({lat:.4f}, {lon:.4f})"}
                except ValueError:
                    pass

        results = self.search_places(p, max_results=1)
        if results:
            return results[0]
        return None

    # -------------------------------------------------------------------------
    # Route Planning (Itineraries)
    # -------------------------------------------------------------------------

    def plan_route(
        self,
        from_place: str,
        to_place: str,
        num_itineraries: int = 3,
        time_str: str = "",
        date_str: str = ""
    ) -> dict:
        """Plans transit routes from origin to destination using HSL Digitransit GraphQL API."""
        from_loc = self.resolve_location(from_place)
        if not from_loc:
            return {
                "error": f"Could not find or resolve origin location: '{from_place}'. Please specify a known address, landmark, or coordinates."
            }

        to_loc = self.resolve_location(to_place)
        if not to_loc:
            return {
                "error": f"Could not find or resolve destination location: '{to_place}'. Please specify a known address, landmark, or coordinates."
            }

        # Date & Time handling
        now_helsinki = datetime.now(HELSINKI_TZ)
        plan_date = date_str if date_str else now_helsinki.strftime("%Y-%m-%d")
        plan_time = time_str if time_str else now_helsinki.strftime("%H:%M:%S")

        query = f"""
        {{
          plan(
            from: {{lat: {from_loc['lat']}, lon: {from_loc['lon']}}},
            to: {{lat: {to_loc['lat']}, lon: {to_loc['lon']}}},
            numItineraries: {num_itineraries},
            date: "{plan_date}",
            time: "{plan_time}",
            transportModes: [{{mode: BUS}}, {{mode: TRAM}}, {{mode: SUBWAY}}, {{mode: RAIL}}, {{mode: FERRY}}]
          ) {{
            itineraries {{
              duration
              startTime
              endTime
              walkDistance
              legs {{
                mode
                startTime
                endTime
                duration
                distance
                realTime
                headsign
                route {{
                  shortName
                  mode
                }}
                from {{
                  name
                  stop {{
                    code
                    zoneId
                  }}
                }}
                to {{
                  name
                  stop {{
                    code
                    zoneId
                  }}
                }}
              }}
            }}
          }}
        }}
        """

        try:
            data = self._make_post_request(ROUTING_ENDPOINT, {"query": query})
        except ValueError as ex:
            return {
                "status": "missing_api_key",
                "error": str(ex),
                "instructions": "HSL Digitransit API requires a free API key. Get one at https://portal-api.digitransit.fi/ and save it as DIGITRANSIT_API_KEY in C:\\Users\\antti\\Kehitys\\apps\\hslnearyou\\.env"
            }
        except Exception as ex:
            return {"status": "error", "error": str(ex)}

        if "errors" in data and data["errors"]:
            error_msg = data["errors"][0].get("message", "Unknown GraphQL error")
            return {"error": f"Digitransit GraphQL error: {error_msg}"}

        itineraries_raw = data.get("data", {}).get("plan", {}).get("itineraries", [])
        if not itineraries_raw:
            return {
                "status": "no_routes_found",
                "message": f"No public transit itineraries found between {from_loc['label']} and {to_loc['label']}."
            }

        options = []
        for idx, itin in enumerate(itineraries_raw, 1):
            dur_min = int(round(itin.get("duration", 0) / 60))
            st_sec = int(itin.get("startTime", 0) / 1000 if itin.get("startTime", 0) > 10_000_000_000 else itin.get("startTime", 0))
            et_sec = int(itin.get("endTime", 0) / 1000 if itin.get("endTime", 0) > 10_000_000_000 else itin.get("endTime", 0))

            dep_dt = datetime.fromtimestamp(st_sec, tz=HELSINKI_TZ)
            arr_dt = datetime.fromtimestamp(et_sec, tz=HELSINKI_TZ)

            # Ticket zones
            zones = set()
            legs_formatted = []
            for leg in itin.get("legs", []):
                route_obj = leg.get("route") or {}
                mode = route_obj.get("mode") or leg.get("mode", "WALK")
                route_badge = route_obj.get("shortName")
                headsign = leg.get("headsign") or ""
                leg_dur_min = int(round(leg.get("duration", 0) / 60))
                dist_m = int(round(leg.get("distance", 0)))
                from_stop = leg.get("from", {}).get("name", "Origin")
                to_stop = leg.get("to", {}).get("name", "Destination")

                for stop_obj in [leg.get("from", {}).get("stop"), leg.get("to", {}).get("stop")]:
                    if stop_obj and stop_obj.get("zoneId"):
                        zones.add(stop_obj["zoneId"])

                legs_formatted.append({
                    "mode": mode,
                    "line": route_badge if route_badge else "Walk",
                    "headsign": headsign,
                    "from": from_stop,
                    "to": to_stop,
                    "duration_minutes": leg_dur_min,
                    "distance_meters": dist_m,
                    "realtime": leg.get("realTime", False)
                })

            sorted_zones = "".join(sorted(list(zones))) if zones else "AB"

            options.append({
                "option_number": idx,
                "departure_time": dep_dt.strftime("%H:%M"),
                "arrival_time": arr_dt.strftime("%H:%M"),
                "duration_minutes": dur_min,
                "ticket_zones": sorted_zones,
                "legs_count": len(legs_formatted),
                "legs": legs_formatted
            })

        return {
            "status": "success",
            "origin": from_loc,
            "destination": to_loc,
            "itineraries_count": len(options),
            "itineraries": options
        }

    # -------------------------------------------------------------------------
    # Nearest Departures
    # -------------------------------------------------------------------------

    def get_nearest_departures(self, place_or_address: str, max_stops: int = 3) -> dict:
        """Finds nearest public transit stops and real-time departure countdowns."""
        loc = self.resolve_location(place_or_address)
        if not loc:
            return {"error": f"Could not find or resolve location: '{place_or_address}'"}

        now_sec = int(time.time()) - 30

        query = f"""
        {{
          nearest(
            lat: {loc['lat']},
            lon: {loc['lon']},
            maxDistance: 1200,
            maxResults: {max_stops},
            filterByPlaceTypes: STOP
          ) {{
            edges {{
              node {{
                distance
                place {{
                  ... on Stop {{
                    gtfsId
                    name
                    code
                    vehicleMode
                    stoptimesWithoutPatterns(startTime: {now_sec}, numberOfDepartures: 5, omitNonPickups: true) {{
                      scheduledDeparture
                      realtimeDeparture
                      serviceDay
                      realtime
                      headsign
                      trip {{
                        route {{
                          shortName
                        }}
                      }}
                    }}
                  }}
                }}
              }}
            }}
          }}
        }}
        """

        try:
            data = self._make_post_request(ROUTING_ENDPOINT, {"query": query})
        except ValueError as ve:
            return {
                "status": "missing_api_key",
                "error": str(ve),
                "instructions": "Digitransit API requires a free API key. Register at https://portal-api.digitransit.fi/ and set the DIGITRANSIT_API_KEY environment variable."
            }
        except Exception as ex:
            return {"status": "error", "error": str(ex)}

        if "errors" in data and data["errors"]:
            return {"error": data["errors"][0].get("message", "GraphQL Error")}

        edges = data.get("data", {}).get("nearest", {}).get("edges", [])
        stops_result = []

        now_epoch = int(time.time())

        for edge in edges:
            node = edge.get("node", {})
            place = node.get("place", {})
            dist = node.get("distance", 0)

            departures = []
            for st in place.get("stoptimesWithoutPatterns", []):
                dep_sec = st.get("realtimeDeparture") if st.get("realtime") and st.get("realtimeDeparture", 0) > 0 else st.get("scheduledDeparture", 0)
                tot_epoch = st.get("serviceDay", 0) + dep_sec
                diff_min = int(round((tot_epoch - now_epoch) / 60))

                route_short = st.get("trip", {}).get("route", {}).get("shortName", "?")
                headsign = st.get("headsign", "")

                departures.append({
                    "line": route_short,
                    "headsign": headsign,
                    "in_minutes": max(0, diff_min),
                    "departure_time": datetime.fromtimestamp(tot_epoch, tz=HELSINKI_TZ).strftime("%H:%M"),
                    "realtime": st.get("realtime", False)
                })

            stops_result.append({
                "stop_name": place.get("name", "Pysäkki"),
                "stop_code": place.get("code", ""),
                "distance_meters": dist,
                "vehicle_mode": place.get("vehicleMode", "BUS"),
                "departures": departures
            })

        return {
            "status": "success",
            "location": loc,
            "stops_found": len(stops_result),
            "stops": stops_result
        }
