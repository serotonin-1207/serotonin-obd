import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;

/** 공개 데이터 팩을 복사하고 SHA-256 및 ECDSA 서명이 든 배포 매니페스트를 만든다. */
public final class SignKnowledgePack {
    private static final String PACK_URL =
        "https://raw.githubusercontent.com/serotonin-1207/serotonin-obd/main/public-data/diagnostic_knowledge_pack.json";

    public static void main(String[] args) throws Exception {
        if (args.length != 7) {
            throw new IllegalArgumentException("asset publicData publicManifest bundledManifest privateKey revision minAppVersion 순서로 지정하세요.");
        }
        Path asset = Path.of(args[0]);
        Path publicData = Path.of(args[1]);
        Path manifest = Path.of(args[2]);
        Path bundledManifest = Path.of(args[3]);
        Path privateKeyPath = Path.of(args[4]);
        int revision = Integer.parseInt(args[5]);
        int minAppVersionCode = Integer.parseInt(args[6]);
        // Git의 Windows 줄바꿈 변환과 무관하게 서명·게시 바이트를 LF로 고정한다.
        byte[] pack = new String(Files.readAllBytes(asset), StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .getBytes(StandardCharsets.UTF_8);
        if (pack.length == 0 || pack.length > 2 * 1024 * 1024) {
            throw new IllegalArgumentException("데이터 팩 크기가 허용 범위를 벗어났습니다.");
        }
        Files.write(asset, pack);

        PrivateKey privateKey;
        byte[] publicKey;
        if (Files.exists(privateKeyPath)) {
            privateKey = KeyFactory.getInstance("EC").generatePrivate(
                new PKCS8EncodedKeySpec(Files.readAllBytes(privateKeyPath))
            );
            // 기존 키를 쓸 때 공개키는 별도 파일에서 읽는다.
            Path publicKeyPath = Path.of(privateKeyPath + ".pub");
            publicKey = Files.readAllBytes(publicKeyPath);
        } else {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair pair = generator.generateKeyPair();
            privateKey = pair.getPrivate();
            publicKey = pair.getPublic().getEncoded();
            Files.createDirectories(privateKeyPath.toAbsolutePath().getParent());
            Files.write(privateKeyPath, privateKey.getEncoded());
            Files.write(Path.of(privateKeyPath + ".pub"), publicKey);
        }

        String sha256 = hex(MessageDigest.getInstance("SHA-256").digest(pack));
        String packVersion = findJsonString(new String(pack, StandardCharsets.UTF_8), "version");
        String header = "leafobd-knowledge-pack-v1\n" + revision + "\n" + packVersion + "\n" + sha256 + "\n";
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(privateKey);
        signer.update(header.getBytes(StandardCharsets.UTF_8));
        signer.update(pack);
        String signature = Base64.getEncoder().encodeToString(signer.sign());

        Files.createDirectories(publicData.toAbsolutePath().getParent());
        Files.write(publicData, pack);
        String json = "{\n" +
            "  \"schemaVersion\": 1,\n" +
            "  \"revision\": " + revision + ",\n" +
            "  \"packVersion\": \"" + packVersion + "\",\n" +
            "  \"publishedAt\": \"" + LocalDate.now(ZoneOffset.UTC) + "\",\n" +
            "  \"minAppVersionCode\": " + minAppVersionCode + ",\n" +
            "  \"packUrl\": \"" + PACK_URL + "\",\n" +
            "  \"sha256\": \"" + sha256 + "\",\n" +
            "  \"signatureAlgorithm\": \"SHA256withECDSA\",\n" +
            "  \"signatureBase64\": \"" + signature + "\"\n" +
            "}\n";
        Files.writeString(manifest, json, StandardCharsets.UTF_8);
        Files.createDirectories(bundledManifest.toAbsolutePath().getParent());
        Files.writeString(bundledManifest, json, StandardCharsets.UTF_8);
        System.out.println(Base64.getEncoder().encodeToString(publicKey));
    }

    private static String findJsonString(String json, String key) {
        String token = "\"" + key + "\"";
        int keyIndex = json.indexOf(token);
        int colon = json.indexOf(':', keyIndex + token.length());
        int start = json.indexOf('"', colon + 1) + 1;
        int end = json.indexOf('"', start);
        if (keyIndex < 0 || colon < 0 || start == 0 || end < 0) {
            throw new IllegalArgumentException("데이터 팩 버전을 찾지 못했습니다.");
        }
        return json.substring(start, end);
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) value.append(String.format("%02x", b & 0xff));
        return value.toString();
    }
}
