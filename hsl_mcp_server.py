#!/usr/bin/env python3
"""HSL Transit & Route Planner MCP Server.

Provides tools for AI assistants (Cline, Claude Desktop, Antigravity, etc.)
to plan public transit routes, get real-time departures, and search locations
across the Helsinki Metropolitan Area (HSL) using Digitransit APIs.
"""

import json
import logging
import os
import sys
from pathlib import Path
from dotenv import load_dotenv

if hasattr(sys.stdin, "reconfigure"):
    sys.stdin.reconfigure(encoding="utf-8")
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

# Suppress loggers to avoid polluting stdout (crucial for MCP stdio transport)
logging.basicConfig(level=logging.WARNING, stream=sys.stderr)
for logger_name in ("urllib3", "httpx", "httpcore", "mcp"):
    logging.getLogger(logger_name).setLevel(logging.WARNING)

# Load environment variables (.env in this directory or parent directories)
_project_dir = Path(__file__).resolve().parent
if str(_project_dir) not in sys.path:
    sys.path.insert(0, str(_project_dir))
_env_candidates = [
    _project_dir / ".env",
    _project_dir.parent / ".env",
    Path.cwd() / ".env",
]
for p in _env_candidates:
    if p.exists():
        load_dotenv(dotenv_path=p)
        break
load_dotenv()

from digitransit_client import DigitransitClient, PRESET_LOCATIONS

# Support both mcp 2.x (MCPServer) and mcp 1.x (FastMCP)
try:
    from mcp.server.mcpserver import MCPServer
except ImportError:
    from mcp.server.fastmcp import FastMCP as MCPServer

mcp = MCPServer("HSL Route Planner & Transit Assistant")


def _get_client() -> DigitransitClient:
    """Returns a fresh client instance to capture any newly loaded environment variables."""
    # Re-check .env in case user updated it recently
    env_file = _project_dir / ".env"
    if env_file.exists():
        load_dotenv(dotenv_path=env_file, override=False)
    api_key = os.getenv("DIGITRANSIT_API_KEY", "").strip()
    return DigitransitClient(api_key=api_key if api_key else None)


# -----------------------------------------------------------------------------
# 1. Route Planning Tool
# -----------------------------------------------------------------------------

@mcp.tool()
def plan_hsl_route(
    from_place: str,
    to_place: str,
    time: str = "",
    date: str = "",
    num_itineraries: int = 3
) -> str:
    """Plan public transit routes and connections between two locations in the Helsinki metropolitan area (HSL).

    Supports addresses, stations, stops, and major points of interest (e.g. 'Kamppi', 'Pasila', 'Otaniemi', 'Helsinki-Vantaan lentoasema', 'Mannerheimintie 10, Helsinki').

    Args:
        from_place: Starting location, address, station or landmark (e.g. 'Kamppi', 'Otaniemi, Espoo', 'Leppävaara')
        to_place: Destination location, address, station or landmark (e.g. 'Helsinki-Vantaan lentoasema', 'Mall of Tripla', 'Hakaniemi')
        time: Optional departure time in 'HH:MM' (24-hour format, Europe/Helsinki time, e.g. '08:30', '17:45'). Defaults to current time.
        date: Optional departure date in 'YYYY-MM-DD' format (e.g. '2026-09-19'). Defaults to current date.
        num_itineraries: Number of alternative itineraries to return (default: 3, max: 5).

    Returns:
        JSON string containing route options, departure/arrival times, total duration, transit lines (Bus, Tram, Metro, Train, Ferry), walk legs, and ticket zones.
    """
    client = _get_client()
    res = client.plan_route(
        from_place=from_place,
        to_place=to_place,
        time_str=time,
        date_str=date,
        num_itineraries=num_itineraries
    )

    # If missing key or error, return JSON with helpful guidance
    if res.get("status") == "missing_api_key":
        return json.dumps({
            "status": "missing_api_key",
            "message": "Digitransit API requires a free API key to calculate route itineraries.",
            "how_to_fix": [
                "1. Register for a free Digitransit developer account at https://portal-api.digitransit.fi/",
                "2. Create a subscription to 'Digitransit API v2' and copy the Primary or Secondary key.",
                "3. Set the key in C:\\Users\\antti\\Kehitys\\apps\\hslnearyou\\.env as DIGITRANSIT_API_KEY=<your_key>",
                "4. Alternatively, pass DIGITRANSIT_API_KEY in the 'env' section of your MCP settings file."
            ],
            "resolved_locations": {
                "from": client.resolve_location(from_place),
                "to": client.resolve_location(to_place)
            }
        }, indent=2, ensure_ascii=False)

    if res.get("status") == "error":
        return json.dumps(res, indent=2, ensure_ascii=False)

    # Format a human-readable text summary alongside structured data
    itineraries = res.get("itineraries", [])
    text_lines = [
        f"Transit Routes from {from_place} to {to_place}:",
        f"Found {len(itineraries)} itinerary option(s):"
    ]
    for opt in itineraries:
        opt_num = opt.get("option_number", 1)
        dep_time = opt.get("departure_time", "")
        arr_time = opt.get("arrival_time", "")
        dur = opt.get("duration_minutes", 0)
        zones = opt.get("ticket_zones", "")
        summary_str = f"- Option {opt_num}: {dep_time} -> {arr_time} ({dur} min, Zones: {zones or 'N/A'})"
        legs_detail = []
        for leg in opt.get("legs", []):
            if leg["mode"] == "WALK":
                legs_detail.append(f"    * Walk {leg['distance_meters']}m ({leg['duration_minutes']} min) to {leg['to']}")
            else:
                legs_detail.append(f"    * {leg['mode']} {leg['line']} ({leg['headsign']}): {leg['from']} -> {leg['to']} ({leg['duration_minutes']} min)")
        text_lines.append(summary_str + "\n" + "\n".join(legs_detail))

    res["formatted_summary"] = "\n".join(text_lines)
    return json.dumps(res, indent=2, ensure_ascii=False)


