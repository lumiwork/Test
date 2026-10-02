package de.lumiwork.silentnetguard.scan;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Constant-Pool-Inhalt einer Klasse: alle UTF8-Einträge und die String-Literale (ldc). */
public record ClassInfo(Set<String> utf8, List<String> literals) {

    public static ClassInfo read(byte[] bytes) throws IOException {
        Set<String> utf8 = new HashSet<>();
        List<String> literals = new ArrayList<>();
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        if (in.readInt() != 0xCAFEBABE) {
            throw new IOException("Keine Klassendatei");
        }
        in.readUnsignedShort();
        in.readUnsignedShort();
        int count = in.readUnsignedShort();
        String[] utfByIndex = new String[count];
        List<Integer> stringRefs = new ArrayList<>();
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> {
                    utfByIndex[i] = in.readUTF();
                    utf8.add(utfByIndex[i]);
                }
                case 3, 4 -> in.skipNBytes(4);
                case 5, 6 -> {
                    in.skipNBytes(8);
                    i++;
                }
                case 8 -> stringRefs.add(in.readUnsignedShort());
                case 7, 16, 19, 20 -> in.skipNBytes(2);
                case 9, 10, 11, 12, 17, 18 -> in.skipNBytes(4);
                case 15 -> in.skipNBytes(3);
                default -> throw new IOException("Unbekannter Constant-Pool-Tag " + tag);
            }
        }
        for (int ref : stringRefs) {
            if (ref > 0 && ref < count && utfByIndex[ref] != null) {
                literals.add(utfByIndex[ref]);
            }
        }
        return new ClassInfo(utf8, literals);
    }

    /**
     * Zählt Literale in der Form der SilentNet-Verschlüsselung: das erste oder letzte Zeichen ist eine kleine
     * Schlüssellänge (1-31), der Rest besteht überwiegend aus Zeichen jenseits von Latin-1.
     */
    public int encryptedLiterals() {
        int n = 0;
        for (String s : literals) {
            if (s.length() < 4) {
                continue;
            }
            char first = s.charAt(0);
            char last = s.charAt(s.length() - 1);
            boolean keyMarker = (first >= 1 && first < 32 && first < s.length() - 1)
                    || (last >= 1 && last < 32 && last < s.length() - 1);
            if (!keyMarker) {
                continue;
            }
            int high = 0;
            for (int i = 0; i < s.length(); i++) {
                if (s.charAt(i) >= 0x100) {
                    high++;
                }
            }
            if (high * 2 >= s.length() - 1) {
                n++;
            }
        }
        return n;
    }
}
