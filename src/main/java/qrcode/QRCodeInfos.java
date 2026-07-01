package qrcode;

/**
 * Constantes et tables du standard QR code, limitées aux versions 1 à 4
 * (matrices 21x21 à 33x33), en mode d'encodage "byte".
 *
 * Toutes les valeurs viennent du standard ISO/IEC 18004.
 */
public final class QRCodeInfos {

    private QRCodeInfos() {}

    /** Niveau de correction d'erreurs. Les bits de format suivent le standard (L=01, M=00, Q=11, H=10). */
    public enum ErrorLevel {
        L(0b01), M(0b00), Q(0b11), H(0b10);
        public final int formatBits;
        ErrorLevel(int formatBits) { this.formatBits = formatBits; }
    }

    public static final int MAX_VERSION = 4;

    /**
     * Pour chaque version (1..4) et chaque niveau (ordre L, M, Q, H) :
     * { nombre total de codewords de données, codewords de correction par bloc, nombre de blocs }.
     */
    private static final int[][][] EC_TABLE = {
        /* v1 */ { {19, 7, 1}, {16, 10, 1}, {13, 13, 1}, { 9, 17, 1} },
        /* v2 */ { {34, 10, 1}, {28, 16, 1}, {22, 22, 1}, {16, 28, 1} },
        /* v3 */ { {55, 15, 1}, {44, 26, 1}, {34, 18, 2}, {26, 22, 2} },
        /* v4 */ { {80, 20, 1}, {64, 18, 2}, {48, 26, 2}, {36, 16, 4} },
    };

    public static int matrixSize(int version) {
        return 17 + 4 * version;
    }

    public static int totalDataCodewords(int version, ErrorLevel lvl) {
        return EC_TABLE[version - 1][lvl.ordinal()][0];
    }

    public static int ecCodewordsPerBlock(int version, ErrorLevel lvl) {
        return EC_TABLE[version - 1][lvl.ordinal()][1];
    }

    public static int numBlocks(int version, ErrorLevel lvl) {
        return EC_TABLE[version - 1][lvl.ordinal()][2];
    }

    /** Bits de remplissage (remainder) ajoutés après les codewords : 0 pour la v1, 7 pour les v2-4. */
    public static int remainderBits(int version) {
        return version == 1 ? 0 : 7;
    }

    /** Centre de l'unique motif d'alignement pour les versions 2-4, ou -1 si aucun (v1). */
    public static int alignmentCenter(int version) {
        switch (version) {
            case 2: return 18;
            case 3: return 22;
            case 4: return 26;
            default: return -1;
        }
    }

    /** Nombre maximal d'octets d'entrée (mode byte) tenant dans la version/niveau donné. */
    public static int maxInputBytes(int version, ErrorLevel lvl) {
        // En-tête mode byte = 4 bits (mode) + 8 bits (compteur) = 12 bits = 1.5 codeword.
        return totalDataCodewords(version, lvl) - 2;
    }

    /** Longueur de l'indicateur de nombre de caractères, mode byte, versions 1-9 : 8 bits. */
    public static int byteCountBits(int version) {
        return 8;
    }

    /**
     * Séquence de format sur 15 bits : code BCH(15,5) du couple (niveau, masque),
     * déjà combiné (XOR) avec le masque de format 0x5412 du standard.
     */
    public static int formatSequence(ErrorLevel lvl, int mask) {
        int data = (lvl.formatBits << 3) | mask;   // 5 bits utiles
        int rem = data;
        for (int i = 0; i < 10; i++) {
            rem = (rem << 1) ^ (((rem >>> 9) & 1) * 0x537);
        }
        int bits = ((data << 10) | rem) ^ 0x5412;
        return bits & 0x7FFF;
    }
}
