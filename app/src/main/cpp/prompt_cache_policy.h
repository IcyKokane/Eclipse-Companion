#pragma once
#include <algorithm>
#include <cstddef>
#include <vector>

namespace eclipse_cache {
// Reuse only token-identical leading positions, and always recompute the last
// prompt token so the native sampler has fresh logits at the correct position.
// Safe even after a response was generated: callers truncate all KV entries
// starting at this position, including generated tokens from the previous turn.
template <class Token>
size_t reusable_prefix(const std::vector<Token>& previous_prompt,
                       const std::vector<Token>& current_prompt) {
    if (current_prompt.empty()) return 0;
    const size_t bound = std::min(previous_prompt.size(), current_prompt.size());
    size_t shared = 0;
    while (shared < bound && previous_prompt[shared] == current_prompt[shared]) ++shared;
    return std::min(shared, current_prompt.size() - 1);
}
}  // namespace eclipse_cache
