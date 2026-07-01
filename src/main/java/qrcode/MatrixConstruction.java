package qrcode;

import qrcode.QRCodeInfos.ErrorLevel;

/**
 * Construction de la matrice du QR code :
 *  - motifs de fonction (détection, séparateurs, synchro, alignement, module sombre),
 *  - placement des données en zig-zag,
 *  - application des 8 masques et choix du meilleur via les pénalités du standard,
 *  - écriture de l'information de format.
 *
 * Convention : matrice[row][col], 1 = module noir, 0 = module blanc.
 */
public final class MatrixConstruction {

    private MatrixConstruction() {}

    public static int[][] construct(int version, ErrorLevel level, int[] codewords) {
        int size = QRCodeInfos.matrixSize(version);
        int[][] m = new int[size][size];
        boolean[][] isFunction = new boolean[size][size];

        placeFinder(m, isFunction, 0, 0);
        placeFinder(m, isFunction, size - 7, 0);
        placeFinder(m, isFunction, 0, size - 7);
        placeSeparators(isFunction, size);
        placeTiming(m, isFunction, size);

        // module toujours sombre (== motif d'alignement de la zone de format)
        m[4 * version + 9][8] = 1;
        isFunction[4 * version + 9][8] = true;

        int alignCenter = QRCodeInfos.alignmentCenter(version);
        if (alignCenter > 0) {
            placeAlignment(m, isFunction, alignCenter);
        }

        reserveFormatArea(isFunction, size);
        placeData(m, isFunction, size, codewords);

        // sélection du meilleur masque
        int[][] best = null;
        int bestPenalty = Integer.MAX_VALUE;
        for (int mask = 0; mask < 8; mask++) {
            int[][] candidate = deepCopy(m);
            applyMask(candidate, isFunction, size, mask);
            placeFormat(candidate, size, level, mask);
            int p = penalty(candidate, size);
            if (p < bestPenalty) {
                bestPenalty = p;
                best = candidate;
            }
        }
        return best;
    }

    // ---- motifs de fonction ----

    private static void placeFinder(int[][] m, boolean[][] fn, int row, int col) {
        for (int i = 0; i < 7; i++) {
            for (int j = 0; j < 7; j++) {
                boolean black = (i == 0 || i == 6 || j == 0 || j == 6)
                             || (i >= 2 && i <= 4 && j >= 2 && j <= 4);
                m[row + i][col + j] = black ? 1 : 0;
                fn[row + i][col + j] = true;
            }
        }
    }

    private static void placeSeparators(boolean[][] fn, int size) {
        for (int i = 0; i < 8; i++) {
            mark(fn, 7, i, size);            mark(fn, i, 7, size);            // haut-gauche
            mark(fn, 7, size - 1 - i, size); mark(fn, i, size - 8, size);     // haut-droite
            mark(fn, size - 8, i, size);     mark(fn, size - 1 - i, 7, size); // bas-gauche
        }
    }

    private static void placeTiming(int[][] m, boolean[][] fn, int size) {
        for (int i = 8; i < size - 8; i++) {
            int v = (i % 2 == 0) ? 1 : 0;
            m[6][i] = v; fn[6][i] = true;
            m[i][6] = v; fn[i][6] = true;
        }
    }

