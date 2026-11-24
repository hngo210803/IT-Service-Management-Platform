"""Run against a local ITSM backend. This script creates one demo ticket."""
import argparse
import json
from urllib.error import HTTPError
from urllib.request import Request, urlopen

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--base-url", default="http://127.0.0.1:8080")
parser.add_argument("--mcp-url", help="Full MCP endpoint when it is served separately")
parser.add_argument("--assistant", action="store_true", help="Test both Ollama flows")
args = parser.parse_args()
base_url = args.base_url.rstrip("/")


def request(method, path, payload=None, expected=200, headers=None, *, url=None):
    options = {"Content-Type": "application/json", **(headers or {})}
    data = json.dumps(payload).encode() if payload is not None else None
    req = Request(url or base_url + path, data=data, method=method, headers=options)
    try:
        response = urlopen(req, timeout=440)
    except HTTPError as error:
        response = error
    with response:
        body = response.read()
        assert response.status == expected, (method, path, response.status, body)
    return json.loads(body) if body else None


def mcp(method, params, request_id=1, expected=200):
    payload = {"jsonrpc": "2.0", "method": method, "params": params}
    if request_id is not None:
        payload["id"] = request_id
    response = request("POST", "/mcp", payload, expected,
                       {"Accept": "application/json, text/event-stream"}, url=args.mcp_url)
    if response is not None:
        assert response["id"] == request_id
        return response["result"]
    return None


request("POST", "/api/tickets", {"title": " ", "description": "Test"}, expected=400)
created = request("POST", "/api/tickets", {
    "title": "Test de connexion au VPN",
    "description": "Ticket de démonstration créé par scripts/smoke.py.",
}, expected=201)
ticket_id = created["id"]
assert created["status"] == "OPEN"
assert request("GET", f"/api/tickets/{ticket_id}")["title"] == created["title"]
assert any(ticket["id"] == ticket_id for ticket in request("GET", "/api/tickets"))
assert request("PATCH", f"/api/tickets/{ticket_id}/status",
               {"status": "IN_PROGRESS"})["status"] == "IN_PROGRESS"
assert request("GET", f"/api/tickets/{ticket_id}")["status"] == "IN_PROGRESS"
request("GET", "/api/tickets/9223372036854775807", expected=404)
print(f"REST OK (ticket #{ticket_id})")

assert mcp("initialize", {
    "protocolVersion": "2025-03-26",
    "capabilities": {},
    "clientInfo": {"name": "itsm-smoke", "version": "1.0"},
})["protocolVersion"] == "2025-03-26"
mcp("notifications/initialized", {}, None, 202)
assert mcp("tools/list", {})["tools"][0]["name"] == "get_ticket_status"
result = mcp("tools/call", {"name": "get_ticket_status", "arguments": {"id": ticket_id}})
assert result["isError"] is False
assert "IN_PROGRESS" in result["content"][0]["text"]
missing = mcp("tools/call", {
    "name": "get_ticket_status", "arguments": {"id": 9223372036854775807},
})
assert missing["isError"] is True
print("MCP OK")

if args.assistant:
    ticket_answer = request("POST", "/api/assistant", {
        "question": f"Quel est le statut actuel du ticket #{ticket_id} ?",
    })
    assert "MCP · statut du ticket" in ticket_answer["tools"], ticket_answer
    assert ticket_answer["answer"] == f"Ticket #{ticket_id} | statut : IN_PROGRESS", ticket_answer

    rag = request("POST", "/api/assistant", {"question": "Comment me connecter au VPN ?"})
    assert "RAG · documentation" in rag["tools"], rag
    assert len(rag["answer"]) <= 390, rag
    assert any(source["file"] == "vpn.md" and f'[{source["id"]}]' in rag["answer"]
               for source in rag["sources"]), rag
    print("Assistant MCP + RAG OK")
