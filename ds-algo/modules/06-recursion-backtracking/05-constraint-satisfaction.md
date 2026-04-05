# 6.5 — Constraint Satisfaction: N-Queens & Sudoku

## Concept

**Constraint satisfaction problems (CSPs)** have variables, domains, and constraints. Backtracking solves them by assigning values one variable at a time and pruning any assignment that violates a constraint. N-Queens and Sudoku Solver are the two canonical CSP interview problems — both appear frequently in FAANG loops.

---

## Deep Dive

### N-Queens: State & Constraints

Place n queens on an n×n board so no two queens attack each other.

```
4-Queens solution (n=4):

. Q . .
. . . Q
Q . . .
. . Q .

Constraints for queen at (row, col):
  1. No other queen in the same column → cols set
  2. No other queen on the same diagonal (row-col = constant) → diag set
  3. No other queen on the same anti-diagonal (row+col = constant) → antiDiag set
  Note: rows are handled automatically — we place exactly one queen per row.
```

### Column & Diagonal Tracking

```
For a 4×4 board, placing queen at (1, 3):
  cols.add(3)          → column 3 is occupied
  diag.add(1-3 = -2)  → diagonal -2 is occupied
  antiDiag.add(1+3=4) → anti-diagonal 4 is occupied

Checking (2, 1): cols={3}, diag={-2}, antiDiag={4}
  col 1: not in cols ✓
  2-1=1: not in diag ✓
  2+1=3: not in antiDiag ✓  → valid placement
```

---

### Sudoku: State & Constraints

Fill a 9×9 grid so each row, column, and 3×3 box contains digits 1-9 exactly once.

```
Box index formula:  (row / 3) * 3 + (col / 3)

Grid visualized with box indices:
  0 0 0 | 1 1 1 | 2 2 2
  0 0 0 | 1 1 1 | 2 2 2
  0 0 0 | 1 1 1 | 2 2 2
  ──────+───────+──────
  3 3 3 | 4 4 4 | 5 5 5
  ...
```

---

## Code Examples

### N-Queens (LC #51)

```java
public List<List<String>> solveNQueens(int n) {
    List<List<String>> result = new ArrayList<>();
    int[] queens = new int[n];  // queens[row] = column where queen is placed
    Arrays.fill(queens, -1);

    Set<Integer> cols = new HashSet<>();
    Set<Integer> diag = new HashSet<>();
    Set<Integer> antiDiag = new HashSet<>();

    backtrack(0, n, queens, cols, diag, antiDiag, result);
    return result;
}

private void backtrack(int row, int n, int[] queens,
                        Set<Integer> cols, Set<Integer> diag, Set<Integer> antiDiag,
                        List<List<String>> result) {
    if (row == n) {
        result.add(buildBoard(queens, n));
        return;
    }
    for (int col = 0; col < n; col++) {
        if (cols.contains(col)) continue;
        int d = row - col, ad = row + col;
        if (diag.contains(d) || antiDiag.contains(ad)) continue;

        queens[row] = col;
        cols.add(col); diag.add(d); antiDiag.add(ad);

        backtrack(row + 1, n, queens, cols, diag, antiDiag, result);

        queens[row] = -1;
        cols.remove(col); diag.remove(d); antiDiag.remove(ad);
    }
}

private List<String> buildBoard(int[] queens, int n) {
    List<String> board = new ArrayList<>();
    for (int row = 0; row < n; row++) {
        char[] line = new char[n];
        Arrays.fill(line, '.');
        line[queens[row]] = 'Q';
        board.add(new String(line));
    }
    return board;
}
```

### Sudoku Solver (LC #37)

```java
public void solveSudoku(char[][] board) {
    solve(board);
}

private boolean solve(char[][] board) {
    for (int row = 0; row < 9; row++) {
        for (int col = 0; col < 9; col++) {
            if (board[row][col] != '.') continue;  // already filled
            for (char c = '1'; c <= '9'; c++) {
                if (isValid(board, row, col, c)) {
                    board[row][col] = c;              // choose
                    if (solve(board)) return true;    // explore
                    board[row][col] = '.';            // unchoose
                }
            }
            return false;  // no digit worked → backtrack to caller
        }
    }
    return true;  // all cells filled → solution found
}

private boolean isValid(char[][] board, int row, int col, char c) {
    int box = (row / 3) * 3 + col / 3;
    for (int i = 0; i < 9; i++) {
        if (board[row][i] == c) return false;           // same row
        if (board[i][col] == c) return false;           // same column
        if (board[(box/3)*3 + i/3][(box%3)*3 + i%3] == c) return false;  // same box
    }
    return true;
}
```

---

## Try It Yourself

**Exercise:** Implement **Word Search** (LC #79) — given a 2D grid of characters and a word, return true if the word can be found by following adjacent cells (horizontally or vertically, no cell used twice).

```java
// Input: board = [["A","B","C","E"],
//                 ["S","F","C","S"],
//                 ["A","D","E","E"]]
// word = "ABCCED" → true
// word = "SEE"    → true
// word = "ABCB"   → false  (can't reuse 'B')

public boolean exist(char[][] board, String word) {
    // Hint: for each cell that matches word[0], start a DFS
    // Mark cells visited with a sentinel (e.g., replace with '#'), restore after
}
```

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
    if (idx == word.length()) return true;  // all chars matched
    if (r < 0 || r >= board.length || c < 0 || c >= board[0].length) return false;
    if (board[r][c] != word.charAt(idx)) return false;

    char tmp = board[r][c];
    board[r][c] = '#';  // choose: mark visited

    boolean found = dfs(board, word, r+1, c, idx+1)
                 || dfs(board, word, r-1, c, idx+1)
                 || dfs(board, word, r, c+1, idx+1)
                 || dfs(board, word, r, c-1, idx+1);

    board[r][c] = tmp;  // unchoose: restore
    return found;
    // Time: O(m*n*4^L) where L=word length.  Space: O(L) stack depth
}
```

</details>

---

## Capstone Connection

`NQueens.java` and `WordSearch.java` are two of the most impressive problems in AlgoForge's `problems/backtracking/` collection. When reviewing AlgoForge before an interview, these two are the best to re-read — they demonstrate both the purity of the framework (N-Queens) and the grid-DFS variant (Word Search) that appears across graph problems in Module 09.
