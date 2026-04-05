# 1.6 — Big-Θ and Big-Ω Notation

## Concept

Big-O gives you the *upper bound* (worst case). But two more notations complete the picture: **Big-Ω** (lower bound, best case floor) and **Big-Θ** (tight bound, exact growth). Interviewers rarely ask you to prove these formally, but knowing the distinctions makes you sound precise.

---

## Deep Dive

### The Three Notations Side-by-Side

```
f(n) = algorithm's actual runtime as a function of n

Big-O (O):     upper bound      f(n) ≤ c · g(n)   "at most"
Big-Ω (Ω):    lower bound      f(n) ≥ c · g(n)   "at least"
Big-Θ (Θ):    tight bound      c₁·g(n) ≤ f(n) ≤ c₂·g(n)   "exactly"

Visually:

        │         Θ(n²)
        │        /
  f(n)  │   ----+---- actual f(n)  (between c₁n² and c₂n²)
        │       |
        │   Ω(n²) = floor
        └──────────────────────▶ n
```

Θ(g(n)) = the algorithm is *both* O(g(n)) AND Ω(g(n)).

---

### When They Differ: Binary Search

Binary search on a sorted array:

```java
public int binarySearch(int[] arr, int target) {
    int lo = 0, hi = arr.length - 1;
    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;
        if (arr[mid] == target) return mid;    // ← best case exits here
        if (arr[mid] < target) lo = mid + 1;
        else hi = mid - 1;
    }
    return -1;
}
```

| Case | Scenario | Bound |
|------|----------|-------|
| Best | Target is the first mid element | Ω(1) — at least 1 step |
| Worst | Target not in array, fully exhaust | O(log n) — at most log n steps |
| Average | Tight on log n growth | Θ(log n) |

So when someone says "binary search is O(log n)" they mean the *upper bound*. The tight bound Θ(log n) applies to the general/average case.

---

### When They Differ: Linear Search

```java
public int linearSearch(int[] arr, int target) {
    for (int i = 0; i < arr.length; i++) {
        if (arr[i] == target) return i;  // might exit early!
    }
    return -1;
}
```

| Bound | Value | Meaning |
|-------|-------|---------|
| Ω(1) | Lower bound | At least 1 comparison (target is first element) |
| O(n) | Upper bound | At most n comparisons (target not found) |
| Θ — no tight bound that applies to both cases | |

There is no single Θ here because best and worst cases differ. We say: "linear search is O(n) worst case, Ω(1) best case."

---

### Merge Sort: A True Θ

Merge sort *always* splits and merges — there's no early exit:

```java
public void mergeSort(int[] arr, int lo, int hi) {
    if (lo >= hi) return;
    int mid = lo + (hi - lo) / 2;
    mergeSort(arr, lo, mid);       // always called
    mergeSort(arr, mid + 1, hi);   // always called
    merge(arr, lo, mid, hi);       // always called — O(n) work
}
// Best case = Worst case = Θ(n log n)
```

This is why merge sort is often preferred over quicksort when worst-case guarantees matter.

---

### Practical Summary for Interviews

In 99% of interviews, "what's the complexity?" means **O (upper bound, worst case)**. Use Θ and Ω only when:
1. Specifically asked for best-case or tight bound
2. Proving your solution is *optimal* (lower bound proof)
3. Discussing why one algorithm is strictly better than another

```
"This solution is O(n log n) using merge sort."
→ Correct and complete for an interview

"Sorting a comparison-based array cannot be faster than Ω(n log n)."
→ Using Ω to prove optimality — impressive addition

"Merge sort is Θ(n log n)."
→ More precise: says it's also never faster — use when asked
```

---

## Try It Yourself

**Exercise:** For each algorithm, state the Big-O (worst), Big-Ω (best), and whether a tight Θ exists.

1. Insertion sort on an array
2. QuickSort (with random pivot)
3. Building a HashMap from an array (inserting n elements)
4. Finding the maximum in an unsorted array

<details>
<summary>Show answers</summary>

1. **Insertion Sort**
   - Best: Ω(n) — already sorted, only n comparisons (no swaps)
   - Worst: O(n²) — reverse sorted, n²/2 comparisons and swaps
   - Tight bound: No single Θ (depends on input); sometimes written Θ(n²) for *average* over random inputs

2. **QuickSort (random pivot)**
   - Best: Ω(n log n) — perfect splits every time
   - Worst: O(n²) — always picks min/max as pivot (degenerate)
   - Average: Θ(n log n) — expected with random pivot
   - Note: In practice, random pivot makes O(n²) astronomically unlikely

3. **Building a HashMap (n insertions)**
   - Best/Worst: O(n) average, O(n²) worst case (all keys collide into one bucket)
   - In practice: Θ(n) amortized average with a good hash function

4. **Finding maximum in unsorted array**
   - Best: Ω(n) — must scan all elements regardless (no knowledge of where max is)
   - Worst: O(n)
   - Tight: Θ(n) — it's *always* exactly n-1 comparisons

</details>

---

## Capstone Connection

When describing AlgoForge implementations in code comments, use the precise notation:
```java
// Time: O(log n) worst case, Θ(log n) average
// Space: O(1) auxiliary
```
This level of precision is exactly what strong candidates write on whiteboards in FAANG interviews.
