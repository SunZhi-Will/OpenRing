// Template skill: html_metadata_extractor
// Demonstrates how to do complex string parsing in a pure QuickJS environment.
// Since there is no DOMParser available, we use Regex.

export function run(input) {
  const html = input?.htmlString || "";

  // 1. Extract <title>
  const titleMatch = html.match(/<title[^>]*>([\s\S]*?)<\/title>/i);
  let title = titleMatch ? titleMatch[1].trim() : "";

  // 2. Extract <meta name="description" content="...">
  let description = "";
  const descMatch = html.match(/<meta[^>]*name=["']description["'][^>]*content=["']([^"']*)["'][^>]*>/i) ||
                    html.match(/<meta[^>]*content=["']([^"']*)["'][^>]*name=["']description["'][^>]*>/i);
  if (descMatch) {
    description = descMatch[1].trim();
  }

  // 3. Extract <meta property="og:image" content="...">
  let image = "";
  const ogImageMatch = html.match(/<meta[^>]*property=["']og:image["'][^>]*content=["']([^"']*)["'][^>]*>/i) ||
                       html.match(/<meta[^>]*content=["']([^"']*)["'][^>]*property=["']og:image["'][^>]*>/i);
  if (ogImageMatch) {
    image = ogImageMatch[1].trim();
  }

  return {
    title,
    description,
    image
  };
}