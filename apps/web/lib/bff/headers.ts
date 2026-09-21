const STRIP_UPSTREAM = [
  "authorization",
  "connection",
  "content-encoding",
  "content-length",
  "keep-alive",
  "proxy-authenticate",
  "proxy-authorization",
  "set-cookie",
  "te",
  "trailer",
  "transfer-encoding",
  "upgrade",
  "www-authenticate",
] as const;

export function stripUpstreamHeaders(source: Headers, requestId: string): Headers {
  const headers = new Headers(source);
  for (const name of STRIP_UPSTREAM) {
    headers.delete(name);
  }
  headers.set("X-Request-Id", requestId);
  if (!headers.has("Content-Type")) {
    headers.set("Content-Type", "text/plain; charset=utf-8");
  }
  return headers;
}
