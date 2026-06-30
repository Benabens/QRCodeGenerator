# QR Code Generator (Java)

Générateur de **QR codes** écrit *from scratch* en Java : encodage des données en
binaire, correction d'erreurs **Reed-Solomon**, construction de la matrice et
**masquage**, jusqu'à une image PNG scannable.

**Mon projet de BA1 à l'EPFL** (cours CS-107 — *Introduction à la programmation*) :
le mini-projet 1, un générateur de QR codes, codé en Java sans bibliothèque externe.

![Exemple de QR code](docs/example.png)

## Ce que ça fait

- Encode un texte en QR code (mode *byte*), **versions 1 à 4** (jusqu'à 33×33 modules).
- Choisit automatiquement la plus petite version qui contient le texte.
- 4 niveaux de correction d'erreurs : **L, M, Q, H**.
- Sortie en **PNG**, ou affichage en direct dans une petite **fenêtre Swing**.

## Comment ça marche (les étapes d'un QR code)

1. **Encodage des données** (`DataEncoding`) — indicateur de mode, longueur, octets,
   terminateur et octets de remplissage, le tout converti en binaire.
2. **Correction d'erreurs** (`ErrorCorrectionEncoding`) — Reed-Solomon dans le corps
   de Galois GF(256) : le code reste lisible même partiellement abîmé.
3. **Découpage en blocs + entrelacement** des codewords de données et de correction.
4. **Construction de la matrice** (`MatrixConstruction`) — motifs de détection,
   séparateurs, synchronisation, alignement, puis placement des données en zig-zag.
5. **Masquage** — application des 8 masques, calcul des pénalités, choix du meilleur.
6. **Information de format** (code BCH) puis **rendu en image** (`Helpers`).

## Lancer

Avec le JDK directement :

```bash
javac -d out src/main/java/qrcode/*.java
java -cp out qrcode.Main "Hello, world!"               # -> qrcode.png
java -cp out qrcode.Main "mon texte" -l Q -o out.png   # niveau Q, fichier out.png
java -cp out qrcode.Main --gui                         # fenêtre interactive
```

Avec Maven :

```bash
mvn -q compile
mvn -q exec:java -Dexec.mainClass=qrcode.Main -Dexec.args='"Hello, world!"'
```

Options : `-l L|M|Q|H` (niveau de correction, défaut M), `-o fichier` (PNG de sortie), `--gui`.

## Structure

```
src/main/java/qrcode/
├── Main.java                    # entrée en ligne de commande
├── QRViewer.java                # petite interface Swing (live)
├── DataEncoding.java            # texte -> bits -> codewords + entrelacement
├── ErrorCorrectionEncoding.java # Reed-Solomon (GF(256))
├── MatrixConstruction.java      # matrice, motifs, masquage, format
├── QRCodeInfos.java             # tables du standard (capacités, format BCH)
└── Helpers.java                 # rendu image / PNG
```

## Limites connues

- Versions 1 à 4 et mode *byte* uniquement (suffisant pour des textes / URL courts,
  jusqu'à ~78 caractères selon le niveau de correction).
- Pas de modes numérique / alphanumérique optimisés, ni de versions ≥ 5.

## Tests

Les QR codes produits ont été vérifiés en les **redécodant avec OpenCV**, pour
confirmer qu'ils contiennent bien le texte d'origine et qu'ils sont scannables.

---

Projet de **BA1 à l'EPFL** (cours CS-107, *Introduction à la programmation*) —
implémentation du standard QR ISO/IEC 18004, codée from scratch sans bibliothèque externe.
