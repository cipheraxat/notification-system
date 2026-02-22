import http from 'k6/http';
import { check, sleep } from 'k6';

// -----------------------------------------------------
// Environment-driven configuration
// -----------------------------------------------------
// Examples:
// 1) GET spike test:
//    k6 run stress-test/k6/spike-test.js
// 2) POST spike test:
//    BASE_URL=http://localhost:8080 TARGET_PATH=/api/v1/notifications METHOD=POST k6 run ...
const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const targetPath = __ENV.TARGET_PATH || '/';
const method = (__ENV.METHOD || 'GET').toUpperCase();

// Fallback payload for POST tests when PAYLOAD env var is not provided.
const defaultPayload = JSON.stringify({
  userId: '550e8400-e29b-41d4-a716-446655440001',
  channel: 'IN_APP',
  priority: 'HIGH',
  content: 'Spike test notification content'
});

export const options = {
  scenarios: {
    // "spikes" profile:
    // Simulates sudden bursts, then recovery, then a bigger burst.
    // Useful for testing queueing, autoscaling behavior, and latency tails.
    spikes: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        // Baseline traffic
        { duration: '1m', target: 50 },
        { duration: '1m', target: 50 },

        // Spike #1
        { duration: '30s', target: 500 },
        { duration: '2m', target: 500 },

        // Recovery after spike #1
        { duration: '30s', target: 50 },
        { duration: '1m', target: 50 },

        // Spike #2 (larger)
        { duration: '30s', target: 1000 },
        { duration: '2m', target: 1000 },

        // Partial recovery and short hold
        { duration: '30s', target: 100 },
        { duration: '1m', target: 100 },

        // End run
        { duration: '30s', target: 0 }
      ],
      // Grace period to finish outstanding requests when ramping down.
      gracefulRampDown: '20s'
    }
  },
  thresholds: {
    // More relaxed than steady test due to intentional spike behavior.
    http_req_failed: ['rate<0.1'],
    // Latency SLOs for spike scenario in milliseconds.
    http_req_duration: ['p(95)<1200', 'p(99)<2000']
  }
};

export default function () {
  // Final URL built from env vars.
  const url = `${baseUrl}${targetPath}`;

  // Shared request headers.
  const params = {
    headers: {
      'Content-Type': 'application/json'
    }
  };

  let response;
  if (method === 'POST') {
    // External PAYLOAD (if set) overrides default payload.
    const payload = __ENV.PAYLOAD || defaultPayload;
    response = http.post(url, payload, params);
  } else {
    response = http.get(url, params);
  }

  // In spike tests we allow <500 to tolerate expected 4xx in some setups,
  // while still surfacing true server failures.
  check(response, {
    'status is success': (r) => r.status >= 200 && r.status < 500
  });

  // Small think-time keeps pressure high while still resembling user pacing.
  sleep(0.1);
}
