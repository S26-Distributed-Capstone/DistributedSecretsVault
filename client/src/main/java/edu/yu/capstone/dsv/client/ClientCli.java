package edu.yu.capstone.dsv.client;

import edu.yu.capstone.dsv.client.dto.CreateSecretRequest;
import edu.yu.capstone.dsv.client.dto.DeleteSecretRequest;
import edu.yu.capstone.dsv.client.dto.UpdateSecretRequest;

public final class ClientCli {

	private ClientCli() {
	}

	public static void main(String[] args) {
		if (args.length == 0) {
			printUsage();
			return;
		}

		Client client = new Client(ClientProperties.fromEnvironment());
		String operation = args[0].toLowerCase();

		try {
			switch (operation) {
				case "create" -> runCreate(client, args);
				case "get" -> runGet(client, args);
				case "update" -> runUpdate(client, args);
				case "delete" -> runDelete(client, args);
				default -> printUsage();
			}
		} catch (ClientException ex) {
			System.err.println("Request failed: " + ex.getMessage());
			if (ex.getStatusCode() > 0) {
				System.err.println("Status: " + ex.getStatusCode());
			}
			if (ex.getResponseBody() != null && !ex.getResponseBody().isBlank()) {
				System.err.println("Body: " + ex.getResponseBody());
			}
			System.exit(1);
		}
	}

	private static void runCreate(Client client, String[] args) {
		if (args.length != 3) {
			printUsage();
			return;
		}

		String response = client.createSecret(new CreateSecretRequest(args[1], args[2]));
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
		if (args.length != 5) {
			printUsage();
			return;
		}

		UpdateSecretRequest request = new UpdateSecretRequest(args[1], args[2], args[3], args[4]);
		String response = client.updateSecret(request);
		System.out.println(response);
	}

	private static void runDelete(Client client, String[] args) {
		if (args.length != 2) {
			printUsage();
			return;
		}

		client.deleteSecret(new DeleteSecretRequest(args[1]));
		System.out.println("Delete request sent successfully.");
	}

	private static void printUsage() {
		System.out.println("Usage:");
		System.out.println("  create <secretName> <secretValue>");
		System.out.println("  get <secretId>");
		System.out.println("  update <currentName> <currentValue> <updatedName> <updatedValue>");
		System.out.println("  delete <secretName>");
	}
}
