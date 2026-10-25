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

export function upstreamContentType(incoming: string | null, method: string): string {
  const normalized = incoming?.split(";")[0]?.trim().toLowerCase() ?? "";
  const hasBody = method !== "GET" && method !== "HEAD";
  if (hasBody && (normalized === "" || normalized === "text/plain")) {
    return "application/json";
  }
  return incoming ?? "application/json";
}

export function stripUpstreamHeaders(source: Headers, requestId: string): Headers {
  const headers = new Headers(source);
  for (const name of STRIP_UPSTREAM) {
    headers.delete(name);
  }
  headers.set("X-Request-Id", requestId);
  if (!headers.has("Content-Type")) {
    headers.set("Content-Type", "text/plain; charset=utf-8");
  }
  const contentType = headers.get("Content-Type") ?? "";
  if (contentType.toLowerCase().includes("json") && !contentType.toLowerCase().includes("charset")) {
    headers.set("Content-Type", "application/json; charset=utf-8");
  }
  return headers;
}
