package market.ui;

import java.util.Scanner;

/**
 * The only place in the program that reads from the user.
 * <p>
 * Every method keeps asking until the answer makes sense, so that a wrong answer
 * can never stop the program.
 */
final class ConsoleInput {

    private final Scanner scanner = new Scanner(System.in);
    private final ConsoleOutput output;

    ConsoleInput(ConsoleOutput output) {
        this.output = output;
    }

    /** Reads a whole line, so that answers containing spaces are kept as they are. */
    String readLine(String prompt) {
        output.prompt(prompt);
        if (!scanner.hasNextLine()) {
            throw new EndOfInputException();
        }
        return scanner.nextLine();
    }

    /**
     * Reads a file path. Paths may contain spaces, and are often pasted with
     * surrounding quotation marks, which are removed here.
     */
    String readPath(String prompt) {
        String path = readLine(prompt).trim();
        if (path.length() >= 2 && path.startsWith("\"") && path.endsWith("\"")) {
            path = path.substring(1, path.length() - 1).trim();
        }
        return path;
    }

    int readNumberInRange(String prompt, int min, int max) {
        while (true) {
            String answer = readLine(prompt + " (" + min + "-" + max + ")").trim();
            try {
                int number = Integer.parseInt(answer);
                if (number < min || number > max) {
                    output.error("Please choose a number between " + min + " and " + max + ".");
                    continue;
                }
                return number;
            } catch (NumberFormatException e) {
                output.error(describe(answer) + " is not a whole number. "
                        + "Please choose a number between " + min + " and " + max + ".");
            }
        }
    }

    long readPositiveAmount(String prompt) {
        while (true) {
            String answer = readLine(prompt).trim();
            try {
                long amount = Long.parseLong(answer);
                if (amount <= 0) {
                    output.error("The amount must be greater than 0.");
                    continue;
                }
                return amount;
            } catch (NumberFormatException e) {
                output.error(describe(answer) + " is not a whole number. Please enter a whole number greater than 0.");
            }
        }
    }

    private static String describe(String answer) {
        return answer.isEmpty() ? "An empty answer" : "\"" + answer + "\"";
    }
}
