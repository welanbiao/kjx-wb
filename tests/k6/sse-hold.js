/**
 * WP-51 占位压测：100 个虚拟用户对 GET ${BASE_URL}/actuator/health 保持短连接。
 *
 * 不要打真实模型 SSE。现行 OpenAPI（contracts/openapi/openapi.yaml）未挂载
 * /v1/chat/completions 等流式路径（/v1/** 为 M3，本仓库契约说明「本文件不收录」）。
 * 本脚本不能当作「100 SSE 长连接、线程池/连接池不耗尽」的 Gate 7 证据。
 *
 * 未在本机执行。运行前必须显式传入非生产 BASE_URL。
 *
 *   k6 run -e BASE_URL=http://127.0.0.1:8080 tests/k6/sse-hold.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';

function requireLoadTestBaseUrl() {
  const raw = __ENV.BASE_URL;
  if (raw === undefined || String(raw).trim() === '') {
    const msg =
      'BASE_URL is required. Refusing to run. Example: k6 run -e BASE_URL=http://127.0.0.1:8080 tests/k6/sse-hold.js';
    console.error(msg);
    throw new Error(msg);
  }
  const baseUrl = String(raw).trim();
  let hostname;
  try {
    hostname = new URL(baseUrl).hostname.toLowerCase();
  } catch (err) {
    const msg = 'BASE_URL must be an absolute URL with scheme, e.g. http://127.0.0.1:8080';
    console.error(msg);
    throw new Error(msg);
  }
  if (hostname.includes('prod') || hostname.includes('production')) {
    const msg = `BASE_URL hostname '${hostname}' contains prod/production; refusing to run`;
    console.error(msg);
    throw new Error(msg);
  }
  return baseUrl.replace(/\/+$/, '');
}

const BASE_URL = requireLoadTestBaseUrl();

export const options = {
  vus: 100,
  duration: '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

export default function sseHoldHealthShortConnect() {
  const res = http.get(`${BASE_URL}/actuator/health`);
  check(res, {
    'health status is 200': (r) => r.status === 200,
  });
  sleep(1);
}
