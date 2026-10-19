package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.datasource.url=jdbc:h2:mem:sentinelpay-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"logging.level.root=WARN"
})
class DemoApplicationTests {

	@Value("${local.server.port}")
	private int port;
	private final HttpClient http = HttpClient.newHttpClient();
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void contextLoads() {
	}

	@Test
	void openApiDocumentIsServedOverHttp() throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + port + "/v3/api-docs"))
				.GET()
				.build();

		HttpResponse<String> response = HttpClient.newHttpClient()
				.send(request, HttpResponse.BodyHandlers.ofString());

		assertEquals(200, response.statusCode());
		assertTrue(response.body().contains("/api/v1/transactions"));
		assertEquals(200, send("GET", "/swagger-ui/index.html", null).statusCode());
	}

	@Test
	void invalidTransactionRequestGetsSafeValidationResponseOverHttp() throws Exception {
		HttpResponse<String> response = send("POST", "/api/v1/transactions", "{}");

		assertEquals(400, response.statusCode());
		assertTrue(response.body().contains("Validation failed"));
		assertTrue(response.body().contains("validationErrors"));
	}

	@Test
	void malformedJsonMissingRecordsAndUnsupportedMethodsReturnClientErrors() throws Exception {
		assertEquals(400, send("POST", "/api/v1/transactions", "{bad-json").statusCode());
		assertEquals(404, send("GET", "/api/v1/transactions/" + UUID.randomUUID(), null).statusCode());
		HttpResponse<String> unsupported = send("PUT", "/api/v1/transactions", "{}");
		assertEquals(405, unsupported.statusCode());
		assertTrue(unsupported.body().contains("not supported"));
		assertEquals(400, send("GET", "/api/accounts/kyc-status", null).statusCode());

		HttpRequest unsupportedContentTypeRequest = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + port + "/api/v1/transactions"))
				.header("Content-Type", "text/plain")
				.POST(HttpRequest.BodyPublishers.ofString("{}"))
				.build();
		HttpResponse<String> unsupportedContentTypeResponse = http.send(unsupportedContentTypeRequest,
				HttpResponse.BodyHandlers.ofString());
		assertEquals(415, unsupportedContentTypeResponse.statusCode(), unsupportedContentTypeResponse.body());
	}

	@Test
	void accountDeviceSessionPaymentTransactionAndDecisionRoutesPersistAndRead() throws Exception {
		String timestamp = LocalDateTime.now().withNano(0).toString();
		HttpResponse<String> accountResponse = send("POST", "/api/accounts",
				"{\"accountCreatedAt\":\"" + timestamp + "\",\"homeZip\":\"10001\",\"kycStatus\":\"VERIFIED\"}");
		assertEquals(201, accountResponse.statusCode(), accountResponse.body());
		UUID accountId = UUID.fromString(objectMapper.readTree(accountResponse.body()).get("userId").asText());
		assertEquals(200, send("GET", "/api/accounts/" + accountId, null).statusCode());
		assertEquals(200, send("GET", "/api/accounts", null).statusCode());
		assertEquals(200, send("GET", "/api/accounts/kyc-status?status=VERIFIED", null).statusCode());
		assertEquals(200, send("PUT", "/api/accounts/" + accountId,
				"{\"accountCreatedAt\":\"" + timestamp + "\",\"homeZip\":\"10002\",\"kycStatus\":\"VERIFIED\"}").statusCode());
		assertEquals(200, send("PATCH", "/api/accounts/" + accountId, "{\"isSuspended\":true}").statusCode());
		assertEquals(400, send("PATCH", "/api/accounts/" + accountId, "{}").statusCode());
		assertEquals(400, send("PATCH", "/api/accounts/" + accountId, "{\"unknownField\":\"x\"}").statusCode());
		assertEquals(400, send("PATCH", "/api/accounts/" + accountId, "{\"isSuspended\":\"yes\"}").statusCode());

		HttpResponse<String> profileResponse = send("POST", "/api/device-profiles",
				"{\"deviceFingerprint\":\"integration-device\",\"createdAt\":\"" + timestamp
						+ "\",\"updatedAt\":\"" + timestamp + "\",\"profileStatus\":\"ACTIVE\"}");
		assertEquals(201, profileResponse.statusCode(), profileResponse.body());
		long profileId = objectMapper.readTree(profileResponse.body()).get("profileId").asLong();
		assertEquals(200, send("GET", "/api/device-profiles/" + profileId, null).statusCode());
		assertEquals(200, send("GET", "/api/device-profiles", null).statusCode());
		assertEquals(200, send("GET", "/api/device-profiles/fingerprint/integration-device", null).statusCode());
		assertEquals(200, send("GET", "/api/device-profiles/status?status=ACTIVE", null).statusCode());
		assertEquals(200, send("PUT", "/api/device-profiles/" + profileId,
				"{\"deviceFingerprint\":\"integration-device\",\"createdAt\":\"" + timestamp
						+ "\",\"updatedAt\":\"" + timestamp + "\",\"profileStatus\":\"ACTIVE\"}").statusCode());
		assertEquals(200, send("PATCH", "/api/device-profiles/" + profileId,
				"{\"profileStatus\":\"REVIEW\"}").statusCode());
		assertEquals(400, send("PATCH", "/api/device-profiles/" + profileId, "{}").statusCode());
		assertEquals(400, send("PATCH", "/api/device-profiles/" + profileId,
				"{\"unknownField\":\"x\"}").statusCode());
		assertEquals(400, send("PATCH", "/api/device-profiles/" + profileId,
				"{\"profileStatus\":10}").statusCode());

		HttpResponse<String> sessionResponse = send("POST", "/api/sessions",
				"{\"geoCountry\":\"US\",\"userAgent\":\"SentinelPay-test\"}");
		assertEquals(201, sessionResponse.statusCode(), sessionResponse.body());
		long sessionId = objectMapper.readTree(sessionResponse.body()).get("sessionId").asLong();
		assertEquals(200, send("GET", "/api/sessions/" + sessionId, null).statusCode());
		assertEquals(200, send("GET", "/api/sessions", null).statusCode());
		assertEquals(200, send("PUT", "/api/sessions/" + sessionId,
				"{\"geoCountry\":\"US\",\"userAgent\":\"SentinelPay-test-updated\"}").statusCode());
		assertEquals(200, send("PATCH", "/api/sessions/" + sessionId,
				"{\"ipAddress\":\"192.0.2.10\"}").statusCode());
		assertEquals(400, send("PATCH", "/api/sessions/" + sessionId, "{}").statusCode());
		assertEquals(400, send("PATCH", "/api/sessions/" + sessionId, "{\"unknownField\":true}").statusCode());
		assertEquals(400, send("PATCH", "/api/sessions/" + sessionId, "{\"ipAddress\":123}").statusCode());

		String cardToken = "integration-token-" + UUID.randomUUID();
		HttpResponse<String> paymentResponse = send("POST", "/api/v1/payment-methods?userId=" + accountId,
				"{\"cardToken\":\"" + cardToken
						+ "\",\"binNumber\":\"411111\",\"cardType\":\"CREDIT\",\"issuerBank\":\"Test Bank\",\"cardCountry\":\"US\",\"cardStatus\":\"ACTIVE\"}");
		assertEquals(201, paymentResponse.statusCode(), paymentResponse.body());
		assertEquals(200, send("GET", "/api/v1/payment-methods/" + cardToken, null).statusCode());
		assertEquals(200, send("GET", "/api/v1/payment-methods", null).statusCode());
		UUID missingAccountId = UUID.randomUUID();
		String paymentUpdate = "{\"cardToken\":\"" + cardToken + "\",\"binNumber\":\"411111\","
				+ "\"cardType\":\"CREDIT\",\"issuerBank\":\"Test Bank\","
				+ "\"cardCountry\":\"US\",\"cardStatus\":\"ACTIVE\"}";
		HttpResponse<String> missingPaymentAccountPut = send("PUT",
				"/api/v1/payment-methods/" + cardToken + "?userId=" + missingAccountId, paymentUpdate);
		assertEquals(404, missingPaymentAccountPut.statusCode(), missingPaymentAccountPut.body());
		assertEquals(404, send("PATCH", "/api/v1/payment-methods/" + cardToken + "?userId=" + missingAccountId,
				"{\"cardStatus\":\"ACTIVE\"}").statusCode());
		assertEquals(200, send("PUT", "/api/v1/payment-methods/" + cardToken, paymentUpdate).statusCode());
		assertEquals(200, send("PATCH", "/api/v1/payment-methods/" + cardToken,
				"{\"cardStatus\":\"REVIEW\"}").statusCode());
		assertEquals(400, send("PATCH", "/api/v1/payment-methods/" + cardToken, "{}").statusCode());
		assertEquals(400, send("PATCH", "/api/v1/payment-methods/" + cardToken,
				"{\"unknownField\":\"x\"}").statusCode());
		assertEquals(400, send("PATCH", "/api/v1/payment-methods/" + cardToken,
				"{\"cardStatus\":1}").statusCode());

		HttpResponse<String> transactionResponse = send("POST", "/api/v1/transactions",
				"{\"amount\":12.50,\"currency\":\"USD\",\"merchantId\":\"merchant-test\","
						+ "\"mccCode\":\"5411\",\"cardToken\":\"" + cardToken + "\",\"sessionId\":" + sessionId + "}");
		assertEquals(201, transactionResponse.statusCode(), transactionResponse.body());
		JsonNode transaction = objectMapper.readTree(transactionResponse.body());
		String transactionId = transaction.get("transactionId").asText();
		assertEquals(200, send("GET", "/api/v1/transactions/" + transactionId, null).statusCode());
		assertEquals(200, send("GET", "/api/v1/transactions", null).statusCode());
		String transactionUpdate = "{\"amount\":13.00,\"currency\":\"USD\",\"merchantId\":\"merchant-test\","
				+ "\"mccCode\":\"5411\",\"timestamp\":\"" + timestamp + "\"}";
		assertEquals(200, send("PUT", "/api/v1/transactions/" + transactionId, transactionUpdate).statusCode());
		assertEquals(200, send("PATCH", "/api/v1/transactions/" + transactionId,
				"{\"amount\":14.00}").statusCode());
		assertEquals(400, send("PATCH", "/api/v1/transactions/" + transactionId, "{}").statusCode());
		assertEquals(400, send("PATCH", "/api/v1/transactions/" + transactionId,
				"{\"unknownField\":\"x\"}").statusCode());
		assertEquals(400, send("PATCH", "/api/v1/transactions/" + transactionId,
				"{\"timestamp\":12}").statusCode());
		assertEquals(400, send("PATCH", "/api/v1/transactions/" + transactionId,
				"{\"timestamp\":\"not-a-date\"}").statusCode());
		assertEquals(400, send("POST", "/api/v1/decisions",
				"{\"transaction\":{\"transactionId\":\"" + transactionId + "\"}}").statusCode());

		HttpResponse<String> decisionResponse = send("POST", "/api/v1/decisions",
				"{\"transaction\":{\"transactionId\":\"" + transactionId
						+ "\"},\"riskScore\":20,\"actionTaken\":\"APPROVE\",\"latencyMs\":2}");
		assertEquals(201, decisionResponse.statusCode(), decisionResponse.body());
		String decisionId = objectMapper.readTree(decisionResponse.body()).get("assessmentId").asText();
		assertEquals(200, send("GET", "/api/v1/decisions/" + decisionId, null).statusCode());
		assertEquals(200, send("GET", "/api/v1/decisions", null).statusCode());
		String decisionUpdate = "{\"riskScore\":30,\"actionTaken\":\"REVIEW\",\"latencyMs\":3}";
		assertEquals(200, send("PUT", "/api/v1/decisions/" + decisionId, decisionUpdate).statusCode());
		assertEquals(200, send("PATCH", "/api/v1/decisions/" + decisionId,
				"{\"riskScore\":40}").statusCode());
		assertEquals(400, send("PATCH", "/api/v1/decisions/" + decisionId, "{}").statusCode());
		assertEquals(400, send("PATCH", "/api/v1/decisions/" + decisionId,
				"{\"unknownField\":\"x\"}").statusCode());
		assertEquals(400, send("PATCH", "/api/v1/decisions/" + decisionId,
				"{\"ruleFlags\":\"not-an-object\"}").statusCode());

		assertEquals(204, send("DELETE", "/api/v1/decisions/" + decisionId, null).statusCode());
		assertEquals(204, send("DELETE", "/api/v1/transactions/" + transactionId, null).statusCode());
		assertEquals(204, send("DELETE", "/api/v1/payment-methods/" + cardToken, null).statusCode());
		assertEquals(204, send("DELETE", "/api/sessions/" + sessionId, null).statusCode());
		assertEquals(204, send("DELETE", "/api/accounts/" + accountId, null).statusCode());
		assertEquals(204, send("DELETE", "/api/device-profiles/" + profileId, null).statusCode());
	}

	private HttpResponse<String> send(String method, String path, String body) throws Exception {
		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + port + path));
		if (body == null) {
			builder.method(method, HttpRequest.BodyPublishers.noBody());
		} else {
			builder.header("Content-Type", "application/json")
					.method(method, HttpRequest.BodyPublishers.ofString(body));
		}
		return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
	}
}
