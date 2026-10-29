package br.com.colheitafeliz.persistence;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Bloqueio cooperativo entre instâncias desktop; o arquivo .lock permanece para evitar disputa na remoção. */
public final class SaveFileLock implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;

    private SaveFileLock(FileChannel channel, FileLock lock) { this.channel = channel; this.lock = lock; }

    public static SaveFileLock acquire(Path file) throws IOException {
        Path absolute = file.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null) throw new IOException("Escolha um arquivo para salvar a fazenda, não a raiz do disco.");
        Files.createDirectories(parent);
        Path canonical = Files.exists(absolute) ? absolute.toRealPath()
                : parent.toRealPath().resolve(absolute.getFileName());
        Path name = canonical.getFileName();
        if (name == null) throw new IOException("Escolha um arquivo para salvar a fazenda.");
        FileChannel channel = FileChannel.open(canonical.resolveSibling(name + ".lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) throw new IOException("Esta fazenda já está aberta em outra instância do jogo.");
            return new SaveFileLock(channel, lock);
        } catch (IOException | RuntimeException e) {
            try { channel.close(); } catch (IOException closeFailure) { e.addSuppressed(closeFailure); }
            if (e instanceof OverlappingFileLockException)
                throw new IOException("Esta fazenda já está aberta em outra instância do jogo.", e);
            throw e;
        }
    }

    @Override public void close() throws IOException {
        try { lock.release(); } finally { channel.close(); }
    }
}
