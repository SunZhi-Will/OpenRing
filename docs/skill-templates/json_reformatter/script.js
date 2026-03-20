// Template skill: json_reformatter

function safeStringify(value, spaces) {
  try {
    return JSON.stringify(value, null, spaces);
  } catch {
    // Fallback: stringify the value itself.
    return String(value);
  }
}

export function run(input) {
  const data = input?.data;
  const style = input?.style === "compact" ? "compact" : "pretty";

  const spaces = style === "compact" ? 0 : 2;
  const json = safeStringify(data, spaces);

  // Ensure consistent newline behavior for pretty mode.
  return { json: json + (style === "pretty" ? "\n" : "") };
}

