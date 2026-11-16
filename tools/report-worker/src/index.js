import { createHandler } from "./handler.js";

// One handler per isolate: the in-isolate rate-limit buckets live in its closure.
let handler;

export default {
  fetch(request, env, ctx) {
    handler ??= createHandler();
    return handler(request, env, ctx);
  },
};
