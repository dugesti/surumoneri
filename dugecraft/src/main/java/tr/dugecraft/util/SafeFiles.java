package tr.dugecraft.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Skript'lerdeki guvenli kayit sirasinin Java karsiligi:
 * .tmp'ye yaz -> boyutu dogrula -> eski dosyayi .bak'a kopyala ->
 * .tmp'yi atomik olarak yerine tasi -> hedef boyutunu dogrula.
 * Hedef dosya hicbir an yok / yarim olmaz.
 */
public final class SafeFiles {

    private SafeFiles() {
    }

    /**
     * @param bak null ise yedek alinmaz
     * @return true sadece dosya eksiksiz yazildiysa
     */
    public static boolean write(File dst, File tmp, File bak, String data) {
        try {
            byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
            File dir = dst.getAbsoluteFile().getParentFile();
            if (dir != null) {
                dir.mkdirs();
            }
            Files.write(tmp.toPath(), bytes, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            if (!tmp.exists() || tmp.length() != bytes.length) {
                return false;
            }
            if (bak != null && dst.exists() && dst.length() > 0) {
                Files.copy(dst.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(tmp.toPath(), dst.toPath(), StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // Dosya sistemi atomik tasimayi desteklemiyorsa normal tasi
                Files.move(tmp.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Files.move(tmp.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            if (tmp.exists()) {
                return false;
            }
            return dst.length() == bytes.length;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
