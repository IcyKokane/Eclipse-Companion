#include "../../app/src/main/cpp/prompt_cache_policy.h"
#include <cassert>
#include <vector>
#include <cstdio>
using V = std::vector<int>;
int main() {
    // Never reuse the final token without decoding it to obtain correct logits.
    assert(eclipse_cache::reusable_prefix(V{1, 2, 3}, V{1, 2, 3}) == 2);
    assert(eclipse_cache::reusable_prefix(V{1, 2, 3}, V{1, 2, 3, 4}) == 3);
    assert(eclipse_cache::reusable_prefix(V{1, 2, 3, 4, 5}, V{1, 2, 6, 7}) == 2);
    assert(eclipse_cache::reusable_prefix(V{1, 2, 3}, V{7, 2, 3}) == 0);
    assert(eclipse_cache::reusable_prefix(V{}, V{1, 2, 3}) == 0);
    assert(eclipse_cache::reusable_prefix(V{1, 2}, V{}) == 0);
    assert(eclipse_cache::reusable_prefix(V{1}, V{1}) == 0);
    assert(eclipse_cache::reusable_prefix(V{1, 2, 3}, V{1}) == 0);
    assert(eclipse_cache::reusable_prefix(V{1, 2, 3}, V{1, 2}) == 1);
    assert(eclipse_cache::reusable_prefix(V{1, 2, 3}, V{1, 2, 4}) == 2);
    std::puts("PASS: 10 native cache-prefix policy cases");
}
