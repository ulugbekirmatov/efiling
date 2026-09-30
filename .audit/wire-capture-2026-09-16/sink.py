import socket, ssl, sys, time, os
out_dir = sys.argv[1]
ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
ctx.load_cert_chain(os.path.join(os.path.dirname(__file__), "sink.crt"), os.path.join(os.path.dirname(__file__), "sink.key"))
srv = socket.socket(); srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1); srv.bind(("127.0.0.1", 9443)); srv.listen(5)
n = 0
while True:
    raw, _ = srv.accept()
    try:
        conn = ctx.wrap_socket(raw, server_side=True)
    except Exception as e:
        print("tls handshake failed:", e, flush=True); raw.close(); continue
    conn.settimeout(20)
    data = b""
    try:
        while True:
            chunk = conn.recv(65536)
            if not chunk: break
            data += chunk
            head, sep, body = data.partition(b"\r\n\r\n")
            if sep:
                cl = [l for l in head.split(b"\r\n") if l.lower().startswith(b"content-length:")]
                if cl and len(body) >= int(cl[0].split(b":")[1]): break
    except Exception as e:
        print("recv ended:", e, flush=True)
    n += 1
    path = os.path.join(out_dir, f"request-{n}.bin")
    open(path, "wb").write(data)
    print(f"captured {len(data)} bytes -> {path}", flush=True)
    try:
        conn.sendall(b"HTTP/1.1 500 Sink\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
    except Exception: pass
    conn.close()
