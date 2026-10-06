// Same-origin proxy to the Figure Cloud API (avoids CORS, which only allows the workers origin).
const UPSTREAM = process.env.FIGURE_API || "https://figure-cloud.figure-softwares.workers.dev";

exports.handler = async (event) => {
  const sub = event.path.replace(/^\/(\.netlify\/functions\/proxy|api)/, "");
  const url = UPSTREAM + "/api" + sub + (event.rawQuery ? "?" + event.rawQuery : "");
  const h = event.headers || {};
  const headers = {
    "content-type": h["content-type"] || "application/json",
    accept: "*/*",
    origin: UPSTREAM,
    referer: UPSTREAM + "/",
    "user-agent": h["user-agent"] || "Mozilla/5.0",
  };
  if (h["x-fig-token"]) headers.cookie = "as_user_token=" + h["x-fig-token"];
  const hasBody = !["GET", "HEAD"].includes(event.httpMethod);
  try {
    const r = await fetch(url, {
      method: event.httpMethod,
      headers,
      body: hasBody ? (event.isBase64Encoded ? Buffer.from(event.body, "base64") : event.body) : undefined,
    });
    const text = await r.text();
    // expose upstream session cookie to the client as a header (cookies on a proxy are unreliable)
    const setCookies = r.headers.getSetCookie ? r.headers.getSetCookie() : [];
    const tok = setCookies.map((c) => /^as_user_token=([^;]+)/.exec(c)).find(Boolean);
    return {
      statusCode: r.status,
      headers: {
        "content-type": r.headers.get("content-type") || "application/json",
        ...(tok ? { "x-fig-token": tok[1] } : {}),
        "access-control-expose-headers": "x-fig-token",
      },
      body: text,
    };
  } catch (e) {
    return { statusCode: 502, body: JSON.stringify({ error: "upstream_unreachable", detail: String(e) }) };
  }
};
