package com.ohhapple.monster_conquest.arena.external;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;
import java.util.UUID;

/** 主服和竞技子服共享的转服票据派生与接收回执校验。 */
public final class ArenaTransferAuth {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER =
            Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER =
            Base64.getUrlDecoder();

    private ArenaTransferAuth() {
    }

    public static String newSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    public static String newNonce() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    public static byte[] deriveTicketKey(
            String transferSecret, UUID sessionId,
            UUID playerId, String nonce) {
        byte[] secret = DECODER.decode(transferSecret);
        if (secret.length < 16) {
            throw new IllegalArgumentException(
                    "Transfer secret is too short");
        }
        String context = "ticket|" + sessionId + "|"
                + playerId + "|" + nonce;
        return hmac(secret, context.getBytes(StandardCharsets.UTF_8));
    }

    public static String signAcceptance(
            String transferSecret, UUID sessionId,
            UUID ticketPlayerId, UUID actualPlayerId,
            String nonce) {
        byte[] ticketKey = deriveTicketKey(
                transferSecret, sessionId,
                ticketPlayerId, nonce);
        String message = acceptanceMessage(
                sessionId, ticketPlayerId,
                actualPlayerId, nonce);
        return ENCODER.encodeToString(hmac(
                ticketKey,
                message.getBytes(StandardCharsets.UTF_8)));
    }

    public static boolean verifyAcceptance(
            String transferSecret, Receipt receipt) {
        try {
            String expected = signAcceptance(
                    transferSecret,
                    receipt.sessionId(),
                    receipt.playerId(),
                    receipt.actualPlayerId(),
                    receipt.nonce());
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    receipt.signature().getBytes(
                            StandardCharsets.US_ASCII));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public static Path receiptsDirectory(Path bridgeDirectory) {
        return bridgeDirectory.resolve(
                ArenaRuntime.TRANSFER_RECEIPTS_DIRECTORY);
    }

    public static void writeReceipt(
            Path bridgeDirectory,
            ArenaBridgeRequest request,
            UUID ticketPlayerId,
            UUID actualPlayerId) throws IOException {
        String nonce = request.transferNonce(ticketPlayerId);
        if (nonce.isBlank()) {
            throw new IOException(
                    "Missing transfer nonce for " + ticketPlayerId);
        }
        writeReceipt(
                bridgeDirectory, request.sessionId(),
                request.transferSecret(), ticketPlayerId,
                nonce, actualPlayerId);
    }

    public static void writeReceipt(
            Path bridgeDirectory, UUID sessionId,
            String transferSecret, UUID ticketPlayerId,
            String nonce, UUID actualPlayerId) throws IOException {
        if (transferSecret == null
                || transferSecret.isBlank()
                || nonce == null || nonce.isBlank()) {
            throw new IOException(
                    "Missing transfer authentication material");
        }
        String signature = signAcceptance(
                transferSecret, sessionId,
                ticketPlayerId, actualPlayerId, nonce);
        Receipt receipt = new Receipt(
                sessionId, ticketPlayerId,
                actualPlayerId, nonce,
                System.currentTimeMillis(), signature);
        Path path = receiptPath(
                bridgeDirectory, receipt.sessionId(), ticketPlayerId);
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(
                path.getFileName() + ".tmp");
        Properties properties = new Properties();
        properties.setProperty("sessionId",
                receipt.sessionId().toString());
        properties.setProperty("playerId",
                receipt.playerId().toString());
        properties.setProperty("actualPlayerId",
                receipt.actualPlayerId().toString());
        properties.setProperty("nonce", receipt.nonce());
        properties.setProperty("acceptedAt",
                Long.toString(receipt.acceptedAtMillis()));
        properties.setProperty("signature",
                receipt.signature());
        try (OutputStream output = Files.newOutputStream(temporary)) {
            properties.store(output,
                    "Monster Conquest transfer receipt");
        }
        try {
            Files.move(temporary, path,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, path,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static Receipt readReceipt(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return new Receipt(
                UUID.fromString(required(properties, "sessionId")),
                UUID.fromString(required(properties, "playerId")),
                UUID.fromString(required(properties, "actualPlayerId")),
                required(properties, "nonce"),
                Long.parseLong(required(properties, "acceptedAt")),
                required(properties, "signature"));
    }

    public static Path receiptPath(
            Path bridgeDirectory, UUID sessionId,
            UUID playerId) {
        return receiptsDirectory(bridgeDirectory)
                .resolve(sessionId + "-" + playerId
                        + ".properties");
    }

    private static String acceptanceMessage(
            UUID sessionId, UUID ticketPlayerId,
            UUID actualPlayerId, String nonce) {
        return "ACCEPT|" + sessionId + "|"
                + ticketPlayerId + "|" + actualPlayerId
                + "|" + nonce;
    }

    private static byte[] hmac(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "HmacSHA256 is unavailable", exception);
        }
    }

    private static String required(
            Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing transfer receipt property " + key);
        }
        return value;
    }

    public record Receipt(
            UUID sessionId, UUID playerId,
            UUID actualPlayerId, String nonce,
            long acceptedAtMillis, String signature) {
    }
}
