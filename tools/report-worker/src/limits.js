/**
 * Per-client token bucket. It backs the Workers Rate Limiting binding: the binding can only count windows of 10 or 60
 * seconds, so the longer window (a few uploads per 10 minutes) is kept here, and the bucket takes over completely when the
 * binding is not configured or fails. It lives in one isolate and is therefore approximate: separate isolates keep
 * separate buckets. The daily counter in KV (handler.js) is the hard cap.
 */
export function createBucket({ capacity, refillPerSecond, now, maxClients = 2000 }) {
  const clients = new Map(); // key -> { tokens, time }
  return function take(key) {
    const current = now();
    const entry = clients.get(key) ?? { tokens: capacity, time: current };
    entry.tokens = Math.min(capacity, entry.tokens + ((current - entry.time) / 1000) * refillPerSecond);
    entry.time = current;
    clients.delete(key);
    if (clients.size >= maxClients) clients.delete(clients.keys().next().value);
    clients.set(key, entry);
    if (entry.tokens < 1) return false;
    entry.tokens -= 1;
    return true;
  };
}
