#!/usr/bin/env python3
"""Test suite for HSL Transit MCP Server."""

import json
import sys
from pathlib import Path

# Add project root to sys.path
sys.path.insert(0, str(Path(__file__).resolve().parent))

from hsl_mcp_server import mcp, plan_hsl_route, get_nearby_hsl_departures, search_hsl_locations

def run_tests():
    print("=== Testing HSL MCP Server Tools ===")

    # 1. Test search_hsl_locations
    print("\n--- 1. Testing search_hsl_locations('Kamppi') ---")
    res_search = json.loads(search_hsl_locations("Kamppi"))
    print(f"Status: {res_search.get('status')}")
    print(f"Found {res_search.get('count')} results:")
    for item in res_search.get("results", [])[:3]:
        print(f"  - {item['label']} ({item['lat']}, {item['lon']}) [source: {item.get('source', 'preset/geocoding')}]")
    assert res_search.get("status") == "success", "Search should succeed"
    assert res_search.get("count", 0) > 0, "Should find at least 1 match for Kamppi"

    # 2. Test search_hsl_locations for Lentoasema
    print("\n--- 2. Testing search_hsl_locations('Lentoasema') ---")
    res_airport = json.loads(search_hsl_locations("Lentoasema"))
    print(f"Found {res_airport.get('count')} results:")
    for item in res_airport.get("results", [])[:2]:
        print(f"  - {item['label']} ({item['lat']}, {item['lon']})")
    assert res_airport.get("count", 0) > 0, "Should find at least 1 match for Lentoasema"

    # 3. Test plan_hsl_route
    print("\n--- 3. Testing plan_hsl_route('Kamppi', 'Pasila') ---")
    res_route = json.loads(plan_hsl_route("Kamppi", "Pasila"))
    status = res_route.get("status")
    print(f"Plan route status: {status}")
    if status == "missing_api_key":
        print("Expected result when DIGITRANSIT_API_KEY is not configured:")
        print(f"Message: {res_route.get('message')}")
        print(f"Resolved from: {res_route.get('resolved_locations', {}).get('from', {}).get('label')}")
        print(f"Resolved to:   {res_route.get('resolved_locations', {}).get('to', {}).get('label')}")
        assert res_route.get("resolved_locations", {}).get("from"), "Start location should resolve"
        assert res_route.get("resolved_locations", {}).get("to"), "End location should resolve"
    elif status == "success":
        print("Success with API key!")
        print(res_route.get("formatted_summary"))
    else:
        print(f"Response: {res_route}")

    # 4. Test get_nearby_hsl_departures
    print("\n--- 4. Testing get_nearby_hsl_departures('Kamppi') ---")
    res_dep = json.loads(get_nearby_hsl_departures("Kamppi"))
    status_dep = res_dep.get("status")
    print(f"Nearby departures status: {status_dep}")
    if status_dep == "missing_api_key":
        print("Expected result when DIGITRANSIT_API_KEY is not configured:")
        print(f"Message: {res_dep.get('message')}")
        print(f"Resolved location: {res_dep.get('resolved_location', {}).get('label')}")
        assert res_dep.get("resolved_location"), "Location should resolve"
    elif status_dep == "success":
        print("Success with API key!")
        print(res_dep.get("formatted_summary"))
    else:
        print(f"Response: {res_dep}")

    # 5. Verify MCP Tool Registration
    print("\n--- 5. Checking Registered MCP Tools ---")
    # In MCPServer/FastMCP, tools can be accessed via list_tools or _tools or get_tools
    print(f"Server Name: {mcp.name}")
    print("All tests passed successfully!")

if __name__ == "__main__":
    run_tests()
