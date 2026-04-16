package edu.yu.capstone.dsv.client;

import edu.yu.capstone.dsv.client.dto.CreateSecretRequest;
import edu.yu.capstone.dsv.client.dto.DeleteSecretRequest;
import edu.yu.capstone.dsv.client.dto.UpdateSecretRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public final class ClientCli {

	private ClientCli() {
	}

	public static void main(String[] args) {
		Client client = new Client(ClientProperties.fromEnvironment());
		printWelcome();
		printUsage();

		try (Scanner scanner = new Scanner(System.in)) {
			while (true) {
				System.out.print("dsv-client> ");
				if (!scanner.hasNextLine()) {
					break;
				}

				String input = scanner.nextLine().trim();
				if (input.isEmpty()) {
					continue;
				}

				if ("exit".equalsIgnoreCase(input) || "quit".equalsIgnoreCase(input)) {
					System.out.println("Goodbye.");
					break;
				}

				if ("help".equalsIgnoreCase(input)) {
					printUsage();
					continue;
				}

				String[] commandArgs = parseArguments(input);
				runCommand(client, commandArgs);
			}
		}
	}

	private static void runCommand(Client client, String[] args) {
		if (args.length == 0) {
			return;
		}

		String operation = args[0].toLowerCase();
		try {
			switch (operation) {
				case "ping" -> runPing(client, args);
				case "create" -> runCreate(client, args);
				case "get" -> runGet(client, args);
				case "update" -> runUpdate(client, args);
				case "delete" -> runDelete(client, args);
				default -> {
					System.out.println("Unknown command: " + args[0]);
					printUsage();
				}
			}
		} catch (ClientException ex) {
			printRequestFailure(ex);
		}
	}

	private static void runPing(Client client, String[] args) {
		if (args.length != 1) {
			printUsage();
			return;
		}

		String response = client.ping();
		System.out.println(response == null || response.isBlank() ? "OK" : response);
	}

	private static void runCreate(Client client, String[] args) {
		if (args.length != 3 && args.length != 4) {
			printUsage();
			return;
		}

		String authKey = args.length == 4 ? args[3] : "";
		String response = client.createSecret(new CreateSecretRequest(args[1], args[2], authKey));
		System.out.println(response);
	}

	private static void runGet(Client client, String[] args) {
		if (args.length != 2) {
			printUsage();
			return;
		}

		String response = client.getSecret(args[1]);
		System.out.println(response);
	}

	private static void runUpdate(Client client, String[] args) {
		if (args.length != 5 && args.length != 6) {
			printUsage();
			return;
		}

		String authKey = args.length == 6 ? args[5] : "";
		UpdateSecretRequest request = new UpdateSecretRequest(args[1], args[2], args[3], args[4], authKey);
		String response = client.updateSecret(request);
		System.out.println(response);
	}

	private static void runDelete(Client client, String[] args) {
		if (args.length != 2 && args.length != 3) {
			printUsage();
			return;
		}

		String authKey = args.length == 3 ? args[2] : "";
		String response = client.deleteSecret(new DeleteSecretRequest(args[1], authKey));
		if (response == null || response.isBlank()) {
			System.out.println("Delete succeeded (no response body).");
			return;
		}
		System.out.println(response);
	}

	private static void printUsage() {
		System.out.println("Usage:");
		System.out.println("  ping");
		System.out.println("  create <secretName> <secretValue> [authKey]");
		System.out.println("  get <secretId>");
		System.out.println("  update <currentName> <currentValue> <updatedName> <updatedValue> [authKey]");
		System.out.println("  delete <secretName> [authKey]");
		System.out.println("  help");
		System.out.println("  exit");
	}

	private static void printWelcome() {
		System.out.println("Distributed Secrets Vault Client CLI");
		System.out.println("Type a command and press Enter. Use help to print commands.");
	}

	private static void printRequestFailure(ClientException ex) {
		System.err.println("Request failed: " + ex.getMessage());
		if (ex.getStatusCode() > 0) {
			System.err.println("Status: " + ex.getStatusCode());
		}
		if (ex.getResponseBody() != null && !ex.getResponseBody().isBlank()) {
			System.err.println("Body: " + ex.getResponseBody());
		}
	}


	private static String[] parseArguments(String line) {
		List<String> tokens = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean inQuotes = false;

		for (int i = 0; i < line.length(); i++) {
			char ch = line.charAt(i);
			if (ch == '"') {
				inQuotes = !inQuotes;
				continue;
			}

			if (Character.isWhitespace(ch) && !inQuotes) {
				if (!current.isEmpty()) {
					tokens.add(current.toString());
					current.setLength(0);
				}
				continue;
			}

			current.append(ch);
		}

		if (!current.isEmpty()) {
			tokens.add(current.toString());
		}

		return tokens.toArray(new String[0]);
	}
}
