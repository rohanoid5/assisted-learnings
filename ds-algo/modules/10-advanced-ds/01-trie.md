# 10.1 — Trie (Prefix Tree)

## What Is a Trie?

A **Trie** (pronounced "try") is a tree structure where each node represents a **character prefix**. Every path from root to a marked node represents a word.

```
Words: ["cat", "car", "card", "care", "dog"]

Trie:
    root
    ├── c
    │   └── a
    │       ├── t  [END]       → "cat"
    │       └── r  [END]       → "car"
    │           ├── d  [END]   → "card"
    │           └── e  [END]   → "care"
    └── d
        └── o
            └── g  [END]       → "dog"
```

Each node stores:
1. A map of children (character → child node)
2. A boolean `isEnd` marking complete words

---

## Implementation

```java
public class Trie {
    private static class TrieNode {
        TrieNode[] children = new TrieNode[26]; // for lowercase a-z
        boolean isEnd = false;
    }

    private final TrieNode root = new TrieNode();

    // Insert a word — O(m) where m = word length
    public void insert(String word) {
        TrieNode curr = root;
        for (char c : word.toCharArray()) {
            int idx = c - 'a';
            if (curr.children[idx] == null) {
                curr.children[idx] = new TrieNode();
            }
            curr = curr.children[idx];
        }
        curr.isEnd = true;
    }

    // Search for exact word — O(m)
    public boolean search(String word) {
        TrieNode node = findNode(word);
        return node != null && node.isEnd;
    }

    // Check if any word starts with prefix — O(m)
    public boolean startsWith(String prefix) {
        return findNode(prefix) != null;
    }

    private TrieNode findNode(String prefix) {
        TrieNode curr = root;
        for (char c : prefix.toCharArray()) {
            int idx = c - 'a';
            if (curr.children[idx] == null) return null;
            curr = curr.children[idx];
        }
        return curr;
    }
}
```

**Complexity:**
- Insert: O(m) time, O(m) space per new word
- Search/StartsWith: O(m) time
- Total space: O(alphabet_size × total_chars) — up to O(26n) for n words

---

## Trie vs HashMap for Words

| | HashMap | Trie |
|--|---------|------|
| Exact word lookup | O(m) avg | O(m) |
| Prefix search | O(n×m) — check all | O(m) |
| Sorted order | No | Yes (DFS) |
| Memory | Low (hash) | Higher (nodes) |
| Use case | Exact lookups | Prefix, autocomplete |

---

## Word Search II (LC #212)

Find all words from a word list that exist in a 2D board (adjacently connected letters).

Naive: run word search for each word individually → O(words × 4^L × board). 

With Trie: build trie from words, then DFS the board once — prune when no prefix matches.

```java
public List<String> findWords(char[][] board, String[] words) {
    Trie trie = new Trie();
    for (String w : words) trie.insert(w);

    int rows = board.length, cols = board[0].length;
    Set<String> result = new HashSet<>();

    for (int r = 0; r < rows; r++) {
        for (int c = 0; c < cols; c++) {
            dfs(board, r, c, trie.root, new StringBuilder(), result, rows, cols);
        }
    }
    return new ArrayList<>(result);
}

private void dfs(char[][] board, int r, int c, Trie.TrieNode node,
                  StringBuilder path, Set<String> result, int rows, int cols) {
    if (r<0||r>=rows||c<0||c>=cols||board[r][c]=='#') return;

    char ch = board[r][c];
    Trie.TrieNode next = node.children[ch - 'a'];
    if (next == null) return; // no word with this prefix → prune

    path.append(ch);
    board[r][c] = '#'; // mark visited

    if (next.isEnd) result.add(path.toString());

    dfs(board, r-1, c, next, path, result, rows, cols);
    dfs(board, r+1, c, next, path, result, rows, cols);
    dfs(board, r, c-1, next, path, result, rows, cols);
    dfs(board, r, c+1, next, path, result, rows, cols);

    board[r][c] = ch; // restore
    path.deleteCharAt(path.length() - 1);
}
```

---

## Design Add and Search Words (LC #211)

Supports `.` as wildcard matching any single character:

```java
public class WordDictionary {
    private static class Node {
        Node[] ch = new Node[26];
        boolean isEnd;
    }

    private final Node root = new Node();

    public void addWord(String word) {
        Node curr = root;
        for (char c : word.toCharArray()) {
            int i = c - 'a';
            if (curr.ch[i] == null) curr.ch[i] = new Node();
            curr = curr.ch[i];
        }
        curr.isEnd = true;
    }

    public boolean search(String word) {
        return dfsSearch(word, 0, root);
    }

    private boolean dfsSearch(String word, int idx, Node node) {
        if (idx == word.length()) return node.isEnd;
        char c = word.charAt(idx);

        if (c == '.') {
            // Try all 26 children
            for (Node child : node.ch) {
                if (child != null && dfsSearch(word, idx + 1, child)) return true;
            }
            return false;
        } else {
            Node child = node.ch[c - 'a'];
            return child != null && dfsSearch(word, idx + 1, child);
        }
    }
}
```

---

## Autocomplete System (LC #642)

Design a search autocomplete that returns top 3 historical searches by frequency matching the current prefix. Advanced design problem — see exercises.

---

## Try It Yourself

**Problem:** Implement a Trie with `insert`, `search`, and `startsWith` methods. (LC #208)

<details>
<summary>Solution</summary>

Full implementation is shown above. Key points to verify:
1. `insert("apple")` then `search("apple")` → true
2. `search("app")` → false (only prefix exists)
3. `startsWith("app")` → true
4. `insert("app")` then `search("app")` → true

```java
Trie trie = new Trie();
trie.insert("apple");
trie.search("apple");    // true
trie.search("app");      // false
trie.startsWith("app");  // true
trie.insert("app");
trie.search("app");      // true
```

</details>

---

## Capstone Connection

Add `datastructures/advanced/Trie.java` to AlgoForge with:
- `insert(String word)`
- `search(String word)` — exact match
- `startsWith(String prefix)` — prefix match
- `autocomplete(String prefix)` — returns all words with that prefix (bonus)
- `delete(String word)` — bonus: remove a word from the trie
