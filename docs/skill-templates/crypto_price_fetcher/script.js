// Template skill: crypto_price_fetcher
// Uses CoinGecko public API; input.symbols must be CoinGecko coin ids (e.g. bitcoin, ethereum).

export function run(input) {
  const symbols = Array.isArray(input?.symbols) ? input.symbols : [];
  const prices = {};
  if (symbols.length === 0) {
    return { prices };
  }
  const ids = symbols.map((s) => String(s).trim().toLowerCase()).filter(Boolean);
  if (ids.length === 0) {
    return { prices };
  }
  const idsParam = ids.join(",");
  const url =
    "https://api.coingecko.com/api/v3/simple/price?ids=" +
    encodeURIComponent(idsParam) +
    "&vs_currencies=usd";
  const req = JSON.stringify({
    url,
    method: "GET",
    headers: { Accept: "application/json" },
    body: null,
  });
  const raw = __openring_fetch(req);
  const res = JSON.parse(raw);
  if (!res.ok) {
    return { prices: {}, error: res.error || "fetch_failed" };
  }
  let data;
  try {
    data = JSON.parse(res.body);
  } catch (e) {
    return { prices: {}, error: "invalid_json_response" };
  }
  for (const id of ids) {
    const row = data[id];
    prices[id] = row && typeof row.usd === "number" ? row.usd : null;
  }
  return { prices };
}
