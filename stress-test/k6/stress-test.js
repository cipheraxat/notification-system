import http from 'k6/http';
import { check, sleep } from 'k6';

// -----------------------------------------------------
// Environment-driven configuration
// -----------------------------------------------------
// You can override these values from terminal, for example:
// BASE_URL=http://localhost:8080 TARGET_PATH=/api/v1/notifications METHOD=POST k6 run ...
const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const targetPath = __ENV.TARGET_PATH || '/';
const method = (__ENV.METHOD || 'GET').toUpperCase();

// Default JSON body used only when METHOD=POST and PAYLOAD is not provided.
// This payload matches the notification API shape for quick testing.
const defaultPayload = JSON.stringify({
  userId: '550e8400-e29b-41d4-a716-446655440001',
  channel: 'IN_APP',
  priority: 'MEDIUM',
  content: 'Load test notification content'
});

export const options = {
  scenarios: {
    // "steady_load" profile:
    // - Ramps gradually to avoid an artificial cold-start spike.
    // - Holds load long enough to observe stable latency/throughput behavior.
    steady_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        // Warm-up: ramp to 100 virtual users in 2 minutes
        { duration: '2m', target: 1000 },
        // Hold: keep 100 users for baseline metrics
        { duration: '8m', target: 10000 },
        // Step-up: increase pressure to 200 users
        { duration: '2m', target: 2000 },
        // Hold: observe sustained behavior at higher load
        { duration: '8m', target: 200 },
        // Cool-down: ramp down to zero users
        { duration: '2m', target: 0 }
      ],
      // Give in-flight requests time to finish when ramping down.
      gracefulRampDown: '30s'
    }
  },
  thresholds: {
    // <5% failures tolerated during this stress profile.
    http_req_failed: ['rate<0.05'],
    // SLO-style latency targets in milliseconds.
    // p95 under 750ms, p99 under 1200ms.
    http_req_duration: ['p(95)<750', 'p(99)<1200']
  }
};

export default function () {
  // Per-iteration request target URL.
  const url = `${baseUrl}${targetPath}`;

  // Common request headers.
  const params = {
    headers: {
      'Content-Type': 'application/json'
    }
  };

  let response;
  if (method === 'POST') {
    // If PAYLOAD env var is present, it overrides defaultPayload.
    const payload = __ENV.PAYLOAD || defaultPayload;
    response = http.post(url, payload, params);
  } else {
    // For GET, k6 uses params as request options.
    response = http.get(url, params);
  }

  // Functional check: count non-2xx/3xx as failed in this profile.
  check(response, {
    'status is success': (r) => r.status >= 200 && r.status < 400
  });

  // Think-time to emulate user pause between actions.
  // Lower value => more request pressure.
  sleep(0.2);
}
