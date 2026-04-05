# 2.6 — Matrix Traversal Patterns

## Concept

A 2D matrix is just a flat array accessed with two indices, but interview problems exploit its spatial structure — spirals, diagonals, and flood-fill. Once you learn to treat cells as graph nodes, every grid problem reduces to BFS/DFS you already know.

---

## Deep Dive

### Matrix as a Graph

```
Grid (4×4):
┌────┬────┬────┬────┐        Cell (r, c) has up to 4 neighbors:
│(0,0)│(0,1)│(0,2)│(0,3)│      Up:    (r-1, c)
├────┼────┼────┼────┤        Down:  (r+1, c)
│(1,0)│(1,1)│(1,2)│(1,3)│      Left:  (r, c-1)
├────┼────┼────┼────┤        Right: (r, c+1)
│(2,0)│(2,1)│(2,2)│(2,3)│
├────┼────┼────┼────┤     Direction arrays (memorize):
│(3,0)│(3,1)│(3,2)│(3,3)│     int[] dr = {-1, 1, 0, 0};
└────┴────┴────┴────┘        int[] dc = { 0, 0,-1, 1};
```

---

### Traversal Patterns

#### 1 — Row by Row (trivial)

```java
for (int r = 0; r < rows; r++)
    for (int c = 0; c < cols; c++)
        process(matrix[r][c]);
```

#### 2 — Spiral Order

```java
// Traverse matrix in spiral order: outside → inside
// [1, 2, 3]      →  [1,2,3,6,9,8,7,4,5]
// [4, 5, 6]
// [7, 8, 9]

public List<Integer> spiralOrder(int[][] matrix) {
    List<Integer> result = new ArrayList<>();
    int top = 0, bottom = matrix.length - 1;
    int left = 0, right = matrix[0].length - 1;

    while (top <= bottom && left <= right) {
        // Right along top row
        for (int c = left; c <= right; c++) result.add(matrix[top][c]);
        top++;

        // Down along right column
        for (int r = top; r <= bottom; r++) result.add(matrix[r][right]);
        right--;

        // Left along bottom row (if still valid)
        if (top <= bottom) {
            for (int c = right; c >= left; c--) result.add(matrix[bottom][c]);
            bottom--;
        }

        // Up along left column (if still valid)
        if (left <= right) {
            for (int r = bottom; r >= top; r--) result.add(matrix[r][left]);
            left++;
        }
    }
    return result;
    // Time: O(n*m), Space: O(1) auxiliary
}
```

#### 3 — Diagonal Traversal

```java
// Anti-diagonal grouping: elements where r+c = d are on the same anti-diagonal
// d ranges from 0 to (rows-1)+(cols-1)

public int[] findDiagonalOrder(int[][] mat) {
    int rows = mat.length, cols = mat[0].length;
    int[] result = new int[rows * cols];
    int idx = 0;

    for (int d = 0; d < rows + cols - 1; d++) {
        // Direction alternates: even d = bottom-left, odd d = top-right
        if (d % 2 == 0) {
            int r = Math.min(d, rows - 1), c = d - r;
            while (r >= 0 && c < cols) result[idx++] = mat[r--][c++];
        } else {
            int c = Math.min(d, cols - 1), r = d - c;
            while (c >= 0 && r < rows) result[idx++] = mat[r++][c--];
        }
    }
    return result;
}
```

#### 4 — BFS Flood Fill

```java
// Spread from a starting cell to all reachable cells (4-directional)
// "Number of Islands" pattern

public void floodFill(int[][] grid, int startR, int startC, int color) {
    int originalColor = grid[startR][startC];
    if (originalColor == color) return;

    int rows = grid.length, cols = grid[0].length;
    int[] dr = {-1, 1, 0, 0};
    int[] dc = { 0, 0,-1, 1};

    Queue<int[]> queue = new ArrayDeque<>();
    queue.offer(new int[]{startR, startC});
    grid[startR][startC] = color;  // mark visited immediately to avoid re-queuing

    while (!queue.isEmpty()) {
        int[] cell = queue.poll();
        for (int d = 0; d < 4; d++) {
            int nr = cell[0] + dr[d], nc = cell[1] + dc[d];
            if (nr >= 0 && nr < rows && nc >= 0 && nc < cols
                    && grid[nr][nc] == originalColor) {
                grid[nr][nc] = color;           // mark visited
                queue.offer(new int[]{nr, nc});
            }
        }
    }
    // Time: O(n*m), Space: O(n*m) for queue
}
```

---

## Code Examples

### Example: Rotate Image 90° Clockwise In-Place

```java
// Technique: Transpose then reverse each row
// [1,2,3]   transpose→  [1,4,7]   reverse rows→  [7,4,1]
// [4,5,6]               [2,5,8]                   [8,5,2]
// [7,8,9]               [3,6,9]                   [9,6,3]

public void rotate(int[][] matrix) {
    int n = matrix.length;

    // Step 1: Transpose (swap matrix[i][j] with matrix[j][i])
    for (int i = 0; i < n; i++)
        for (int j = i + 1; j < n; j++) {
            int tmp = matrix[i][j];
            matrix[i][j] = matrix[j][i];
            matrix[j][i] = tmp;
        }

    // Step 2: Reverse each row
    for (int i = 0; i < n; i++) {
        int left = 0, right = n - 1;
        while (left < right) {
            int tmp = matrix[i][left];
            matrix[i][left++] = matrix[i][right];
            matrix[i][right--] = tmp;
        }
    }
    // Time: O(n²), Space: O(1)
}
```

---

## Try It Yourself

**Exercise:** Count the number of islands in a binary grid. `'1'` = land, `'0'` = water. An island is surrounded by water and formed by connecting adjacent land cells (4-directionally).

```
grid = [["1","1","0","0","0"],
        ["1","1","0","0","0"],
        ["0","0","1","0","0"],
        ["0","0","0","1","1"]]
→ 3
```

```java
public int numIslands(char[][] grid) {
    // Hint: for each unvisited '1', BFS/DFS to mark entire island as visited
}
```

<details>
<summary>Show solution</summary>

```java
public int numIslands(char[][] grid) {
    if (grid == null || grid.length == 0) return 0;
    int rows = grid.length, cols = grid[0].length;
    int count = 0;

    for (int r = 0; r < rows; r++) {
        for (int c = 0; c < cols; c++) {
            if (grid[r][c] == '1') {
                count++;
                dfs(grid, r, c, rows, cols);  // sink the whole island
            }
        }
    }
    return count;
}

private void dfs(char[][] grid, int r, int c, int rows, int cols) {
    if (r < 0 || r >= rows || c < 0 || c >= cols || grid[r][c] != '1') return;
    grid[r][c] = '0';           // mark as visited (sink it)
    dfs(grid, r - 1, c, rows, cols);
    dfs(grid, r + 1, c, rows, cols);
    dfs(grid, r, c - 1, rows, cols);
    dfs(grid, r, c + 1, rows, cols);
}
// Time: O(n*m), Space: O(n*m) recursion stack in worst case
// LC #200 — one of the most important graph/grid problems
```

This pattern — iterate, find unvisited source, flood fill to mark — appears in dozens of LeetCode problems (Max Area of Island, Pacific Atlantic, Surrounded Regions).

</details>

---

## Capstone Connection

Matrix traversal underpins Module 09 (Graphs) and Module 11 (DP). The spiral and flood fill patterns appear in AlgoForge's `problems/arrays/` and `problems/graphs/`. The rotate/transpose technique is a visual puzzle that tests spatial reasoning — interviewers use it to see how you think.
