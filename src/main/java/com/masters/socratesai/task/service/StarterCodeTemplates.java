package com.masters.socratesai.task.service;

import lombok.experimental.UtilityClass;

/** Runnable entry points only; the student still supplies the problem's algorithm. */
@UtilityClass
public class StarterCodeTemplates {
    public String forLanguage(String languageKey) {
        return switch (languageKey) {
            case "java" -> """
                import java.util.Scanner;

                public class Main {
                    public static void main(String[] args) {
                        Scanner input = new Scanner(System.in);
                        solve(input);
                    }

                    static void solve(Scanner input) {
                        // TODO: Read the input, write your logic, and print the answer.
                        // Read a value with input.nextInt() or input.next().
                        // Print your result with System.out.println(...).
                    }
                }
                """;
            case "python3" -> """
                import sys


                def solve():
                    data = sys.stdin.read().split()
                    # TODO: Parse data, write your logic, and print the answer.
                    pass


                if __name__ == "__main__":
                    solve()
                """;
            case "cpp17" -> """
                #include <iostream>

                void solve() {
                    // TODO: Read from std::cin, write your logic, and print to std::cout.
                }

                int main() {
                    std::ios::sync_with_stdio(false);
                    std::cin.tie(nullptr);
                    solve();
                    return 0;
                }
                """;
            default -> "";
        };
    }

    public String resolve(String languageKey, String authoredCode) {
        return authoredCode == null || authoredCode.isBlank() ? forLanguage(languageKey) : authoredCode;
    }
}
