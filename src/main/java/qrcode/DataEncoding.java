package qrcode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import qrcode.QRCodeInfos.ErrorLevel;

/**
 * Encodage des données en mode "byte" :
 *  - choix de la plus petite version (1-4) qui contient le texte,
 *  - construction du flux de bits (mode, compteur, données, terminateur, remplissage),
 *  - découpage en blocs, ajout de la correction d'erreurs, et entrelacement final.
 */
public final class DataEncoding {

    private DataEncoding() {}

    /** Résultat de l'encodage : la version choisie et la suite finale de codewords (données + correction entrelacées). */
    public static final class Encoded {
        public final int version;
        public final int[] codewords;
        public Encoded(int version, int[] codewords) {
            this.version = version;
            this.codewords = codewords;
        }
    }

    public static Encoded encode(String input, ErrorLevel level) {
        byte[] raw = input.getBytes(StandardCharsets.UTF_8);

        int version = -1;
        for (int v = 1; v <= QRCodeInfos.MAX_VERSION; v++) {
            if (raw.length <= QRCodeInfos.maxInputBytes(v, level)) {
                version = v;
                break;
            }
        }
        if (version == -1) {
            throw new IllegalArgumentException(
                "Texte trop long pour un QR code v1-4 : " + raw.length + " octets, maximum "
                + QRCodeInfos.maxInputBytes(QRCodeInfos.MAX_VERSION, level)
                + " octets au niveau " + level + ".");
        }

        int totalData = QRCodeInfos.totalDataCodewords(version, level);
        int capacityBits = totalData * 8;

        List<Integer> bits = new ArrayList<>();
        appendBits(bits, 0b0100, 4);                                  // indicateur de mode "byte"
        appendBits(bits, raw.length, QRCodeInfos.byteCountBits(version)); // nombre d'octets
        for (byte b : raw) {
            appendBits(bits, b & 0xFF, 8);                            // les données
        }

        // terminateur : jusqu'à 4 zéros, sans dépasser la capacité
        appendBits(bits, 0, Math.min(4, capacityBits - bits.size()));
        // alignement sur l'octet
        while (bits.size() % 8 != 0) bits.add(0);
        // octets de remplissage alternés 0xEC / 0x11
        int[] pad = {0xEC, 0x11};
        for (int i = 0; bits.size() < capacityBits; i++) {
            appendBits(bits, pad[i % 2], 8);
        }

        // conversion en codewords de données
        int[] dataCodewords = new int[totalData];
        for (int i = 0; i < totalData; i++) {
            int value = 0;
            for (int j = 0; j < 8; j++) {
                value = (value << 1) | bits.get(i * 8 + j);
            }
            dataCodewords[i] = value;
        }

        // découpage en blocs (tous de même taille pour les versions 1-4)
        int nBlocks = QRCodeInfos.numBlocks(version, level);
        int ecLen = QRCodeInfos.ecCodewordsPerBlock(version, level);
        int perBlock = totalData / nBlocks;

        int[][] dataBlocks = new int[nBlocks][];
        int[][] ecBlocks = new int[nBlocks][];
        for (int b = 0; b < nBlocks; b++) {
            int[] block = new int[perBlock];
            System.arraycopy(dataCodewords, b * perBlock, block, 0, perBlock);
            dataBlocks[b] = block;
            ecBlocks[b] = ErrorCorrectionEncoding.encode(block, ecLen);
        }

        // entrelacement : données colonne par colonne, puis correction colonne par colonne
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < perBlock; i++) {
            for (int b = 0; b < nBlocks; b++) out.add(dataBlocks[b][i]);
        }
        for (int i = 0; i < ecLen; i++) {
            for (int b = 0; b < nBlocks; b++) out.add(ecBlocks[b][i]);
        }

        int[] result = new int[out.size()];
        for (int i = 0; i < out.size(); i++) result[i] = out.get(i);
        return new Encoded(version, result);
    }

    private static void appendBits(List<Integer> bits, int value, int length) {
        for (int i = length - 1; i >= 0; i--) {
            bits.add((value >> i) & 1);
        }
    }
}
