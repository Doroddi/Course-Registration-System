import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';

const fixture = JSON.parse(open('/work/fixture.json'));
const vus = Number(__ENV.USERS);
const workload = __ENV.WORKLOAD;
export const options = {
  scenarios: { baseline: { executor: 'constant-vus', vus, duration: `${__ENV.SECONDS}s`, gracefulStop: '35s' } },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
};
const requests = new Counter('observed_requests');
const categories = ['ok', 'business', 'temporary', 'unexpected'];
const operations = ['enroll', 'cancel', 'courses', 'timetable'];
// Fast business rejections must not dilute the latency of successful writes.
const durations = {};
const counts = {};
for (const op of operations) for (const category of categories) {
  const key = `${op}_${category}`;
  durations[key] = new Trend(`latency_${key}`, true);
  counts[key] = new Counter(`count_${key}`);
}
const codes = {};
for (const code of ['COURSE_FULL', 'ALREADY_ENROLLED', 'SUBJECT_ALREADY_ENROLLED', 'CREDIT_LIMIT_EXCEEDED', 'SCHEDULE_CONFLICT', 'OTHER']) {
  codes[code] = new Counter(`rejection_${code}`);
}
function request(op, method, path, body, token, expected) {
  const response = http.request(method, `${__ENV.BASE_URL}${path}`, body, {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    timeout: '30s', tags: { name: op },
  });
  const category = response.status === expected ? 'ok'
    : op === 'enroll' && response.status === 409 ? 'business'
    : response.status === 503 ? 'temporary' : 'unexpected';
  const key = `${op}_${category}`;
  durations[key].add(response.timings.duration);
  counts[key].add(1);
  requests.add(1, { operation: op, status: String(response.status) });
  if (category === 'business') {
    let code;
    try { code = response.json('code'); } catch (_) { /* malformed body is counted below */ }
    (codes[code] || codes.OTHER).add(1);
  }
  return response.status;
}
export default function () {
  const token = fixture.tokens[__VU - 1];
  const writer = workload !== 'mixed' || __VU <= Math.max(1, Math.floor(vus / 5));
  if (!writer) {
    request('courses', 'GET', '/course-offerings?page=0&size=20', null, token, 200);
    request('timetable', 'GET', '/me/timetable', null, token, 200);
    return;
  }
  const id = workload === 'distributed' ? fixture.offerings[__VU - 1] : fixture.offerings[0];
  const status = request('enroll', 'POST', '/enrollments', JSON.stringify({ courseOfferingId: id }), token, 201);
  // Every successful write is cancelled to sustain turnover. No request is retried.
  // Ambiguous errors are cleaned up as well, since the server might have committed.
  if (status !== 409) request('cancel', 'DELETE', `/enrollments/${id}`, null, token, 204);
}
export function handleSummary(data) {
  return { '/work/summary.json': JSON.stringify(data, null, 2) };
}
