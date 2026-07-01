package qrcode;

/**
 * Correction d'erreurs Reed-Solomon dans le corps de Galois GF(256),
 * polynôme primitif 0x11D (x^8 + x^4 + x^3 + x^2 + 1), élément générateur alpha = 2.
 *
 * À partir des codewords de données d'un bloc, calcule les codewords de correction.
 */
public final class ErrorCorrectionEncoding {

    private ErrorCorrectionEncoding() {}

    private static final int[] EXP = new int[512]; // table des puissances de alpha
    private static final int[] LOG = new int[256]; // table des logarithmes

    static {
        int x = 1;
        for (int i = 0; i < 255; i++) {
            EXP[i] = x;
            LOG[x] = i;
            x <<= 1;
            if ((x & 0x100) != 0) {
                x ^= 0x11D;
            }
        }
        // duplication pour éviter les modulos lors des multiplications
        for (int i = 255; i < 512; i++) {
            EXP[i] = EXP[i - 255];
        }
    }

    /** Multiplication dans GF(256). */
    private static int mul(int a, int b) {
        if (a == 0 || b == 0) return 0;
        return EXP[LOG[a] + LOG[b]];
    }

    /** Polynôme générateur de degré {@code degree} : produit des (x - alpha^i), monique (coef de tête = 1). */
    private static int[] generatorPoly(int degree) {
        int[] g = {1};
        for (int i = 0; i < degree; i++) {
            int[] next = new int[g.length + 1];
            for (int j = 0; j < g.length; j++) {
                next[j] ^= g[j];
                next[j + 1] ^= mul(g[j], EXP[i]);
            }
            g = next;
        }
        return g; // longueur degree+1, g[0] = 1
    }

    /**
     * Calcule les {@code nEc} codewords de correction d'erreurs pour le bloc de données donné.
     * (Reste de la division polynomiale de data·x^nEc par le polynôme générateur, dans GF(256).)
     */
    public static int[] encode(int[] data, int nEc) {
        int[] gen = generatorPoly(nEc);
        int[] res = new int[data.length + nEc];
        System.arraycopy(data, 0, res, 0, data.length);

        for (int i = 0; i < data.length; i++) {
            int coef = res[i] & 0xFF;
            if (coef != 0) {
                for (int j = 0; j < gen.length; j++) {
                    res[i + j] ^= mul(gen[j], coef);
                }
            }
        }

        int[] ec = new int[nEc];
        System.arraycopy(res, data.length, ec, 0, nEc);
        return ec;
    }
}
