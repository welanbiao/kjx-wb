/**
 * WP-51 并发解锁占位压测：POST ${BASE_URL}/app/works/${WORK_CODE}/unlock
 *
 * 头部：Authorization（TOKEN 环境变量，禁止写死）、X-Idempotency-Key（每次迭代唯一）。
 * WORK_CODE、TOKEN 缺省则拒绝运行。
 *
 * Gate 7 验收是「解锁成功数与额度严格一致」。本脚本只发 HTTP，不查 MySQL / Redis，
 * 不能单独作为额度一致性证据。未在本机执行。
 *
 *   k6 run -e BASE_URL=http://127.0.0.1:8080 -e WORK_CODE=<code> -e TOKEN=<app-token> tests/k6/unlock-concurrency.js
 *
 * 压测环境禁止真实短信与真实支付。
 */
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const unlockHttpOk = new Counter('unlock_http_2xx');
const unlockBusinessOk = new Counter('unlock_business_success_or_idempotent');

function requireEnv(name) {
  const raw = __ENV[name];
  if (raw === undefined || String(raw).trim() === '') {
    const msg = `${name} is required. Refusing to run.`;
    console.error(msg);
    throw new Error(msg);
  }
  return String(raw).trim();
}

function requireLoadTestBaseUrl() {
  const baseUrl = requireEnv('BASE_URL');
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

function authorizationHeader(token) {
  if (/^Bearer\s+/i.test(token)) {
    return token;
  }
  return `Bearer ${token}`;
}

function uniqueIdempotencyKey() {
  return `${__VU}-${__ITER}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

const BASE_URL = requireLoadTestBaseUrl();
const WORK_CODE = requireEnv('WORK_CODE');
const TOKEN = requireEnv('TOKEN');

export const options = {
  vus: 50,
  iterations: 200,
};

export default function unlockConcurrency() {
  const url = `${BASE_URL}/app/works/${encodeURIComponent(WORK_CODE)}/unlock`;
  const res = http.post(url, null, {
    headers: {
      Authorization: authorizationHeader(TOKEN),
      'X-Idempotency-Key': uniqueIdempotencyKey(),
      'Content-Type': 'application/json',
    },
  });

  if (res.status >= 200 && res.status < 300) {
    unlockHttpOk.add(1);
  }

  let businessOk = false;
  try {
    const body = res.json();
    businessOk = body && (body.code === 0 || body.code === 1010);
  } catch (err) {
    businessOk = false;
  }
  if (businessOk) {
    unlockBusinessOk.add(1);
  }

  check(res, {
    'unlock reached the app': (r) => r.status > 0,
  });
}
