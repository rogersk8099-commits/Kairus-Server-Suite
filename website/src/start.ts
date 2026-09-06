import { createStart, createCsrfMiddleware, createMiddleware } from "@tanstack/react-start";
import { getRequest } from "@tanstack/react-start/server";

import { renderErrorPage } from "./lib/error-page";

const errorMiddleware = createMiddleware().server(async ({ next }) => {
  try {
    return await next();
  } catch (error) {
    if (error != null && typeof error === "object" && "statusCode" in error) {
      throw error;
    }
    console.error(error);
    return new Response(renderErrorPage(), {
      status: 500,
      headers: { "content-type": "text/html; charset=utf-8" },
    });
  }
});

const exactOriginMiddleware = createMiddleware().server(async ({ next }) => {
  const request = getRequest();
  if (!["GET", "HEAD", "OPTIONS"].includes(request.method)) {
    const configured = process.env["WEBSITE_URL"];
    if (!configured) throw new Error("WEBSITE_URL is required");
    const expected = new URL(configured).origin;
    const actual = request.headers.get("origin");
    if (!actual || actual !== expected) return new Response("Forbidden", { status: 403 });
  }
  return next();
});

// Start installs this automatically when src/start.ts is absent; defining the
// file opts out, so re-add it explicitly to keep server functions protected
// from cross-site requests.
const csrfMiddleware = createCsrfMiddleware({
  filter: (ctx) => ctx.handlerType === "serverFn",
});

export const startInstance = createStart(() => ({
  requestMiddleware: [errorMiddleware, exactOriginMiddleware, csrfMiddleware],
}));