# -----------------------------------------------------------------------------
# 2. Nearby Departures Tool
# -----------------------------------------------------------------------------

@mcp.tool()
def get_nearby_hsl_departures(
    place_or_address: str,
    max_stops: int = 3
) -> str:
    """Get real-time departure countdowns for public transit stops near a place or address in the Helsinki region.

    Args:
        place_or_address: Location name, address, or station (e.g. 'Kamppi', 'Rautatieasema', 'Otaniemi', 'Hakaniemi')
        max_stops: Number of closest stops to query (default: 3)

    Returns:
        JSON string containing nearby stops, distance in meters, and upcoming departures (line badge, destination, countdown minutes, realtime flag).
    """
    client = _get_client()
    res = client.get_nearest_departures(place_or_address=place_or_address, max_stops=max_stops)

    if res.get("status") == "missing_api_key":
        return json.dumps({
            "status": "missing_api_key",
            "message": "Digitransit API requires a free API key to query nearby stop departures.",
            "how_to_fix": [
                "1. Register for a free Digitransit developer account at https://portal-api.digitransit.fi/",
                "2. Create a subscription to 'Digitransit API v2' and copy the key.",
                "3. Set DIGITRANSIT_API_KEY in C:\\Users\\antti\\Kehitys\\apps\\hslnearyou\\.env"
            ],
            "resolved_location": client.resolve_location(place_or_address)
        }, indent=2, ensure_ascii=False)

    if res.get("status") == "error":
        return json.dumps(res, indent=2, ensure_ascii=False)

    # Format human-readable summary
    stops = res.get("stops", [])
    loc_label = res.get("location", {}).get("label", place_or_address)
    text_lines = [f"Upcoming Departures near {loc_label}:"]
    for s in stops:
        text_lines.append(f"\nStop: {s['stop_name']} ({s['stop_code']}) - {s['distance_meters']}m away [{s['vehicle_mode']}]:")
        if not s.get("departures"):
            text_lines.append("  No upcoming departures scheduled.")
        for d in s.get("departures", []):
            rt_flag = " (live)" if d.get("realtime") else ""
            text_lines.append(f"  * Line {d['line']} to {d['headsign']}: {d['in_minutes']} min ({d['departure_time']}){rt_flag}")

    res["formatted_summary"] = "\n".join(text_lines)
    return json.dumps(res, indent=2, ensure_ascii=False)


# -----------------------------------------------------------------------------
# 3. Location Search Tool
# -----------------------------------------------------------------------------

@mcp.tool()
def search_hsl_locations(
    query: str,
    max_results: int = 5
) -> str:
    """Search for locations, stations, landmarks, or addresses in the Helsinki metropolitan area.

    Args:
        query: Name, address, or search term (e.g. 'Kamppi', 'Länsiterminaali', 'Mannerheimintie', 'Alberga')
        max_results: Maximum number of search results to return (default: 5)

    Returns:
        JSON string listing matched places with labels, coordinates (latitude, longitude), and source.
    """
    client = _get_client()
    matches = client.search_locations(query=query, max_results=max_results)
    return json.dumps({
        "status": "success",
        "query": query,
        "count": len(matches),
        "results": matches
    }, indent=2, ensure_ascii=False)


# -----------------------------------------------------------------------------
# Main Entry Point
# -----------------------------------------------------------------------------

if __name__ == "__main__":
    mcp.run(transport="stdio")
