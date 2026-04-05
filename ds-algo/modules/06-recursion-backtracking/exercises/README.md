# Module 06: Recursion & Backtracking — Exercises

## Overview

These exercises apply the choose-explore-unchoose framework to increasingly complex problems. Attempt each independently before revealing the solution. By the end, every major backtracking template will be in your muscle memory.

---

## Exercise 1: Generate Parentheses (LC #22)

**Goal:** Generate all combinations of well-formed parentheses for `n` pairs.

```
n = 3  →  ["((()))","(()())","(())()","()(())","()()()"]
n = 1  →  ["()"]
```

1. Use the backtracking framework from Topic 6.3.
2. Constraints: add `(` only if `open < n`; add `)` only if `close < open`.
3. Add a JUnit 5 test that checks: n=1 → size 1, n=2 → size 2, n=3 → size 5 (the Catalan numbers C₁=1, C₂=2, C₃=5).

<details>
<summary>Solution</summary>

```java
public List<String> generateParenthesis(int n) {
    List<String> result = new ArrayList<>();
    backtrack(result, new StringBuilder(), 0, 0, n);
    return result;
}

private void backtrack(List<String> res, StringBuilder sb, int open, int close, int max) {
    if (sb.length() == max * 2) { res.add(sb.toString()); return; }
    if (open < max) {
        sb.append('(');
        backtrack(res, sb, open + 1, close, max);
        sb.deleteCharAt(sb.length() - 1);
    }
    if (close < open) {
        sb.append(')');
        backtrack(res, sb, open, close + 1, max);
        sb.deleteCharAt(sb.length() - 1);
    }
}

@Test
void testGenerateParenthesis() {
    assertEquals(1, generateParenthesis(1).size());
    assertEquals(2, generateParenthesis(2).size());
    assertEquals(5, generateParenthesis(3).size());
    assertTrue(generateParenthesis(3).containsAll(
        List.of("((()))","(()())","(())()","()(())","()()()")));
}
```

</details>

---

## Exercise 2: Subsets (LC #78)

**Goal:** Given an integer array `nums` of unique elements, return all possible subsets (the power set).

```
nums = [1, 2, 3]  →  [[], [1], [2], [1,2], [3], [1,3], [2,3], [1,2,3]]
```

