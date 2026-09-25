import http from 'k6/http';
import { Counter } from 'k6/metrics';
const fixture = JSON.parse(open('/work/fixture.json'));
export const options = {
  scenarios: { capacity: { executor: 'per-vu-iterations', vus: 200, iterations: 1, maxDuration: '60s' } },
  thresholds: { accepted: ['count==30'], full: ['count==170'], unexpected: ['count==0'] },
};
const accepted = new Counter('accepted');
const full = new Counter('full');
const unexpected = new Counter('unexpected');
export default function () {
  const response = http.post(`${__ENV.BASE_URL}/enrollments`, JSON.stringify({courseOfferingId: fixture.offerings[0]}), {
    headers: {Authorization: `Bearer ${fixture.tokens[__VU-1]}`, 'Content-Type': 'application/json'}, timeout: '30s',
  });
  // Initialize all counters even when a category has no samples.
  accepted.add(0); full.add(0); unexpected.add(0);
  if (response.status === 201) accepted.add(1);
  else if (response.status === 409 && response.json('code') === 'COURSE_FULL') full.add(1);
  else unexpected.add(1);
}
export function handleSummary(data) { return {'/work/capacity-summary.json': JSON.stringify(data,null,2)}; }
