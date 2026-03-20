// Template skill: crypto_price_fetcher
// Note: The QuickJS runtime and network permission enforcement are currently roadmap items.
// Keep this template deterministic so host integration can validate shapes before real networking.

export function run(input) {
  const symbols = Array.isArray(input?.symbols) ? input.symbols : [];
  const prices = {};

  for (const s of symbols) {
    const symbol = String(s);
    if (!symbol) continue;

    // Deterministic placeholder value.
    // Replace this block with real networking once the host provides fetch/HTTP.
    prices[symbol] = 0;
  }

  return { prices };
}

