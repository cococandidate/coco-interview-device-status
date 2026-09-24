const DEFAULT_TIMEOUT_MS = 3000;

export interface HttpResponse<T = unknown> {
  status: number;
  headers: Headers;
  body: T | undefined;
}

export async function get<T = unknown>(url: string, timeoutMs = DEFAULT_TIMEOUT_MS): Promise<HttpResponse<T>> {
  return await call<T>("GET", url, undefined, timeoutMs);
}

export async function put<T = unknown>(url: string, body: unknown, timeoutMs = DEFAULT_TIMEOUT_MS): Promise<HttpResponse<T>> {
  return await call<T>("PUT", url, body, timeoutMs);
}

export async function post<T = unknown>(url: string, body: unknown, timeoutMs = DEFAULT_TIMEOUT_MS): Promise<HttpResponse<T>> {
  return await call<T>("POST", url, body, timeoutMs);
}

async function call<T>(method: string, url: string, body: unknown, timeoutMs: number): Promise<HttpResponse<T>> {
  const res = await fetch(url, {
    method,
    headers: body === undefined ? {} : { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(timeoutMs),
  });

  const text = await res.text();
  return { status: res.status, headers: res.headers, body: parseJson<T>(text) };
}

// A non-JSON body (an HTML error page from a proxy, say) should not turn a
// retryable 503 into a thrown parse error.
function parseJson<T>(text: string): T | undefined {
  if (!text) return undefined;
  try {
    return JSON.parse(text) as T;
  } catch {
    return undefined;
  }
}
