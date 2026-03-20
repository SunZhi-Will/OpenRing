// Template skill: text_uppercase

export function run(input) {
  const text = input?.text == null ? "" : String(input.text);
  return { text: text.toUpperCase() };
}

