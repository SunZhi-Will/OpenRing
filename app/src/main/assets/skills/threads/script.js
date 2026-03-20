export function run(input) {
  const text = input && input.text != null ? String(input.text) : "";
  const includeLink = input && input.includeLink === true;

  // Deterministic preview payload. Real posting/network should be implemented when QuickJS is wired.
  return {
    postText: text,
    includeLink
  };
}