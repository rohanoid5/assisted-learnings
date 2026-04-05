# 6.3 — Backtracking Framework

## Concept

**Backtracking** is depth-first search over a decision tree. At each step you make a choice, recurse into the resulting state, then **undo the choice** ("backtrack") to explore the next option. The power is in **pruning**: if a partial choice already violates a constraint, you cut off the entire subtree below it — avoiding exponential wasted work.

---

## Deep Dive

### The Universal Framework

```java
void backtrack(State state, Result result) {
    if (isSolution(state)) {
        result.add(copy(state));  // ← always copy, not reference
        return;
    }

    for (Choice choice : getChoices(state)) {
        if (isValid(state, choice)) {        // ← pruning happens here
            makeChoice(state, choice);       // 1. Choose
            backtrack(state, result);        // 2. Explore
            undoChoice(state, choice);       // 3. Unchoose
        }
    }
}
```

The three steps **choose → explore → unchoose** are fundamental. The state must be restored after every recursive call so sibling branches see a clean slate.

---

### Decision Tree Visualization

Generate parentheses with n=2 (max 2 pairs):

```
                    ""
               /          \
          "("               ← only "(" valid at start
           │
         /   \
      "(("     "()"
       │          \
    "(()"        "()(":
       │              \
    "(())"          "()()"   ← both are solutions
```

Pruning rules:
- Can add `(` if `openCount < n`
- Can add `)` if `closeCount < openCount`

Without pruning: 2^(2n) = 16 states for n=2. With pruning: 5 (the Catalan number C₂ = 2 valid results + 3 cut branches).

---

### When to Use Backtracking

| Signal | Example |
|--------|---------|
| "find all combos/permutations/subsets" | Subsets, Permutations |
| "is there a valid arrangement?" | N-Queens, Sudoku |
| "find all valid strings" | Generate Parentheses |
| "find a path through a grid" | Word Search |
| constraint satisfaction with many variables | Crossword filler, map coloring |

---

## Code Examples

### Generate Parentheses (LC #22)

```java
public List<String> generateParenthesis(int n) {
    List<String> result = new ArrayList<>();
    backtrack(result, new StringBuilder(), 0, 0, n);
    return result;
}

private void backtrack(List<String> result, StringBuilder current,
                        int open, int close, int max) {
    if (current.length() == max * 2) {
        result.add(current.toString());
        return;
    }
    // Choose to add '('
    if (open < max) {
        current.append('(');           // choose
        backtrack(result, current, open + 1, close, max);
        current.deleteCharAt(current.length() - 1);  // unchoose
    }
    // Choose to add ')'
    if (close < open) {
        current.append(')');
        backtrack(result, current, open, close + 1, max);
        current.deleteCharAt(current.length() - 1);
    }
}
// Time: O(4^n / √n) — the nth Catalan number of results, each of length 2n
// Space: O(n) stack depth + O(4^n / √n) output
```

### Letter Combinations of a Phone Number (LC #17)

```java
private static final Map<Character, String> PHONE = Map.of(
    '2', "abc", '3', "def", '4', "ghi", '5', "jkl",
    '6', "mno", '7', "pqrs", '8', "tuv", '9', "wxyz"
);

public List<String> letterCombinations(String digits) {
    if (digits.isEmpty()) return Collections.emptyList();
    List<String> result = new ArrayList<>();
    backtrack(result, new StringBuilder(), digits, 0);
    return result;
}

private void backtrack(List<String> result, StringBuilder current,
                        String digits, int index) {
    if (index == digits.length()) {
        result.add(current.toString());
        return;
    }
    for (char letter : PHONE.get(digits.charAt(index)).toCharArray()) {
        current.append(letter);
        backtrack(result, current, digits, index + 1);
        current.deleteCharAt(current.length() - 1);
    }
}
```

---

## Try It Yourself

**Exercise:** Trace the backtracking decision tree for `generateParenthesis(2)`. At each node, show what's in `current`, the values of `open` and `close`, and whether you prune or recurse.

<details>
<summary>Solution — Full Trace</summary>

```
current="",    open=0, close=0 → can add "(" (open<2), can't add ")" (close=open)
  current="(",   open=1, close=0 → can add both
    current="((", open=2, close=0 → can't add "(", can add ")"
      current="(()", open=2, close=1 → can add ")"
        current="(())" → len=4=2*2 → ADD ✓ → backtrack
      ← unchoose ")"
    ← unchoose "("
    current="()", open=1, close=1 → can add "(", can't add ")" (close=open)
      current="()(", open=2, close=1 → can add ")"
        current="()()" → len=4 → ADD ✓ → backtrack
      ← unchoose ")"
    ← unchoose "(", unchoose ")"
  ← unchoose "("

Result: ["(())", "()()"]  — both Catalan strings for n=2 ✓
```

</details>

---

## Capstone Connection

The choose-explore-unchoose template appears verbatim in every backtracking solution in `problems/backtracking/`. When you write `NQueens.java` next, the only difference is the validity check (queen placement rules) — the framework itself is identical to this one.
