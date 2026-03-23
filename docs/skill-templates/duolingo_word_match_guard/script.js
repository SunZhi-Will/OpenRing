function normalizeToken(raw) {
  var text = raw == null ? "" : String(raw);
  try {
    text = text.normalize("NFKC");
  } catch (e) {
    // Some runtimes may not support normalize; keep original string.
  }
  return text
    .toLowerCase()
    .replace(/[\u200B-\u200D\uFEFF]/g, "")
    .replace(/[_.,!?;:()[\]{}"'`~|\\/+-]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function splitCandidateParts(label) {
  var base = label == null ? "" : String(label);
  var parts = [base];
  var chunks = base.split(/\n|\/|·|•|：|:|,|;/);
  for (var i = 0; i < chunks.length; i++) {
    var p = chunks[i].trim();
    if (p) parts.push(p);
  }
  return parts;
}

function run(input) {
  var target = input && input.target != null ? String(input.target) : "";
  var choices = input && Array.isArray(input.choices) ? input.choices : [];
  var allowContainsFallback = !!(input && input.allowContainsFallback);

  if (!target.trim()) {
    return {
      status: "invalid_argument",
      selected: "",
      confidence: 0,
      reason: "target is required",
      matchedIndices: []
    };
  }
  if (!choices.length) {
    return {
      status: "invalid_argument",
      selected: "",
      confidence: 0,
      reason: "choices is required and must be a non-empty array",
      matchedIndices: []
    };
  }

  var targetNorm = normalizeToken(target);
  var exactHits = [];
  var containsHits = [];

  for (var i = 0; i < choices.length; i++) {
    var label = choices[i] == null ? "" : String(choices[i]);
    var parts = splitCandidateParts(label);
    var exact = false;
    var contains = false;
    for (var j = 0; j < parts.length; j++) {
      var norm = normalizeToken(parts[j]);
      if (!norm) continue;
      if (norm === targetNorm) exact = true;
      if (norm.indexOf(targetNorm) >= 0 || targetNorm.indexOf(norm) >= 0) contains = true;
    }
    if (exact) exactHits.push(i);
    if (contains) containsHits.push(i);
  }

  if (exactHits.length === 1) {
    var idx = exactHits[0];
    return {
      status: "ok",
      selected: String(choices[idx]),
      confidence: 1,
      reason: "unique exact normalized match",
      matchedIndices: [idx]
    };
  }

  if (exactHits.length > 1) {
    return {
      status: "ambiguous",
      selected: "",
      confidence: 0,
      reason: "multiple exact matches; do not click blindly",
      matchedIndices: exactHits
    };
  }

  if (allowContainsFallback && containsHits.length === 1) {
    var cidx = containsHits[0];
    return {
      status: "ok",
      selected: String(choices[cidx]),
      confidence: 0.55,
      reason: "unique contains fallback match",
      matchedIndices: [cidx]
    };
  }

  if (allowContainsFallback && containsHits.length > 1) {
    return {
      status: "ambiguous",
      selected: "",
      confidence: 0,
      reason: "contains fallback produced multiple matches",
      matchedIndices: containsHits
    };
  }

  return {
    status: "not_found",
    selected: "",
    confidence: 0,
    reason: "no deterministic match found",
    matchedIndices: []
  };
}
