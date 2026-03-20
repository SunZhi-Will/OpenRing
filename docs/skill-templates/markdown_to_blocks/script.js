// Template skill: markdown_to_blocks
// Demonstrates structural text parsing logic offloaded from the LLM.

export function run(input) {
  const md = input?.markdown || "";
  const lines = md.split('\n');
  const blocks = [];

  let currentBlock = null;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();

    if (line === '') {
      // Empty line breaks paragraphs
      if (currentBlock && currentBlock.type === 'paragraph') {
        blocks.push(currentBlock);
        currentBlock = null;
      }
      continue;
    }

    // Header matching (e.g., "## Title")
    const headerMatch = line.match(/^(#{1,6})\s+(.*)/);
    if (headerMatch) {
      if (currentBlock) blocks.push(currentBlock);
      blocks.push({
        type: `h${headerMatch[1].length}`,
        content: headerMatch[2]
      });
      currentBlock = null;
      continue;
    }

    // List item matching (e.g., "- Item" or "* Item")
    if (line.match(/^[-*+]\s+(.*)/)) {
      if (currentBlock && currentBlock.type !== 'list') {
        blocks.push(currentBlock);
        currentBlock = null;
      }
      if (!currentBlock) {
        currentBlock = { type: 'list', content: line };
      } else {
        currentBlock.content += '\n' + line;
      }
      continue;
    }

    // Otherwise, paragraph text
    if (!currentBlock) {
      currentBlock = { type: 'paragraph', content: line };
    } else if (currentBlock.type === 'paragraph') {
      currentBlock.content += ' ' + line;
    } else {
      blocks.push(currentBlock);
      currentBlock = { type: 'paragraph', content: line };
    }
  }

  // Push the final block
  if (currentBlock) {
    blocks.push(currentBlock);
  }

  return { blocks };
}