    private static void placeAlignment(int[][] m, boolean[][] fn, int center) {
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                boolean black = Math.max(Math.abs(i), Math.abs(j)) != 1;
                m[center + i][center + j] = black ? 1 : 0;
                fn[center + i][center + j] = true;
            }
        }
    }

    private static void reserveFormatArea(boolean[][] fn, int size) {
        for (int i = 0; i <= 8; i++) {
            mark(fn, 8, i, size);
            mark(fn, i, 8, size);
        }
        for (int i = 0; i < 8; i++) {
            mark(fn, 8, size - 1 - i, size);
            mark(fn, size - 1 - i, 8, size);
        }
    }

    private static void mark(boolean[][] fn, int r, int c, int size) {
        if (r >= 0 && r < size && c >= 0 && c < size) fn[r][c] = true;
    }

    // ---- placement des données en zig-zag ----

    private static void placeData(int[][] m, boolean[][] fn, int size, int[] codewords) {
        int totalBits = codewords.length * 8;
        int bitIndex = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5; // on saute la colonne de synchronisation
            for (int vert = 0; vert < size; vert++) {
                for (int k = 0; k < 2; k++) {
                    int col = right - k;
                    boolean upward = ((right + 1) & 2) == 0;
                    int row = upward ? (size - 1 - vert) : vert;
                    if (!fn[row][col]) {
                        int bit = 0;
                        if (bitIndex < totalBits) {
                            int cw = codewords[bitIndex >> 3];
                            bit = (cw >> (7 - (bitIndex & 7))) & 1;
                            bitIndex++;
                        }
                        m[row][col] = bit;
                    }
                }
            }
        }
    }

    // ---- masquage ----

    private static boolean maskCondition(int mask, int r, int c) {
        switch (mask) {
            case 0: return (r + c) % 2 == 0;
            case 1: return r % 2 == 0;
            case 2: return c % 3 == 0;
            case 3: return (r + c) % 3 == 0;
            case 4: return (r / 2 + c / 3) % 2 == 0;
            case 5: return (r * c) % 2 + (r * c) % 3 == 0;
            case 6: return ((r * c) % 2 + (r * c) % 3) % 2 == 0;
            case 7: return ((r + c) % 2 + (r * c) % 3) % 2 == 0;
            default: return false;
        }
    }

    private static void applyMask(int[][] m, boolean[][] fn, int size, int mask) {
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (!fn[r][c] && maskCondition(mask, r, c)) {
                    m[r][c] ^= 1;
                }
            }
        }
    }

    // ---- information de format (15 bits, deux copies) ----

    private static void placeFormat(int[][] m, int size, ErrorLevel level, int mask) {
        int fmt = QRCodeInfos.formatSequence(level, mask);

        // première copie, autour du motif haut-gauche
        for (int i = 0; i <= 5; i++) m[i][8] = bit(fmt, i);
        m[7][8] = bit(fmt, 6);
        m[8][8] = bit(fmt, 7);
        m[8][7] = bit(fmt, 8);
        for (int i = 9; i < 15; i++) m[8][14 - i] = bit(fmt, i);

        // seconde copie, le long des motifs haut-droite et bas-gauche
        for (int i = 0; i < 8; i++) m[8][size - 1 - i] = bit(fmt, i);
        for (int i = 8; i < 15; i++) m[size - 15 + i][8] = bit(fmt, i);
        m[size - 8][8] = 1; // module toujours sombre
    }

    private static int bit(int value, int i) {
        return (value >> i) & 1;
    }

    // ---- évaluation des pénalités (règles 1 à 4 du standard) ----

    private static int penalty(int[][] m, int size) {
        int score = 0;

        // règle 1 : séries de 5+ modules de même couleur
        for (int r = 0; r < size; r++) score += lineRun(m, r, true, size);
        for (int c = 0; c < size; c++) score += lineRun(m, c, false, size);

        // règle 2 : blocs 2x2 de même couleur
        for (int r = 0; r < size - 1; r++) {
            for (int c = 0; c < size - 1; c++) {
                int v = m[r][c];
                if (v == m[r][c + 1] && v == m[r + 1][c] && v == m[r + 1][c + 1]) score += 3;
            }
        }

        // règle 3 : motif ressemblant au motif de détection (1:1:3:1:1 + zone claire)
        int[] patA = {1, 0, 1, 1, 1, 0, 1, 0, 0, 0, 0};
        int[] patB = {0, 0, 0, 0, 1, 0, 1, 1, 1, 0, 1};
        for (int r = 0; r < size; r++) {
            for (int c = 0; c <= size - 11; c++) {
                if (matches(m, r, c, true, patA) || matches(m, r, c, true, patB)) score += 40;
            }
        }
        for (int c = 0; c < size; c++) {
            for (int r = 0; r <= size - 11; r++) {
                if (matches(m, r, c, false, patA) || matches(m, r, c, false, patB)) score += 40;
            }
        }

        // règle 4 : proportion de modules sombres trop éloignée de 50 %
        int dark = 0;
        for (int r = 0; r < size; r++) for (int c = 0; c < size; c++) dark += m[r][c];
        int percent = dark * 100 / (size * size);
        score += (Math.abs(percent - 50) / 5) * 10;

        return score;
    }

    private static int lineRun(int[][] m, int index, boolean row, int size) {
        int score = 0, run = 0, prev = -1;
        for (int k = 0; k < size; k++) {
            int v = row ? m[index][k] : m[k][index];
            if (v == prev) {
                run++;
            } else {
                if (run >= 5) score += 3 + (run - 5);
                run = 1;
                prev = v;
            }
        }
        if (run >= 5) score += 3 + (run - 5);
        return score;
    }

    private static boolean matches(int[][] m, int r, int c, boolean row, int[] pat) {
        for (int k = 0; k < pat.length; k++) {
            int v = row ? m[r][c + k] : m[r + k][c];
            if (v != pat[k]) return false;
        }
        return true;
    }

    private static int[][] deepCopy(int[][] a) {
        int[][] b = new int[a.length][];
        for (int i = 0; i < a.length; i++) b[i] = a[i].clone();
        return b;
    }
}
