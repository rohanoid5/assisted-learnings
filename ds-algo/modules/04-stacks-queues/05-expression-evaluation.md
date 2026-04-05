# 4.5 — Expression Evaluation

## Concept

Evaluating arithmetic expressions — handling operator precedence and parentheses — is a classic stack application. The two key algorithms are the **two-stack method** (operands + operators) and **Shunting Yard** (convert infix to postfix, then evaluate postfix). These problems appear in phone screens and teach general recursive/iterative parsing.

---

## Deep Dive

### Operator Precedence via Stack

```
Evaluate: 3 + 5 × 2

Infix:   3  +  5  ×  2

Two-stack approach:
  nums:  []       ops:  []
  push 3           nums: [3]
  push +           ops:  [+]
  push 5           nums: [3, 5]
  see ×, higher precedence than + → push ×
                   ops:  [+, ×]
  push 2           nums: [3, 5, 2]
  end of input → flush ops:
    pop × → apply 5×2=10   nums: [3, 10]
    pop + → apply 3+10=13  nums: [13]
  Result: 13
```

### Postfix Evaluation (Reverse Polish Notation)

```
Postfix: 3  5  2  ×  +

Process left-to-right:
  see 3 → push.   stack: [3]
  see 5 → push.   stack: [3, 5]
  see 2 → push.   stack: [3, 5, 2]
  see × → pop 2 and 5, push 5×2=10.  stack: [3, 10]
  see + → pop 10 and 3, push 3+10=13. stack: [13]
  Result: 13 ✓

No precedence rules needed for postfix — just evaluate operands in order.
```

---

## Code Examples

### Basic Calculator II (LC #227) — No Parentheses

```java
// Handles +, -, *, / with correct precedence. No parentheses.
// Strategy: process token by token using the *previous operator*.

public int calculate(String s) {
    Deque<Integer> stack = new ArrayDeque<>();
    int num = 0;
    char op = '+';   // assume a leading + before the first number

    for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        if (Character.isDigit(c))
            num = num * 10 + (c - '0');

        if ((!Character.isDigit(c) && c != ' ') || i == s.length() - 1) {
            switch (op) {
                case '+': stack.push(num);          break;
                case '-': stack.push(-num);         break;
                case '*': stack.push(stack.pop() * num); break;
                case '/': stack.push(stack.pop() / num); break;
            }
            op = c;
            num = 0;
        }
    }

    int result = 0;
    while (!stack.isEmpty()) result += stack.pop();
    return result;
    // Time: O(n), Space: O(n)
}
```

### Basic Calculator I (LC #224) — With Parentheses, No Multiplication

```java
// Handles +, -, parentheses. No * or /.
// When we see '(', push the running result and sign onto the stack.
// When we see ')', pop and restore.

public int calculateWithParens(String s) {
    Deque<Integer> stack = new ArrayDeque<>();
    int result = 0, num = 0, sign = 1;

    for (char c : s.toCharArray()) {
        if (Character.isDigit(c)) {
            num = num * 10 + (c - '0');
        } else if (c == '+') {
            result += sign * num;
            num = 0; sign = 1;
        } else if (c == '-') {
            result += sign * num;
            num = 0; sign = -1;
        } else if (c == '(') {
            stack.push(result);  // save result before parenthesis
            stack.push(sign);    // save sign before parenthesis
            result = 0; sign = 1;
        } else if (c == ')') {
            result += sign * num;
            num = 0;
            result *= stack.pop();    // multiply by sign before '('
            result += stack.pop();    // add result before '('
        }
    }
    return result + sign * num;
}
```

### RPN Evaluation (LC #150)

```java
public int evalRPN(String[] tokens) {
    Deque<Integer> stack = new ArrayDeque<>();
    Set<String> ops = Set.of("+", "-", "*", "/");

    for (String token : tokens) {
        if (ops.contains(token)) {
            int b = stack.pop(), a = stack.pop();
            switch (token) {
                case "+": stack.push(a + b); break;
                case "-": stack.push(a - b); break;
                case "*": stack.push(a * b); break;
                case "/": stack.push(a / b); break;
            }
        } else {
            stack.push(Integer.parseInt(token));
        }
    }
    return stack.pop();
}
```

---

## Try It Yourself

**Exercise:** Implement the Shunting Yard algorithm to convert an infix expression string (with `+`,`-`,`*`,`/` and parentheses) to a postfix (RPN) string. Then evaluate the result using your RPN evaluator.

Input: `"3+5*2"` → postfix: `"3 5 2 * +"` → result: `13`

<details>
<summary>Show solution</summary>

```java
public String infixToPostfix(String expr) {
    StringBuilder output = new StringBuilder();
    Deque<Character> ops = new ArrayDeque<>();
    Map<Character, Integer> prec = Map.of('+', 1, '-', 1, '*', 2, '/', 2);

    for (char c : expr.toCharArray()) {
        if (Character.isDigit(c)) {
            output.append(c).append(' ');
        } else if (c == '(') {
            ops.push(c);
        } else if (c == ')') {
            while (ops.peek() != '(') output.append(ops.pop()).append(' ');
            ops.pop();  // discard '('
        } else {  // operator
            while (!ops.isEmpty() && ops.peek() != '(' &&
                   prec.getOrDefault(ops.peek(), 0) >= prec.get(c))
                output.append(ops.pop()).append(' ');
            ops.push(c);
        }
    }
    while (!ops.isEmpty()) output.append(ops.pop()).append(' ');
    return output.toString().trim();
}
```

</details>

---

## Capstone Connection

Expression parsing is a gateway to compiler design — lexers, parsers, and ASTs are built from the same principles. In interviews, calculator problems are a signal that the interviewer wants to see clean state machine thinking — the sign/stack approach above is the idiomatic FAANG answer.
