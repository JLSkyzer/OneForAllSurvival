"""Client MCP minimal pour le plugin Blockbench (HTTP). Usage : bb.py tools | call NOM 'JSON' ..."""
import json
import sys
import urllib.request

URL = "http://localhost:3000/bb-mcp"
sid = None


def rpc(method, params=None, notify=False, _id=[0]):
    body = {"jsonrpc": "2.0", "method": method}
    if params is not None:
        body["params"] = params
    if not notify:
        _id[0] += 1
        body["id"] = _id[0]
    head = {"Content-Type": "application/json", "Accept": "application/json, text/event-stream"}
    if sid:
        head["Mcp-Session-Id"] = sid
    req = urllib.request.Request(URL, json.dumps(body).encode(), head)
    with urllib.request.urlopen(req, timeout=60) as r:
        s = r.headers.get("mcp-session-id")
        raw = r.read().decode()
    if notify:
        return s, None
    for line in raw.splitlines():
        if line.startswith("data: "):
            raw = line[6:]
    return s, json.loads(raw)


sid, _ = rpc("initialize", {"protocolVersion": "2025-03-26", "capabilities": {}, "clientInfo": {"name": "verif", "version": "1"}})
rpc("notifications/initialized", notify=True)


def tools():
    return rpc("tools/list")[1]["result"]["tools"]


def call(name, args):
    d = rpc("tools/call", {"name": name, "arguments": args})[1]
    if "error" in d:
        return "ERREUR RPC " + json.dumps(d["error"], ensure_ascii=False)
    res = d["result"]
    out = []
    for c in res.get("content", []):
        out.append(c.get("text") or f"<{c.get('type')} {c.get('mimeType','')}>")
    return ("ISERROR " if res.get("isError") else "") + "\n".join(out)


args = sys.argv[1:]
while args:
    cmd = args.pop(0)
    if cmd == "tools":
        ts = tools()
        print(len(ts), "outils")
        for t in ts:
            print(" ", t["name"], list(t.get("inputSchema", {}).get("properties", {})))
    elif cmd == "schema":
        n = args.pop(0)
        for t in tools():
            if t["name"] == n:
                print(n, "::", t.get("description"))
                print(json.dumps(t.get("inputSchema"), ensure_ascii=False))
    elif cmd == "call":
        n = args.pop(0)
        a = json.loads(args.pop(0))
        print(f"[{n}]", call(n, a)[:4000])
