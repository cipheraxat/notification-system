import http from 'k6/http';
import { check, sleep } from 'k6';

// This script repeatedly calls the debug allocation endpoint to
// force the JVM to consume heap memory. Use in conjunction with
// Grafana to observe heap growth under repeated allocation.

export const options = {
  vus: 20,
  duration: '1m',
  thresholds: {
    http_req_failed: ['rate<0.1'],
  },
};

export default function () {
  // allocate 5 MB per request (controlled via query param)
  const res = http.post('http://localhost:8080/api/v1/debug/alloc?mb=5');
  check(res, { 'alloc status 200': (r) => r.status === 200 });
  sleep(0.5);
}