1. Use the "add at every node" template (result is added before the loop, not just at leaves).
2. Verify the output has exactly `2^n` subsets.
3. Extension: adapt for **Subsets II** (LC #90) where nums may contain duplicates: `[1,2,2]` → 6 subsets.

<details>
<summary>Solution</summary>

```java
public List<List<Integer>> subsets(int[] nums) {
    List<List<Integer>> result = new ArrayList<>();
    backtrack(result, new ArrayList<>(), nums, 0);
    return result;
}

private void backtrack(List<List<Integer>> result, List<Integer> curr, int[] nums, int start) {
    result.add(new ArrayList<>(curr));  // snapshot at every node
    for (int i = start; i < nums.length; i++) {
        curr.add(nums[i]);
        backtrack(result, curr, nums, i + 1);
        curr.remove(curr.size() - 1);
    }
}

// Subsets II — with duplicates (sort first, skip same value at same level)
public List<List<Integer>> subsetsWithDup(int[] nums) {
    Arrays.sort(nums);
    List<List<Integer>> result = new ArrayList<>();
    backtrackDup(result, new ArrayList<>(), nums, 0);
    return result;
}

private void backtrackDup(List<List<Integer>> result, List<Integer> curr, int[] nums, int start) {
    result.add(new ArrayList<>(curr));
    for (int i = start; i < nums.length; i++) {
        if (i > start && nums[i] == nums[i - 1]) continue;  // skip duplicate sibling
        curr.add(nums[i]);
        backtrackDup(result, curr, nums, i + 1);
        curr.remove(curr.size() - 1);
    }
}
```

</details>

---

## Exercise 3: Permutations (LC #46 & #47)

**Goal:** Return all permutations of an array of distinct integers.

```
nums = [1, 2, 3]  →  [[1,2,3],[1,3,2],[2,1,3],[2,3,1],[3,1,2],[3,2,1]]
```

1. Use a `boolean[] used` array to track which elements are in the current permutation.
2. Base case: `current.size() == nums.length`.
3. Extension: **Permutations II** (LC #47) — input has duplicates. Add a sort + skip check: `if (i > 0 && nums[i] == nums[i-1] && !used[i-1]) continue;`

<details>
<summary>Solution</summary>

```java
public List<List<Integer>> permute(int[] nums) {
    List<List<Integer>> result = new ArrayList<>();
    backtrack(result, new ArrayList<>(), nums, new boolean[nums.length]);
    return result;
}

private void backtrack(List<List<Integer>> result, List<Integer> curr,
                        int[] nums, boolean[] used) {
    if (curr.size() == nums.length) { result.add(new ArrayList<>(curr)); return; }
    for (int i = 0; i < nums.length; i++) {
        if (used[i]) continue;
        used[i] = true;
        curr.add(nums[i]);
        backtrack(result, curr, nums, used);
        curr.remove(curr.size() - 1);
        used[i] = false;
    }
}

// Permutations II — duplicates
public List<List<Integer>> permuteUnique(int[] nums) {
    Arrays.sort(nums);
    List<List<Integer>> result = new ArrayList<>();
    backtrack2(result, new ArrayList<>(), nums, new boolean[nums.length]);
    return result;
}

private void backtrack2(List<List<Integer>> result, List<Integer> curr,
                         int[] nums, boolean[] used) {
    if (curr.size() == nums.length) { result.add(new ArrayList<>(curr)); return; }
    for (int i = 0; i < nums.length; i++) {
        if (used[i]) continue;
        if (i > 0 && nums[i] == nums[i-1] && !used[i-1]) continue;  // deduplicate
        used[i] = true; curr.add(nums[i]);
        backtrack2(result, curr, nums, used);
        curr.remove(curr.size() - 1); used[i] = false;
    }
}
```

</details>

---

## Exercise 4: N-Queens (LC #51)

**Goal:** Return all distinct solutions to placing n queens on an n×n chessboard.

```
n = 4  →  [[".Q..","...Q","Q...","..Q."],["..Q.","Q...","...Q",".Q.."]]
```

1. Use three HashSets: `cols`, `diagonals` (row-col), `antiDiagonals` (row+col).
2. Work row by row — one queen per row guaranteed.
3. For N-Queens II (LC #52): just count solutions instead of collecting boards.

<details>
<summary>Solution</summary>

```java
public List<List<String>> solveNQueens(int n) {
    List<List<String>> result = new ArrayList<>();
    backtrack(result, new int[n], new HashSet<>(), new HashSet<>(), new HashSet<>(), 0, n);
    return result;
}

private void backtrack(List<List<String>> result, int[] queens,
                        Set<Integer> cols, Set<Integer> diag, Set<Integer> antiDiag,
                        int row, int n) {
    if (row == n) { result.add(build(queens, n)); return; }
    for (int col = 0; col < n; col++) {
        int d = row - col, ad = row + col;
        if (cols.contains(col) || diag.contains(d) || antiDiag.contains(ad)) continue;
        queens[row] = col;
        cols.add(col); diag.add(d); antiDiag.add(ad);
        backtrack(result, queens, cols, diag, antiDiag, row + 1, n);
        queens[row] = -1;
        cols.remove(col); diag.remove(d); antiDiag.remove(ad);
    }
}

private List<String> build(int[] queens, int n) {
    List<String> board = new ArrayList<>();
    for (int q : queens) {
        char[] row = new char[n]; Arrays.fill(row, '.');
        row[q] = 'Q'; board.add(new String(row));
    }
    return board;
}
```

</details>

---

## Exercise 5: Word Search (LC #79)

**Goal:** Given a 2D grid of characters and a word, return true if the word exists in the grid using adjacent (up/down/left/right) cells without reusing cells.

```
board = [["A","B","C","E"],["S","F","C","S"],["A","D","E","E"]]
word = "ABCCED"  →  true
word = "ABCB"    →  false
```

1. For each starting cell that matches `word[0]`, launch a DFS.
2. Mark visited cells by temporarily replacing them with `'#'`, restore after.
3. Test the impossibility case: grid is all 'A' except the last char of word is missing.

<details>
<summary>Solution</summary>

```java
public boolean exist(char[][] board, String word) {
    for (int r = 0; r < board.length; r++)
        for (int c = 0; c < board[0].length; c++)
            if (dfs(board, word, r, c, 0)) return true;
    return false;
}

private boolean dfs(char[][] board, String word, int r, int c, int idx) {
    if (idx == word.length()) return true;
    if (r < 0 || r >= board.length || c < 0 || c >= board[0].length) return false;
    if (board[r][c] != word.charAt(idx)) return false;

    char saved = board[r][c];
    board[r][c] = '#';
    boolean found = dfs(board, word, r+1, c, idx+1) || dfs(board, word, r-1, c, idx+1)
                 || dfs(board, word, r, c+1, idx+1) || dfs(board, word, r, c-1, idx+1);
    board[r][c] = saved;
    return found;
}
```

</details>

---

## Capstone Checkpoint ✅

By completing these exercises you have:
- [x] Implemented **all major backtracking templates**: parentheses, subsets, permutations, CSP, grid DFS
- [x] Applied the **choose-explore-unchoose** framework consistently
- [x] Handled **duplicate avoidance** via sort + skip
- [x] Practiced **pruning** to eliminate invalid branches early

Add all five solutions to `capstone/algoforge/src/main/java/com/algoforge/problems/backtracking/`.
