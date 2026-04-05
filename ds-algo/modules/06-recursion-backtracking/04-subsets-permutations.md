# 6.4 — Subsets, Permutations & Combinations

## Concept

Three closely related problems form the backbone of backtracking interviews: **subsets** (which elements to include), **permutations** (what order to arrange them), and **combinations** (choose k from n without regard to order). Each has a distinct decision tree shape, and recognizing which shape your problem maps to tells you the solution structure immediately.

---

## Deep Dive

### The Three Decision Trees

```
Input: [1, 2, 3]

SUBSETS (2^n = 8 results):           PERMUTATIONS (n! = 6 results):
At each step, include or skip         At each step, pick any unused element

         []                                    []
       /     \                          /       |       \
     [1]      []                      [1]      [2]      [3]
    /   \    /  \                   /   \    /   \    /   \
  [1,2] [1] [2]  []              [1,2][1,3][2,1][2,3][3,1][3,2]
  / \   ...  ...   ...          [1,2,3][1,3,2] ... all 6 arrangements

COMBINATIONS (choose k=2 from [1,2,3] → C(3,2) = 3):
Pick elements in increasing index order to avoid duplicates ([1,2] = [2,1])

          []
        /  |  \
     [1]  [2]  [3]        ← choose first element (must be ≤ max allowed start)
    /  \    \
  [1,2][1,3] [2,3]        ← add second, only from indices > first
```

---

### Key Difference: Start Index vs Used Set

```
Subsets/Combinations:  track START INDEX  (avoid re-using earlier elements)
  backtrack(start, current)  →  loop from 'start' to end

Permutations:          track USED SET  (any element can appear, but only once)
  backtrack(used, current)   →  loop from 0 to end, skip if used[i]
```

---

## Code Examples

### Subsets (LC #78) — no duplicates in input

```java
public List<List<Integer>> subsets(int[] nums) {
    List<List<Integer>> result = new ArrayList<>();
    backtrack(result, new ArrayList<>(), nums, 0);
    return result;
}

private void backtrack(List<List<Integer>> result, List<Integer> current,
                        int[] nums, int start) {
    result.add(new ArrayList<>(current));  // add at EVERY node (not just leaves)
    for (int i = start; i < nums.length; i++) {
        current.add(nums[i]);
        backtrack(result, current, nums, i + 1);  // i+1 prevents reuse
        current.remove(current.size() - 1);
    }
}
// Time: O(n * 2^n) — 2^n subsets, each up to n long to copy
```

### Subsets II (LC #90) — input has duplicates

```java
public List<List<Integer>> subsetsWithDup(int[] nums) {
    Arrays.sort(nums);  // sort first so duplicates are adjacent
    List<List<Integer>> result = new ArrayList<>();
    backtrack(result, new ArrayList<>(), nums, 0);
    return result;
}

private void backtrack(List<List<Integer>> result, List<Integer> current,
                        int[] nums, int start) {
    result.add(new ArrayList<>(current));
    for (int i = start; i < nums.length; i++) {
        // Skip duplicate: same value as previous sibling at same level
        if (i > start && nums[i] == nums[i - 1]) continue;
        current.add(nums[i]);
        backtrack(result, current, nums, i + 1);
        current.remove(current.size() - 1);
    }
}
```

### Permutations (LC #46) — no duplicates in input

```java
public List<List<Integer>> permute(int[] nums) {
    List<List<Integer>> result = new ArrayList<>();
    boolean[] used = new boolean[nums.length];
    backtrack(result, new ArrayList<>(), nums, used);
    return result;
}

private void backtrack(List<List<Integer>> result, List<Integer> current,
                        int[] nums, boolean[] used) {
    if (current.size() == nums.length) {
        result.add(new ArrayList<>(current));
        return;
    }
    for (int i = 0; i < nums.length; i++) {
        if (used[i]) continue;
        used[i] = true;
        current.add(nums[i]);
        backtrack(result, current, nums, used);
        current.remove(current.size() - 1);
        used[i] = false;
    }
}
// Time: O(n * n!) — n! permutations, each n long to copy
```

### Combinations (LC #77) — choose k from [1..n]

```java
public List<List<Integer>> combine(int n, int k) {
    List<List<Integer>> result = new ArrayList<>();
    backtrack(result, new ArrayList<>(), n, k, 1);
    return result;
}

private void backtrack(List<List<Integer>> result, List<Integer> current,
                        int n, int k, int start) {
    if (current.size() == k) {
        result.add(new ArrayList<>(current));
        return;
    }
    // Optimization: only iterate up to n-(k-current.size())+1
    // (no point starting at a position where not enough elements remain)
    int remaining = k - current.size();
    for (int i = start; i <= n - remaining + 1; i++) {
        current.add(i);
        backtrack(result, current, n, k, i + 1);
        current.remove(current.size() - 1);
    }
}
```

---

## Try It Yourself

**Exercise:** Implement **Combination Sum** (LC #39): given an array of distinct integers and a target, find all unique combinations that sum to target. The same number can be used multiple times.

```java
// Input: candidates = [2,3,6,7], target = 7
// Output: [[2,2,3], [7]]
public List<List<Integer>> combinationSum(int[] candidates, int target) {
    // your code here
}
```

<details>
<summary>Solution</summary>

```java
public List<List<Integer>> combinationSum(int[] candidates, int target) {
    Arrays.sort(candidates);  // allows early termination
    List<List<Integer>> result = new ArrayList<>();
    backtrack(result, new ArrayList<>(), candidates, target, 0);
    return result;
}

private void backtrack(List<List<Integer>> result, List<Integer> current,
                        int[] candidates, int remaining, int start) {
    if (remaining == 0) {
        result.add(new ArrayList<>(current));
        return;
    }
    for (int i = start; i < candidates.length; i++) {
        if (candidates[i] > remaining) break;  // pruning: sorted, so further elements also too big
        current.add(candidates[i]);
        backtrack(result, current, candidates, remaining - candidates[i], i);  // i not i+1 (reuse allowed)
        current.remove(current.size() - 1);
    }
}
```

</details>

---

## Capstone Connection

`Subsets.java`, `Permutations.java`, and `CombinationSum.java` in `problems/backtracking/` each follow one of the three templates above — only the loop bounds and validity checks differ. Understanding these three templates lets you solve the entire "combinations/permutations" LC tag on autopilot.
